package akka.persistence.hbase.journal

import com.typesafe.config.Config

/**
 *
 * @param table table to be used to store akka messages
 * @param family column family name to store akka messages in
 * @param partitionCount Number of regions the used Table is partitioned to.
 *                       Currently must be FIXED, and not change during the lifetime of the app.
 *                       Should be a bigger number, for example 10 even if you currently have 2 regions, so you can split regions in the future.
 * @param scanBatchSize when performing scans, how many items to we want to obtain per one next(N) call
 * @param replayDispatcherId dispatcher for fetching and replaying messages
 */
case class PluginPersistenceSettings(
  zookeeperQuorum: String,
  table: String,
  family: String,
  partitionCount: Int,
  scanBatchSize: Int,
  pluginDispatcherId: String,
  replayDispatcherId: String,
  publishTestingEvents: Boolean,
  snapshotHdfsDir: String,
  hdfsDefaultName:String
)

object PluginPersistenceSettings {
  def apply(rootConfig: Config, persistenceConf: String): PluginPersistenceSettings = {
    val persistenceConfig = rootConfig.getConfig(persistenceConf)
    PluginPersistenceSettings(
      zookeeperQuorum      = persistenceConfig.getString("hbase.zookeeper.quorum"),
      table                = persistenceConfig.getString("table"),
      family               = persistenceConfig.getString("family"),
      partitionCount       = persistenceConfig.getInt("partition.count"),
      scanBatchSize        = persistenceConfig.getInt("scan-batch-size"),
      pluginDispatcherId   = persistenceConfig.getString("plugin-dispatcher"),
      replayDispatcherId   = persistenceConfig.getString("replay-dispatcher"),
      publishTestingEvents = persistenceConfig.getBoolean("publish-testing-events"),
      snapshotHdfsDir      = persistenceConfig.getString("snapshot-dir"),
      hdfsDefaultName      = persistenceConfig.getString("hdfs-default-name")
    )
  }
}
