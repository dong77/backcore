package com.coinport.coinex.opendata

import akka.actor.{ Cancellable, ActorContext }
import akka.event.LoggingReceive
import akka.persistence.{ PersistentRepr, EventsourcedProcessor }
import akka.persistence.hbase.common.{ HdfsSnapshotDescriptor, RowKey, DeferredConversions }
import akka.persistence.hbase.common.Columns._
import akka.persistence.hbase.journal.PluginPersistenceSettings
import akka.persistence.hbase.common.Const._
import akka.persistence.serialization.Snapshot
import akka.serialization.SerializationExtension
import com.coinport.coinex.common.{ Manager, ExtendedProcessor }
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
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.collection.JavaConverters._

import Implicits._
import DeferredConversions._

class ExportOpenDataProcessor(var asyncHBaseClient: AsyncHBaseClient) extends ExtendedProcessor
    with EventsourcedProcessor {
  override def processorId = EXPORT_OPEN_DATA_PROCESSOR <<
  val config = context.system.settings.config
  private var cancellable: Cancellable = null
  lazy val openDataConfig = loadConfig("open_data_config.scala")
  private val scheduleInterval = openDataConfig.scheduleInterval
  lazy val manager = new ExportOpenDataManager(asyncHBaseClient, context, openDataConfig)

  override def preStart(): Unit = {
    super.preStart()
    if (openDataConfig.enableExportData) {
      scheduleExport()
    }
  }

  override def receiveCommand = LoggingReceive {
    case DoExportData => doExportData()
  }

  def receiveRecover = updateState

  def updateState: Receive = {
    case map: ExportOpenDataMap =>
      manager.updatePSeqMap(scala.collection.mutable.Map.empty[String, Long] ++ map.processorSeqMap)
  }

  private def scheduleExport() = {
    cancellable = context.system.scheduler.schedule(10 second, scheduleInterval, self, DoExportData)(
      context.system.dispatcher)
  }

  override def postStop() {
    //    fs.close()
  }

  private def doExportData() {
    val dumpedMap = manager.doExportData()
    if (!dumpedMap.isEmpty) {
      persist(ExportOpenDataMap(dumpedMap))(_ => ())
    }
  }

  private def loadConfig(configPath: String): OpenDataConfig = {
    val in: InputStream = this.getClass.getClassLoader.getResourceAsStream(configPath)
    (new Eval()(IOUtils.toString(in))).asInstanceOf[OpenDataConfig]
  }

}

class ExportOpenDataManager(val asyncHBaseClient: AsyncHBaseClient, val context: ActorContext, val openDataConfig: OpenDataConfig) extends Manager[ExportOpenDataMap] {
  private val config = context.system.settings.config
  private lazy val fs: FileSystem = openHdfsSystem(openDataConfig.hdfsHost)
  private val exportSnapshotHdfsDir = openDataConfig.exportSnapshotHdfsDir
  private val exportMessagesHdfsDir = openDataConfig.exportMessagesHdfsDir
  private val snapshotHdfsDir: String = config.getString("hadoop-snapshot-store.snapshot-dir")
  private val messagesTable = config.getString("hbase-journal.table")
  private val messagesFamily = config.getString("hbase-journal.family")
  private val BUFFER_SIZE = 2048
  private val SCAN_MAX_NUM_ROWS = 50
  implicit var pluginPersistenceSettings = PluginPersistenceSettings(config, JOURNAL_CONFIG)
  implicit var executionContext = context.system.dispatcher
  implicit var serialization = SerializationExtension(context.system)
  // [pid, dumpFileName]
  private val pFileMap = openDataConfig.pFileMap
  // [pId, (seqNum, timestamp)]
  private val pSeqMap = collection.mutable.Map.empty[String, Long]
  pFileMap.keySet foreach { key => pSeqMap.put(key, 1L) }

  def getSnapshot(): ExportOpenDataMap = {
    ExportOpenDataMap(pSeqMap)
  }

  override def loadSnapshot(map: ExportOpenDataMap) {
    updatePSeqMap(scala.collection.mutable.Map.empty[String, Long] ++ map.processorSeqMap)
  }

  def updatePSeqMap(addedMap: scala.collection.mutable.Map[String, Long]) {
    addedMap.keySet foreach {
      key =>
        if (pFileMap.contains(key)) {
          pSeqMap.put(key, addedMap(key))
        }
    }
  }

  def doExportData(): (scala.collection.mutable.Map[String, Long]) = {
    val dumpedMap = scala.collection.mutable.Map.empty[String, Long]
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
            }, classOf[Snapshot]).get
        val exportSnapshotPath = new Path(exportSnapshotHdfsDir, s"snapshot_${pFileMap(processorId)}_${seqNum}_${System.currentTimeMillis()}.json")
        val jsonSnapshot = s"""{"timestamp" : ${System.currentTimeMillis()}, "snapshot" : ${PrettyJsonSerializer.toJson(snapshot)}}"""
        withStream(new BufferedWriter(new OutputStreamWriter(fs.create(exportSnapshotPath, true)), BUFFER_SIZE))(IOUtils.write(jsonSnapshot, _))
        seqNum

      case _ => processedSeqNum - 1
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
          new Path(exportMessagesHdfsDir, s"message_${pFileMap(processorId)}_${toSeqNum - 1}_${System.currentTimeMillis()}.json"))))
        writer.write(s"""{"timestamp" : ${System.currentTimeMillis()}, "messages":[""")
        writer.write(data.substring(0, data.length - 1).toString())
        writer.write("]}")
        writer.flush()
        writer.close()
    }
  }

  private def openHdfsSystem(defaultName: String): FileSystem = {
    val conf = new Configuration()
    conf.set("fs.default.name", defaultName)
    FileSystem.get(conf)
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
