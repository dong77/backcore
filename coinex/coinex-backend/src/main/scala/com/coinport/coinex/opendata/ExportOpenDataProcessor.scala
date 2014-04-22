package com.coinport.coinex.opendata

import akka.actor.Cancellable
import akka.event.LoggingReceive
import akka.persistence.{ EventsourcedProcessor, SnapshotMetadata, SnapshotOffer }
import akka.persistence.hbase.common.{ HdfsSnapshotDescriptor, RowKey, DeferredConversions }
import akka.persistence.hbase.common.Columns._
import akka.persistence.hbase.journal.PluginPersistenceSettings
import akka.persistence.hbase.common.Const._
import akka.serialization.SerializationExtension
import com.coinport.coinex.common.ExtendedProcessor
import com.coinport.coinex.common.PersistentId._
import com.coinport.coinex.data._
import com.coinport.coinex.serializers.{ ThriftBinarySerializer, ThriftJsonSerializer }
import com.coinport.coinex.common.support.SnapshotSupport
import com.twitter.util.Eval
import java.io.{ BufferedInputStream, BufferedOutputStream, Closeable, InputStream }
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
import scala.Tuple2

import Implicits._
import DeferredConversions._
import akka.persistence.serialization.Snapshot
import scala.util.{ Failure, Success }

class ExportOpenDataProcessor(var asyncHBaseClient: AsyncHBaseClient) extends ExtendedProcessor
    with EventsourcedProcessor with ExportOpenDataManager {
  override def processorId = EXPORT_OPEN_DATA_PROCESSOR <<

  private var cancellable: Cancellable = null
  private val scheduleInterval = 10 second
  private val pSeqMap = scala.collection.mutable.Map.empty[String, Long]

  // Init parameters for ExportOpenDataManager
  override var fs: FileSystem = null
  override var messageItemsInterval: Long = 100L
  override var snapshotHdfsDir: String = ""
  override var exportSnapshotDir: String = ""
  override var exportMessagesDir: String = ""
  override var messagesTable: String = ""
  override var messagesFamily: String = ""
  override implicit var pluginPersistenceSettings: PluginPersistenceSettings = null

  override def preStart(): Unit = {
    super.preStart()
    val config = context.system.settings.config
    val exportData = config.getBoolean("akka.opendata.is-export")
    if (!exportData) return
    pSeqMap ++= loadOpenDataProcessors(context.system.settings.config.getString("akka.opendata.processors-map-path"))
    fs = openHdfsSystem(config.getString("akka.opendata.hdfs-name"))
    messageItemsInterval = config.getLong("akka.opendata.messages-interval")
    snapshotHdfsDir = config.getString("hadoop-snapshot-store.snapshot-dir")
    exportSnapshotDir = config.getString("akka.opendata.export-snapshot-dir")
    exportMessagesDir = config.getString("akka.opendata.export-messages-dir")
    messagesTable = config.getString("hbase-journal.table")
    messagesFamily = config.getString("hbase-journal.family")
    pluginPersistenceSettings = PluginPersistenceSettings(config, JOURNAL_CONFIG)
    scheduleExport()
    System.out.println(s"""${">" * 20} In preStart""")
  }

  override def receiveCommand = LoggingReceive {
    case DoExportData =>
      exportData()
  }

  override def receiveRecover: Receive = {
    case SnapshotOffer(metadata: SnapshotMetadata, snapshot: Any) =>
      pSeqMap ++= snapshot.asInstanceOf[Map[String, Long]]
      System.out.println(s"""${">" * 20} In SnapshotOffer""")
    case Tuple2(processorId: String, lastSeqNum: Long) =>
      pSeqMap += processorId -> lastSequenceNr
      System.out.println(s"""${">" * 20} In recovery messages""")
  }

  private def scheduleExport() = {
    cancellable = context.system.scheduler.schedule(
      scheduleInterval, scheduleInterval, self, DoExportData)
  }

  override def postStop() {
    //    fs.close()
  }

  private def exportData() {
    System.out.println(s"""${">" * 40} exportData : pSeqMap = ${pSeqMap.toString()}, pSeqMap.keySet = ${pSeqMap.keySet.toString}""")
    pSeqMap.keySet foreach {
      processorId =>
        if (processorId != null && !processorId.isEmpty) {
          val processedSeqNum = pSeqMap(processorId)
          val lastSeqNum = dumpSnapshot(processorId, processedSeqNum)
          if (lastSeqNum - processedSeqNum > messageItemsInterval) {
            dumpMessages(processorId, processedSeqNum, lastSeqNum)
            persist(Tuple2(processorId, lastSeqNum))(_ => ())
            pSeqMap += processorId -> lastSeqNum
          }
        }
    }
  }

}

trait ExportOpenDataManager { this: SnapshotSupport =>
  var asyncHBaseClient: AsyncHBaseClient
  var fs: FileSystem
  var messageItemsInterval: Long
  var snapshotHdfsDir: String
  var exportSnapshotDir: String
  var exportMessagesDir: String
  var messagesTable: String
  var messagesFamily: String
  val binarySerilizer = new ThriftBinarySerializer()
  val jsonSerilizer = new ThriftJsonSerializer()
  private val BUFFER_SIZE = 2048
  private val SCAN_MAX_NUM_ROWS = 50
  implicit var pluginPersistenceSettings: PluginPersistenceSettings
  val serialization = SerializationExtension(context.system)

