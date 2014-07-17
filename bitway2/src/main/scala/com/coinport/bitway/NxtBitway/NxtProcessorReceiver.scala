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

class NxtProcessorReceiver(config: BitwayConfig, sender: Actor) extends Actor with ActorLogging {
  val client = new Jedis(config.ip, config.port)
  val serializer = new ThriftBinarySerializer()
  val responseChannel = config.responseChannelPrefix + Currency.Nxt.value.toString

  implicit val executeContext = context.system.dispatcher

  override def preStart = {
    super.preStart
    listenAtRedis(10)
  }

  def receive = LoggingReceive {
    case ListenAtRedis =>
      client.lpop(responseChannel) match {
        case Some(s: Array[Byte]) =>
          sender.sender() ! serializer.fromBinary(s, classOf[BitwayMessage.Immutable])
          listenAtRedis()
        case None =>
          listenAtRedis(5)
      }
  }

  private def listenAtRedis(timeout: Long = 0) {
    context.system.scheduler.scheduleOnce(timeout.seconds, self, ListenAtRedis)(context.system.dispatcher)
  }
}

