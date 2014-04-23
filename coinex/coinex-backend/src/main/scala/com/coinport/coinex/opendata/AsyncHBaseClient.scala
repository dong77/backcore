package com.coinport.coinex.opendata

import org.hbase.async.HBaseClient
import akka.persistence.hbase.journal.{ PluginPersistenceSettings, HBaseClientFactory }
import akka.actor.ExtendedActorSystem
import akka.persistence.PersistenceSettings

case class AsyncHBaseClient(implicit val system: ExtendedActorSystem) {
  private val config = system.settings.config
  // use journal config as hbse client config
  private val hBasePersistenceSettings = PluginPersistenceSettings(config, "hbase-journal")
  private val client = HBaseClientFactory.getClient(hBasePersistenceSettings, new PersistenceSettings(config.getConfig("akka.persistence")))

  def getClient(): HBaseClient = {
    client
  }

  def shutDown() {
    HBaseClientFactory.shutDown()
  }
}