package akka.persistence.hbase.journal

import akka.actor.{Actor, ActorLogging}
import org.hbase.async.{HBaseClient, KeyValue}
import java.util. { ArrayList => JArrayList }
import akka.persistence.hbase.common.{AsyncBaseUtils, HBaseSerialization}
import org.apache.hadoop.conf.Configuration

// todo split into one API classes and register the impls as extensions
trait HBaseJournalBase extends HBaseSerialization with AsyncBaseUtils {
  this: Actor with ActorLogging =>

  def client: HBaseClient

  def hBasePersistenceSettings: PluginPersistenceSettings
  def hadoopConfig: Configuration

  override def getTable = hBasePersistenceSettings.table
  override def getFamily = hBasePersistenceSettings.family

  type AsyncBaseRows = JArrayList[JArrayList[KeyValue]]

  /** Used to avoid writing all data to the same region - see "hot region" problem */
  def partition(sequenceNr: Long): Long = sequenceNr % hBasePersistenceSettings.partitionCount

}
