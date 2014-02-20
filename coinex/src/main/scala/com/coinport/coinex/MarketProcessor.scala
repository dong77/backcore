package com.coinport.coinex
import scala.concurrent.duration._
import akka.actor._
import akka.persistence._
import scala.collection.mutable
import java.util.Random
import scala.util.Sorting
import Domain._
import scala.annotation.tailrec

// if market is Market(BTC, RMB), then we call orders with Market(BTC, RMB) buy orders
object MarketProcessor {
  class State(val market: Market) {
    

    val buyMarketOrders = {
       implicit val sortingSell = new Ordering[SellOrder] {
          def compare(a: SellOrder, b: SellOrder): Int = 1
       }
      mutable.SortedSet.empty[BuyOrder]
    }
    val buyLimitPriceOrders = mutable.SortedSet.empty[BuyOrder]
    val sellMarketOrders = mutable.SortedSet.empty[SellOrder]
    val sellLimitPriceOrders = mutable.SortedSet.empty[SellOrder]
    var lastPrice: Option[Double] = None

    override def toString = {
      "" //  "[State: %s]:\nbuyOrders: %s\nsellOrders: %s".format(market, buyOrders.mkString(", "), sellOrders.mkString(", "))
    }

    // @tailrec
    def addBuyOrder(o: BuyOrder) = {
    }

    def addSellOrder(o: SellOrder) = {
    }
  }

}

class MarketProcessor(market: Market, accountProcessorPath: ActorPath) extends EventsourcedProcessor with ActorLogging {
  import MarketProcessor._
  override def processorId = "coinex_market_processor_" + market
  println("============market processor created: " + self.path)
  val channel = context.actorOf(Channel.props("coinex-mp2ap-" + market), name = "mp2ap_" + market)

  var state = new State(market)

  override val receiveCommand: Receive = {
    case cmd =>
      log.info("--- CMD: {}", cmd)
      if (receiveCommandInternal.isDefinedAt(cmd)) receiveCommandInternal(cmd)
  }
  override val receiveRecover: Receive = {
    case event =>
      log.info("--- EVT: {}, lastSequenceNr: {}", event, lastSequenceNr)
      if (receiveRecoverInternal.isDefinedAt(event)) receiveRecoverInternal(event)
  }

  private def receiveRecoverInternal: Receive = {
    case SnapshotOffer(_, _) =>
    case evt: Evt => updateState(evt)
  }

  private def receiveCommandInternal: Receive = {
    case DebugDump => println("-" * 100 + "\n" + state.toString)
    case DebugResetState => state = new State(market)

    case msg @ ConfirmablePersistent(e: OrderSubmitted, _, _) =>
      persist(e)(updateState)
      msg.confirm

    case msg =>
      log.error("receiveCommand not supported: {}", msg)
  }

  def updateState(evt: Evt) = {
    evt match {
      case OrderSubmitted(o: BuyOrder) =>
        state.addBuyOrder(o)

      case OrderSubmitted(o: SellOrder) =>
        state.addSellOrder(o)

      case evt @ OrderCancelled(o: BuyOrder) =>
        //updateSpendable(o.uid, o.market.out, a => a.copy(spendable = a.spendable - o.outAmount, locked = a.locked + o.outAmount))
        channel forward Deliver(getCurrentPersistentMessage.withPayload(evt), accountProcessorPath)

      case evt @ OrderCancelled(o: SellOrder) =>
        //updateSpendable(o.uid, o.market.out, a => a.copy(spendable = a.spendable - o.outAmount, locked = a.locked + o.outAmount))
        channel forward Deliver(getCurrentPersistentMessage.withPayload(evt), accountProcessorPath)

      case msg =>
        log.error("updateState not supported: {}", msg)
    }

  }
}
