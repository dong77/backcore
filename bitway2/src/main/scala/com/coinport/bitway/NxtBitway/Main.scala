package com.coinport.bitway.NxtBitway

import com.typesafe.config.ConfigFactory

/**
 * Created by chenxi on 7/17/14.
 */
object Main {
  def main(args: Array[String]): Unit = {
    val bitwayConfig = ConfigFactory.load()
    val nxtConfig = bitwayConfig.getConfig("akka.nxt")

    val nxt = new NxtBitway(nxtConfig)
    nxt.start()
  }
}
