package com.coinport.coinex.opendata

import akka.actor.ActorContext
import akka.persistence.PersistentRepr
import akka.persistence.hbase.journal.PluginPersistenceSettings
import akka.persistence.hbase.common.Const._
import akka.persistence.hbase.common._
import akka.persistence.hbase.common.Columns._
import akka.persistence.serialization.Snapshot
import com.coinport.coinex.common.Manager
import com.coinport.coinex.data.ExportOpenDataMap
import java.util.{ ArrayList => JArrayList }
import java.io.BufferedInputStream
import org.apache.commons.io.IOUtils
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{ Path, FileSystem }
import org.apache.hadoop.hbase.util.Bytes
import org.hbase.async.KeyValue
import scala.concurrent.Future
import scala.collection.mutable
import scala.collection.mutable.Map
import scala.collection.mutable.ListBuffer
import scala.collection.JavaConverters._

import DeferredConversions._

class ExportOpenDataManager(val asyncHBaseClient: AsyncHBaseClient, val context: ActorContext, implicit val openDataConfig: OpenDataConfig)
    extends Manager[ExportOpenDataMap] with EventWriter {
  private val config = context.system.settings.config
  implicit lazy val fs: FileSystem = openHdfsSystem(openDataConfig.hdfsHost)
  private val snapshotHdfsDir: String = config.getString("hadoop-snapshot-store.snapshot-dir")
  private val messagesTable = config.getString("hbase-journal.table")
  private val messagesFamily = config.getString("hbase-journal.family")
  private val cryptKey = config.getString("akka.persistence.encryption-settings")
  private val SCAN_MAX_NUM_ROWS = 5
  private val ReplayGapRetry = 5
  implicit var pluginPersistenceSettings = PluginPersistenceSettings(config, JOURNAL_CONFIG)
  implicit var executionContext = context.system.dispatcher
  implicit var serialization = EncryptingSerializationExtension(context.system, cryptKey)

  // [pid, dumpFileName]
  implicit val pFileMap: mutable.Map[String, String] = openDataConfig.pFileMap
  // [pId, (seqNum, timestamp)]
  private val pSeqMap = Map.empty[String, Long]
  pFileMap.keySet foreach { key => pSeqMap.put(key, 1L) }

  def getSnapshot(): ExportOpenDataMap = {
    ExportOpenDataMap(pSeqMap)
  }

  override def loadSnapshot(map: ExportOpenDataMap) {
    updatePSeqMap(Map.empty[String, Long] ++ map.processorSeqMap)
  }

  def updatePSeqMap(addedMap: Map[String, Long]) {
    addedMap.keySet foreach {
      key =>
        if (pFileMap.contains(key)) {
          pSeqMap.put(key, addedMap(key))
        }
    }
  }

  def exportData(): (Map[String, Long]) = {
    val dumpedMap = Map.empty[String, Long]
    pSeqMap.keySet foreach {
      processorId =>
        if (processorId != null && !processorId.isEmpty) {
          // "processedSeqNum" included in current process
          val processedSeqNum = pSeqMap(processorId)
          // "lastSeqNum" included in current process, excluded in next process
          val lastSeqNum = dumpSnapshot(processorId, processedSeqNum)
          if (lastSeqNum >= processedSeqNum) { // when lastSeqNum == processedSeqNum, there is one message
            dumpMessages(processorId, processedSeqNum, lastSeqNum + 1)
            dumpedMap += processorId -> (lastSeqNum + 1)
          }
        }
    }
    if (!dumpedMap.isEmpty) {
      pSeqMap ++= dumpedMap
    }
    dumpedMap
  }

  def dumpSnapshot(processorId: String, processedSeqNum: Long): Long = {
    val snapshotMetas = listSnapshots(snapshotHdfsDir, processorId)

    if (snapshotMetas.isEmpty) //no file to process, let processedSeqNum to former process's lastNum, which is processedSeqNum - 1
      return processedSeqNum - 1
    snapshotMetas.head match {
      // when lastSeqNum == processedSeqNum, there is one message
      case desc @ HdfsSnapshotDescriptor(processorId: String, seqNum: Long, _) if (seqNum >= processedSeqNum) =>
        val path = new Path(snapshotHdfsDir, desc.toFilename)
        val snapshot =
          serialization.deserialize(
            withStream(new BufferedInputStream(fs.open(path, BUFFER_SIZE), BUFFER_SIZE)) {
              IOUtils.toByteArray
            }, classOf[Snapshot])
        writeSnapshot(processorId, seqNum, snapshot)
        seqNum
      case _ => processedSeqNum - 1
    }
  }

  // "fromSeqNum" is inclusive, "toSeqNum" is exclusive
  def dumpMessages(processorId: String, fromSeqNum: Long, toSeqNum: Long) {
    if (toSeqNum <= fromSeqNum) return
    val client = asyncHBaseClient.getClient()
    var retryTimes: Int = 0
    var isDuplicate = false
    var tryStartSeqNr: Long = if (fromSeqNum <= 0) 1 else fromSeqNum

    var scanner: SaltedScanner = null
    type AsyncBaseRows = JArrayList[JArrayList[KeyValue]]

    def hasSequenceGap(columns: collection.mutable.Buffer[KeyValue]): Boolean = {
      val processingSeqNr = sequenceNr(columns)
      if (tryStartSeqNr != processingSeqNr) {
        if (tryStartSeqNr > processingSeqNr) {
          sys.error(s"Replay $processorId Meet duplicated message: to process is $tryStartSeqNr, actual is $processingSeqNr")
          isDuplicate = true
        }
        return true
      } else {
        return false
      }
    }

    def initScanner() {
      if (scanner != null) scanner.close()
      scanner = new SaltedScanner(client, pluginPersistenceSettings.partitionCount, Bytes.toBytes(messagesTable), Bytes.toBytes(messagesFamily))
      scanner.setSaltedStartKeys(processorId, tryStartSeqNr)
      scanner.setSaltedStopKeys(processorId, RowKey.toSequenceNr(toSeqNum))
      scanner.setKeyRegexp(processorId)
      scanner.setMaxNumRows(SCAN_MAX_NUM_ROWS)
    }

    def sequenceNr(columns: mutable.Buffer[KeyValue]): Long = {
      for (column <- columns) {
        if (java.util.Arrays.equals(column.qualifier, SequenceNr)) {
          return Bytes.toLong(column.value())
        }
      }
      0L
    }

    def getMessages(rows: AsyncBaseRows): (Boolean, String, List[(Long, Any)]) = {
      val messages = ListBuffer.empty[(Long, Any)]
      for (row <- rows.asScala) {
        if (hasSequenceGap(row.asScala) && retryTimes < ReplayGapRetry) {
          if (isDuplicate) {
            return (true, "Duplicated message", List.empty[(Long, Any)])
          }
          sys.error(s"Meet gap at ${tryStartSeqNr}")
          retryTimes += 1
          Thread.sleep(100)
          initScanner()
          return (false, "", List.empty[(Long, Any)])
        } else {
          if (retryTimes >= ReplayGapRetry) {
            return (true, s"Gap retry times reach ${ReplayGapRetry}", List.empty[(Long, Any)])
          }
          var seqNum = 0L
          var payload: Any = null
          for (column <- row.asScala) {
            if (java.util.Arrays.equals(column.qualifier, Message) || java.util.Arrays.equals(column.qualifier, SequenceNr)) {
              if (java.util.Arrays.equals(column.qualifier, Message)) {
                // will throw an exception if failed
                payload = serialization.deserialize(column.value(), classOf[PersistentRepr]).payload
              } else {
                seqNum = Bytes.toLong(column.value())
                tryStartSeqNr = seqNum + 1
              }
            }
          }
          messages.append((seqNum, payload))
          retryTimes = 0
        }
      }
      (false, "", messages.toList)
    }

    def handleRows(): Future[Unit] = {
      scanner.nextRows() flatMap {
        case null =>
          scanner.close()
          Future(())
        case rows: AsyncBaseRows =>
          val (isFailed, errMsg, messages) = getMessages(rows)
          if (!messages.isEmpty && tryStartSeqNr > 0) {
            writeMessages(processorId, tryStartSeqNr - 1, messages)
          }
          if (isFailed) {
            sys.error(errMsg)
            Future.failed(new Exception(errMsg))
          } else {
            handleRows()
          }
      }
    }

    initScanner
    handleRows()
  }

  private def writeMessages(processorId: String, lastSeqNum: Long, messages: List[(Long, Any)]) {
    MessageJsonWriter.writeMessages(processorId, lastSeqNum, messages)
    if (openDataConfig.messageWriterMap.contains(processorId)) {
      openDataConfig.messageWriterMap(processorId).writeMessages(processorId, lastSeqNum, messages)
    }
  }

  private def writeSnapshot(processorId: String, seqNum: Long, snapshot: Snapshot) {
    SnapshotJsonWriter.writeSnapshot(processorId, seqNum, snapshot)
    val className = snapshot.data.getClass.getEnclosingClass.getSimpleName
    if (openDataConfig.snapshotWriterMap.contains(className)) {
      openDataConfig.snapshotWriterMap(className).writeSnapshot(processorId, seqNum, snapshot)
    }
  }

  private def openHdfsSystem(defaultName: String): FileSystem = {
    val conf = new Configuration()
    conf.set("fs.default.name", defaultName)
    FileSystem.get(conf)
  }

  private def listSnapshots(snapshotDir: String, processorId: String): Seq[HdfsSnapshotDescriptor] = {
    val descs = fs.listStatus(new Path(snapshotDir)) flatMap {
      HdfsSnapshotDescriptor.from(_, processorId)
    }
    if (descs.isEmpty) Nil else descs.sortWith(_.seqNumber > _.seqNumber).toSeq
  }

}