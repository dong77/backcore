package com.coinport.bitway.NxtBitway

import com.mongodb.casbah.Imports._
import dispatch._
import scala.concurrent.ExecutionContext.Implicits.global

object NxtBitway{
  private val targetUrl="http://localhost:7876/nxt"

  def main(args: Array[String]): Unit = {

    def getJsonResult(map: Map[String, String]): String = {
      val rq: Req = url(targetUrl) <<? map
      val result = Http(rq)
      result().getResponseBody
    }


    val json = getJsonResult(Map("requestType" -> "getBlockchainStatus"))
    println("json>>>>>>>>>>>>"+json)
  }
}