  protected def loadOpenDataProcessors(mapConfigPath: String): Map[String, Long] = {
    val in: InputStream = this.getClass.getClassLoader.getResourceAsStream(mapConfigPath)
    val map = (new Eval()(IOUtils.toString(in))).asInstanceOf[Map[String, Long]]
    System.out.println(s"""${">" * 40} loadOpenDataProcessors : map.toString = ${map.toString()}""")
    map
  }

  protected def openHdfsSystem(defaultName: String): FileSystem = {
    val conf = new Configuration()
    conf.set("fs.default.name", defaultName)
    FileSystem.get(conf)
  }

  protected def dumpSnapshot(processorId: String, processedSeqNum: Long): Long = {
    val snapshotMetas = listSnapshots(snapshotHdfsDir, processorId)
    if (snapshotMetas.isEmpty)
      return processedSeqNum
    snapshotMetas.head match {
      case desc @ HdfsSnapshotDescriptor(processorId: String, seqNum: Long, _) if (seqNum - processedSeqNum > messageItemsInterval) =>
        val path = new Path(snapshotHdfsDir, desc.toFilename)
        System.out.println(s"""${">" * 40} dumpSnapshot : processorId = $processorId, path = ${path.getName}""")
        try {
          val snapshot =
            serialization.deserialize(
              withStream(new BufferedInputStream(fs.open(path, BUFFER_SIZE), BUFFER_SIZE)) { IOUtils.toByteArray }, classOf[Snapshot]) match {
                case Success(snapshot) =>
                  snapshot.data.asInstanceOf[AnyRef]
                case Failure(ex) =>
                  log.error(s"""Failed to deserialize snapshot file ${path.getName}, error : ${ex.getMessage}""")
                  return seqNum
              }
          System.out.println(s"""${">" * 40} dumpSnapshot : processorId = $processorId, path = ${path.getName}, snapshot = ${snapshot.toString}""")

          val jsonSnapshot = jsonSerilizer.toBinary(snapshot)
          val exportSnapshotPath = new Path(exportSnapshotDir, desc.toFilename)
          withStream(new BufferedOutputStream(fs.create(exportSnapshotPath, true), BUFFER_SIZE))(IOUtils.write(jsonSnapshot, _))
          return seqNum
        } catch {
          case e: Exception =>
            log.error("dumpSnapshot Error " + e.getMessage)
            return processedSeqNum
        }
      case _ => processedSeqNum
    }
  }

  protected def dumpMessages(processorId: String, fromSeqNum: Long, toSeqNum: Long) {
    if (toSeqNum <= fromSeqNum) return
    val client = asyncHBaseClient.getClient()
    val scanner = client.newScanner(Bytes.toBytes(messagesTable))
    scanner.setFamily(Bytes.toBytes(messagesFamily))
    scanner.setStartKey(RowKey(processorId, fromSeqNum).toBytes)
    scanner.setStopKey(RowKey.toKeyForProcessor(processorId, toSeqNum))
    scanner.setMaxNumRows(SCAN_MAX_NUM_ROWS)
    val outputStream = new BufferedOutputStream(fs.create(new Path(exportMessagesDir, s"$processorId~$toSeqNum~${System.currentTimeMillis()}")))
    outputStream.write(Bytes.toBytes("{messages:["))
    type AsyncBaseRows = JArrayList[JArrayList[KeyValue]]

    def handleRows(in: AnyRef): Future[Unit] = {
      in match {
        case null => Future(())
        case rows: AsyncBaseRows =>
          for (row <- rows.asScala) {
            outputStream.write(Bytes.toBytes("\"message\":{"))
            for (column <- row.asScala) {
              if (java.util.Arrays.equals(column.qualifier, Message) || java.util.Arrays.equals(column.qualifier, SequenceNr)) {
                outputStream.write(Bytes.toBytes("\""))
                outputStream.write(column.qualifier)
                outputStream.write(Bytes.toBytes("\":"))
                if (java.util.Arrays.equals(column.qualifier, Message))
                  outputStream.write(jsonSerilizer.toBinary(binarySerilizer.fromBinary(column.value())))
                else (java.util.Arrays.equals(column.qualifier, SequenceNr))
                outputStream.write(Bytes.toBytes(Bytes.toLong(column.value()).toString))
                outputStream.write(Bytes.toBytes(","))
              }
            }
            outputStream.write(Bytes.toBytes("},"))
          }
          go()
      }
    }

    def go(): Future[Unit] = scanner.nextRows() flatMap handleRows
    go() onComplete {
      outputStream.write(Bytes.toBytes("]}"))
      res => outputStream.close()
    }
  }

  private def withStream[S <: Closeable, A](stream: S)(fun: S => A): A =
    try fun(stream) finally stream.close()

  private def listSnapshots(snapshotDir: String, processorId: String): List[HdfsSnapshotDescriptor] = {
    val descs = fs.listStatus(new Path(snapshotDir)) flatMap { HdfsSnapshotDescriptor.from(_, processorId) }
    if (descs.isEmpty)
      Nil
    else
      descs.sortWith(_.seqNumber > _.seqNumber).toList
  }

}