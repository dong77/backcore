package com.coinport.bitway.NxtBitway

import akka.actor.{Actor, ActorLogging}
import com.coinport.coinex.serializers.ThriftBinarySerializer
import com.coinport.coinex.data.Currency
import akka.event.LoggingReceive
import scala.concurrent.duration._
import com.redis._
import com.coinport.bitway.NxtBitway.processor.NxtProcessor

/**
 * Created by chenxi on 7/17/14.
 */
class NxtMonitor(processor: NxtProcessor, config: BitwayConfig) extends Actor with ActorLogging {
  val client = new RedisClient(config.ip, config.port)
  val serializer = new ThriftBinarySerializer()

  val responseChannel = config.responseChannelPrefix + Currency.Nxt.value.toString
  val requestChannel = config.requestChannelPrefix + Currency.Nxt.value.toString

  implicit val executeContext = context.system.dispatcher

  override def preStart = {
    super.preStart
    sendMessageToSelf(1)
  }

  def receive = LoggingReceive {
    case MonitorAtHttp =>
      println("aaaaaaaaaaaaaaa")
      sendMessageToSelf(1)
  }

  private def sendMessageToSelf(timeout: Long = 0) {
    context.system.scheduler.scheduleOnce(timeout.seconds, self, MonitorAtHttp)(context.system.dispatcher)
  }
}
