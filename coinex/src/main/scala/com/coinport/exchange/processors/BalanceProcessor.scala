package com.coinport.exchange.processors

import com.typesafe.config.ConfigFactory
import akka.util.Timeout
import scala.concurrent.duration._
import akka.actor._
import akka.persistence._
import com.coinport.exchange.common._
import com.coinport.exchange.actors.LocalRouters
import scala.collection.mutable
import com.coinport.exchange.domain.Transfer
import com.coinport.exchange.domain.Events._
import com.coinport.exchange.domain.Commands._

class BalanceProcessor(routers: LocalRouters) extends EventsourcedProcessor with ActorLogging {
  override def processorId = "balance_processor"
  var lastProcessedSeqNr = -1L
  /*
  val markethubChannel = context.actorOf(PersistentChannel.props("balance_2_markethub_channel",
    PersistentChannelSettings(redeliverInterval = 3 seconds, redeliverMax = 15)),
    name = "balance_2_markethub_channel")

  val transferChannel = context.actorOf(PersistentChannel.props("balance_2_transfer_channel",
    PersistentChannelSettings(redeliverInterval = 3 seconds, redeliverMax = 15)),
    name = "balance_2_transfer_channel")
  */

  val balances = mutable.HashMap[Long, Double]()

  override val receiveRecover: Receive = {
    case e: Event => updateState(e)
    case _ =>
  }

  override val receiveCommand: Receive = {
    case c @ ConfirmablePersistent(event, _, _) =>
      persist(event)(updateState)
      c.confirm()
    case _ =>
  }

  def updateState(event: Any) = {
    event match {
      case DepositConfirmed(t) =>
        if (lastProcessedSeqNr < t.id) {
          lastProcessedSeqNr = t.id
          val amount = balances.getOrElse(t.uid, 0.0) + t.amount
          balances += t.uid -> amount
          println("------bp: processed  " + event)
        } else {
          println("------bp: skipped  " + event)
        }

      case _ =>
    }

    println("====== balances: " + balances.mkString("\n"))
  }
}
