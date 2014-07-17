package com.coinport.bitway.NxtBitway

/**
 * Created by chenxi on 7/17/14.
 */
import akka.actor.{ActorLogging, Actor}
import akka.event.LoggingReceive
import scala.concurrent.duration._
import redis.clients.jedis.Jedis
import com.coinport.coinex.serializers.ThriftBinarySerializer
import com.coinport.coinex.data.{Currency, BitwayMessage}

class NxtReceiver(config: BitwayConfig) extends Actor with ActorLogging {
  val client = new Jedis(config.ip, config.port)
  val serializer = new ThriftBinarySerializer()
  val responseChannel = config.responseChannelPrefix + Currency.Nxt.value.toString

  implicit val executeContext = context.system.dispatcher

  override def preStart = {
    super.preStart
    sendMessageToSelf(10)
  }

  def receive = LoggingReceive {
    case ListenAtRedis =>
      println("bbbbbbbbbbbb")
      sendMessageToSelf(1)
//      client.lpop(responseChannel) match {
//        case Some(s) =>
//          sender.sender() ! serializer.fromBinary(s, classOf[BitwayMessage.Immutable])
//          waitWhile()
//        case None =>
//          waitWhile(5)
//      }
  }

  private def sendMessageToSelf(timeout: Long = 0) {
    context.system.scheduler.scheduleOnce(timeout.seconds, self, ListenAtRedis)(context.system.dispatcher)
  }
}

