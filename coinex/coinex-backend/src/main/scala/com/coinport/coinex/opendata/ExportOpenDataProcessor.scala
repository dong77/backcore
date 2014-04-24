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
import akka.serialization.{ SerializationExtension, Serialization }
import com.coinport.coinex.common.ExtendedProcessor
import com.coinport.coinex.common.PersistentId._
import com.coinport.coinex.data._
import com.coinport.coinex.data.ExportOpenDataMap
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
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }
import scala.collection.JavaConverters._

import Implicits._
import DeferredConversions._

class ExportOpenDataProcessor(var asyncHBaseClient: AsyncHBaseClient) extends ExtendedProcessor
    with EventsourcedProcessor {
  override def processorId = EXPORT_OPEN_DATA_PROCESSOR <<

  private var cancellable: Cancellable = null
  private val scheduleInterval = 10 second
  private val pSeqMap = scala.collection.mutable.Map.empty[String, Long]
  private var manager: ExportOpenDataManager = null

  override def preStart(): Unit = {
    super.preStart()
    val config = context.system.settings.config
    val exportData = config.getBoolean("akka.opendata.enabled-export")
    if (!exportData) return
    pSeqMap ++= loadOpenDataProcessors(context.system.settings.config.getString("akka.opendata.processors-map-path"))
    manager = new ExportOpenDataManager(asyncHBaseClient, context)
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

  private def loadOpenDataProcessors(mapConfigPath: String): Map[String, Long] = {
    val in: InputStream = this.getClass.getClassLoader.getResourceAsStream(mapConfigPath)
    val set = (new Eval()(IOUtils.toString(in))).asInstanceOf[Set[String]]
    val map = Map.empty[String, Long]
    set.foreach(map.put(_, 0L))
    map
  }
}

class ExportOpenDataManager {
  private var asyncHBaseClient: AsyncHBaseClient = null
  private var fs: FileSystem = null
  private var actorContext: ActorContext = null
  var messagesBatchSize: Long = 100L
  private var snapshotHdfsDir: String = ""
  private var exportSnapshotHdfsDir: String = ""
  private var exportMessagesHdfsDir: String = ""
  private var messagesTable: String = ""
  private var messagesFamily: String = ""
  implicit var pluginPersistenceSettings: PluginPersistenceSettings = null
  private val BUFFER_SIZE = 2048
  private val SCAN_MAX_NUM_ROWS = 50
  implicit var executionContext: ExecutionContext = null
  implicit var serialization: Serialization = null

  def this(asyncHBaseClient: AsyncHBaseClient, context: ActorContext) {
    this()
    actorContext = context
    val config = context.system.settings.config
    fs = openHdfsSystem(config.getString("akka.opendata.hdfs-host"))
    messagesBatchSize = config.getLong("akka.opendata.messages-batch-size")
    snapshotHdfsDir = config.getString("hadoop-snapshot-store.snapshot-dir")
    exportSnapshotHdfsDir = config.getString("akka.opendata.export-snapshot-hdfs-dir")
    exportMessagesHdfsDir = config.getString("akka.opendata.export-messages-hdfs-dir")
    messagesTable = config.getString("hbase-journal.table")
    messagesFamily = config.getString("hbase-journal.family")
    pluginPersistenceSettings = PluginPersistenceSettings(config, JOURNAL_CONFIG)
    this.asyncHBaseClient = asyncHBaseClient
    executionContext = context.system.dispatcher
    serialization = SerializationExtension(context.system)
  }

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
            }, classOf[Snapshot]) match {
              case Success(snapshot) =>
                snapshot.data.asInstanceOf[AnyRef]
              case Failure(ex) =>
                sys.error(s"""Failed to deserialize snapshot file ${path.getName}, error : ${ex.getMessage}""")
                return seqNum
            }
        val exportSnapshotPath = new Path(exportSnapshotHdfsDir, desc.toFilename)
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
      builder.substring(0, builder.length - 1)
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
        val writer = new BufferedWriter(new OutputStreamWriter(fs.create(new Path(exportMessagesHdfsDir, s"$processorId~${toSeqNum - 1}~${System.currentTimeMillis()}"))))
        writer.write("{\"messages\":[")
        writer.write(data.toString())
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