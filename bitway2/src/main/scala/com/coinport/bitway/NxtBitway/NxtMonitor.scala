package com.coinport.bitway.NxtBitway

import akka.actor.{Actor, ActorLogging}
import redis.clients.jedis.Jedis
import com.coinport.coinex.serializers.ThriftBinarySerializer
import com.coinport.coinex.data.Currency
import akka.event.LoggingReceive
import scala.concurrent.duration._

/**
 * Created by chenxi on 7/17/14.
 */
class NxtMonitor(config: BitwayConfig) extends Actor with ActorLogging {
  val client = new Jedis(config.ip, config.port)
  val serializer = new ThriftBinarySerializer()
  val requestChannel = config.requestChannelPrefix + Currency.Nxt.value.toString

  implicit val executeContext = context.system.dispatcher

  override def preStart = {
    super.preStart
    sendMessageToSelf(10)
  }

  def receive = LoggingReceive {
    case MonitorAtHttp =>
      println("aaaaaaaaaaaaaa")
      sendMessageToSelf(1)
  }

  private def sendMessageToSelf(timeout: Long = 0) {
    context.system.scheduler.scheduleOnce(timeout.seconds, self, MonitorAtHttp)(context.system.dispatcher)
  }
}
