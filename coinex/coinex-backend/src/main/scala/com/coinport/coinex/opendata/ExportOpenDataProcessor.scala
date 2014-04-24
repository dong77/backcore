package com.coinport.coinex.opendata

import akka.actor.{ Cancellable, ActorContext }
import akka.event.LoggingReceive
import akka.persistence.{ PersistentRepr, EventsourcedProcessor }
import akka.persistence.hbase.common.{ HdfsSnapshotDescriptor, RowKey, DeferredConversions }
import akka.persistence.hbase.common.Columns._
import akka.persistence.hbase.journal.PluginPersistenceSettings
import akka.persistence.hbase.common.Const._
import akka.persistence.{ SnapshotMetadata, SnapshotOffer }
import akka.persistence.serialization.Snapshot
import akka.serialization.SerializationExtension
import com.coinport.coinex.common.ExtendedProcessor
import com.coinport.coinex.common.PersistentId._
import com.coinport.coinex.data._
import com.coinport.coinex.serializers.PrettyJsonSerializer
import com.twitter.util.Eval
import java.io._
import java.util.{ ArrayList => JArrayList }
import org.apache.commons.io.IOUtils
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{ Path, FileSystem }
import org.apache.hadoop.hbase.util.Bytes
import org.hbase.async.KeyValue
import scala.collection.mutable.Map
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.collection.JavaConverters._

import Implicits._
import DeferredConversions._

class ExportOpenDataProcessor(var asyncHBaseClient: AsyncHBaseClient) extends ExtendedProcessor
    with EventsourcedProcessor {
  override def processorId = EXPORT_OPEN_DATA_PROCESSOR <<

  private var cancellable: Cancellable = null
  private val scheduleInterval = 10 second
  private val pSeqMap = scala.collection.mutable.Map.empty[String, Long]
  private var pFileMap = scala.collection.mutable.Map.empty[String, String]
  private var manager: ExportOpenDataManager = null

  override def preStart(): Unit = {
    super.preStart()
    val config = context.system.settings.config
    val exportData = config.getBoolean("akka.opendata.enabled-export")
    if (!exportData) return
    val (pInitSeqMap, pInitFileMap) = loadOpenDataProcessors(context.system.settings.config.getString("akka.opendata.processors-map-path"))
    pSeqMap ++= pInitSeqMap
    pFileMap ++= pInitFileMap
    manager = new ExportOpenDataManager(asyncHBaseClient, context, pFileMap)
    scheduleExport()
  }

  override def receiveCommand = LoggingReceive {
    case DoExportData =>
      exportData()
  }

  override def receiveRecover: Receive = {
    case SnapshotOffer(metadata: SnapshotMetadata, snapshot: Any) =>
      val snapMap = snapshot.asInstanceOf[Map[String, Long]]
      snapMap.keySet foreach {
        key => withCheck(key, pSeqMap)(pSeqMap.put(key, snapMap(key)))
      }
    case ExportOpenDataMap(map) =>
      map.keySet foreach {
        key => withCheck(key, pSeqMap)(pSeqMap.put(key, map(key)))
      }
  }

  private def withCheck(key: String, map: Map[String, Long])(f: => Unit) {
    if (map.contains(key)) f
  }

  private def scheduleExport() = {
    cancellable = context.system.scheduler.schedule(
      scheduleInterval, scheduleInterval, self, DoExportData)
  }

  override def postStop() {
    //    fs.close()
  }

  private def exportData() {
    val dumpedMap = scala.collection.mutable.Map.empty[String, Long]
    pSeqMap.keySet foreach {
      processorId =>
        if (processorId != null && !processorId.isEmpty) {
          // "processedSeqNum" included in current process
          val processedSeqNum = pSeqMap(processorId)
          // "lastSeqNum" included in current process, excluded in next process
          val lastSeqNum = manager.dumpSnapshot(processorId, processedSeqNum)
          if (lastSeqNum - processedSeqNum >= manager.messagesBatchSize) {
            manager.dumpMessages(processorId, processedSeqNum, lastSeqNum + 1)
            dumpedMap += processorId -> (lastSeqNum + 1)
          }
        }
    }
    if (!dumpedMap.isEmpty) {
      persist(ExportOpenDataMap(dumpedMap))(_ => ())
      pSeqMap ++= dumpedMap
    }
  }

  private def loadOpenDataProcessors(mapConfigPath: String): (Map[String, Long], collection.immutable.Map[String, String]) = {
    val in: InputStream = this.getClass.getClassLoader.getResourceAsStream(mapConfigPath)
    val pFileMap = (new Eval()(IOUtils.toString(in))).asInstanceOf[collection.immutable.Map[String, String]]
    val pSeqMap = Map.empty[String, Long]
    pFileMap.keySet.foreach(pSeqMap.put(_, 0L))
    (pSeqMap, pFileMap)
  }
}

class ExportOpenDataManager(val asyncHBaseClient: AsyncHBaseClient, val context: ActorContext, val pFileMap: Map[String, String]) {
  private val config = context.system.settings.config
  private val fs: FileSystem = openHdfsSystem(config.getString("akka.opendata.hdfs-host"))
  var messagesBatchSize: Long = config.getLong("akka.opendata.messages-batch-size")
  private val snapshotHdfsDir: String = config.getString("hadoop-snapshot-store.snapshot-dir")
  private val exportSnapshotHdfsDir = config.getString("akka.opendata.export-snapshot-hdfs-dir")
  private val exportMessagesHdfsDir = config.getString("akka.opendata.export-messages-hdfs-dir")
  private val messagesTable = config.getString("hbase-journal.table")
  private val messagesFamily = config.getString("hbase-journal.family")
  private val BUFFER_SIZE = 2048
  private val SCAN_MAX_NUM_ROWS = 50
  implicit var pluginPersistenceSettings = PluginPersistenceSettings(config, JOURNAL_CONFIG)
  implicit var executionContext = context.system.dispatcher
  implicit var serialization = SerializationExtension(context.system)

  protected def openHdfsSystem(defaultName: String): FileSystem = {
    val conf = new Configuration()
    conf.set("fs.default.name", defaultName)
    FileSystem.get(conf)
  }

  def dumpSnapshot(processorId: String, processedSeqNum: Long): Long = {
    val snapshotMetas = listSnapshots(snapshotHdfsDir, processorId)
    if (snapshotMetas.isEmpty)
      return processedSeqNum
    snapshotMetas.head match {
      case desc @ HdfsSnapshotDescriptor(processorId: String, seqNum: Long, _) if (seqNum - processedSeqNum > messagesBatchSize) =>
        val path = new Path(snapshotHdfsDir, desc.toFilename)
        val snapshot =
          serialization.deserialize(
            withStream(new BufferedInputStream(fs.open(path, BUFFER_SIZE), BUFFER_SIZE)) {
              IOUtils.toByteArray
            }, classOf[Snapshot]).get
        val exportSnapshotPath = new Path(exportSnapshotHdfsDir, s"snapshot_${pFileMap(processorId)}_${seqNum}")
        val jsonSnapshot = PrettyJsonSerializer.toJson(snapshot)
        withStream(new BufferedWriter(new OutputStreamWriter(fs.create(exportSnapshotPath, true)), BUFFER_SIZE))(IOUtils.write(jsonSnapshot, _))
        seqNum

      case _ => processedSeqNum
    }
  }

  // "fromSeqNum" is included, "toSeqNum" is excluded
  def dumpMessages(processorId: String, fromSeqNum: Long, toSeqNum: Long) {
    if (toSeqNum <= fromSeqNum) return
    val client = asyncHBaseClient.getClient()
    val scanner = client.newScanner(Bytes.toBytes(messagesTable))
    scanner.setFamily(Bytes.toBytes(messagesFamily))
    scanner.setStartKey(RowKey(processorId, fromSeqNum).toBytes)
    scanner.setStopKey(RowKey.toKeyForProcessor(processorId, toSeqNum))
    scanner.setKeyRegexp(RowKey.patternForProcessor(processorId))
    scanner.setMaxNumRows(SCAN_MAX_NUM_ROWS)
    type AsyncBaseRows = JArrayList[JArrayList[KeyValue]]

    def getMessages(rows: AsyncBaseRows): String = {
      val builder = new StringBuilder()
      for (row <- rows.asScala) {
        builder ++= "{"
        for (column <- row.asScala) {
          if (java.util.Arrays.equals(column.qualifier, Message) || java.util.Arrays.equals(column.qualifier, SequenceNr)) {
            builder ++= "\"" ++= Bytes.toString(column.qualifier) ++= "\":"
            if (java.util.Arrays.equals(column.qualifier, Message)) {
              // will throw an exception if failed
              val msg = serialization.deserialize(column.value(), classOf[PersistentRepr]).get
              builder ++= PrettyJsonSerializer.toJson(msg.payload)
            } else {
              builder ++= Bytes.toLong(column.value()).toString
            }
            builder ++= ","
          }
        }
        builder.delete(builder.length - 1, builder.length)
        builder ++= "},"
      }
      builder.toString()
    }

    def handleRows(): Future[StringBuilder] = {
      scanner.nextRows() flatMap {
        case null =>
          scanner.close()
          Future(new StringBuilder())
        case rows: AsyncBaseRows =>
          val builder = new StringBuilder()
          builder ++= getMessages(rows)
          handleRows() map {
            res =>
              builder ++= res
          }
      }
    }

    handleRows() map {
      case data if !data.isEmpty =>
        val writer = new BufferedWriter(new OutputStreamWriter(fs.create(
          new Path(exportMessagesHdfsDir, s"message_${pFileMap(processorId)}_${toSeqNum - 1}"))))
        writer.write("{\"messages\":[")
        writer.write(data.substring(0, data.length - 1).toString())
        writer.write("]}")
        writer.flush()
        writer.close()
    }
  }

  private def withStream[S <: Closeable, A](stream: S)(fun: S => A): A =
    try fun(stream) finally stream.close()

  private def listSnapshots(snapshotDir: String, processorId: String): List[HdfsSnapshotDescriptor] = {
    val descs = fs.listStatus(new Path(snapshotDir)) flatMap {
      HdfsSnapshotDescriptor.from(_, processorId)
    }
    if (descs.isEmpty) Nil else descs.sortWith(_.seqNumber > _.seqNumber).toList
  }

}