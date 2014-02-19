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

    val mso = new MarketSellOrders()
    val mbo = new MarketBuyOrders()
    val lpbo = new LimitPriceBuyOrders()
    val lpso = new LimitPriceSellOrders()
    var lastPrice: Option[Double] = None

    override def toString = {
      "" //  "[State: %s]:\nbuyOrders: %s\nsellOrders: %s".format(market, buyOrders.mkString(", "), sellOrders.mkString(", "))
    }

    // @tailrec
    def addMarketBuyOrder(o: BuyOrder) = {
    }
    def addMarketSellOrder(o: SellOrder) = {
    }
    def addLimitPriceBuyOrder(o: BuyOrder) = {
    }
    def addLimitPriceSellOrder(o: SellOrder) = {
    }
  }

  class MarketSellOrders extends Orders[SellOrder] {
    def doCompare(a: SellOrder, b: SellOrder): Int = {
      if (a.id < b.id) -1 else if (a.id > b.id) 1 else 0
    }
  }

  class MarketBuyOrders extends Orders[BuyOrder] {
    def doCompare(a: BuyOrder, b: BuyOrder): Int = {
      if (a.id < b.id) -1 else if (a.id > b.id) 1 else 0
    }
  }

  class LimitPriceBuyOrders extends Orders[BuyOrder] {
    def doCompare(a: BuyOrder, b: BuyOrder): Int = {
      if (a.price.get > b.price.get) -1
      else if (a.price.get < b.price.get) 1
      else if (a.id < b.id) -1
      else if (a.id > b.id) 1
      else 0
    }
  }

  class LimitPriceSellOrders extends Orders[SellOrder] {
    def doCompare(a: SellOrder, b: SellOrder): Int = {
      if (a.price.get < b.price.get) -1
      else if (a.price.get > b.price.get) 1
      else if (a.id < b.id) -1
      else if (a.id > b.id) 1
      else 0
    }
  }

  trait Orders[T] {
    implicit val sorting = new Ordering[T] { def compare(a: T, b: T): Int = doCompare(a, b) }
    def doCompare(a: T, b: T): Int
    var orders = mutable.SortedSet.empty[T]
    def apply() = orders
    def reset() = { orders = mutable.SortedSet.empty[T] }
  }

}

class MarketProcessor(market: Market, accountProcessorPath: ActorPath) extends EventsourcedProcessor with ActorLogging {
  import MarketProcessor._
  override def processorId = "coinex_market_processor_" + market
  println("============market processor created: " + self.path)
  val channel = context.actorOf(PersistentChannel.props("coinex-mp2ap-" + market), name = "mp2ap_" + market)

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
        println("---buy order submitted" + o)
        val result =
          if (o.price.isDefined) state.addLimitPriceBuyOrder(o)
          else state.addMarketBuyOrder(o)
        channel forward Deliver(Persistent(result), accountProcessorPath)

      case OrderSubmitted(o: SellOrder) =>
        println("---sell order submitted" + o)
        val result =
          if (o.price.isDefined) state.addLimitPriceSellOrder(o)
          else state.addMarketSellOrder(o)
        channel forward Deliver(Persistent(result), accountProcessorPath)

      case evt @ OrderCancelled(o: BuyOrder) =>
        //updateSpendable(o.uid, o.market.out, a => a.copy(spendable = a.spendable - o.outAmount, locked = a.locked + o.outAmount))
        channel forward Deliver(Persistent(evt), accountProcessorPath)

      case evt @ OrderCancelled(o: SellOrder) =>
        //updateSpendable(o.uid, o.market.out, a => a.copy(spendable = a.spendable - o.outAmount, locked = a.locked + o.outAmount))
        channel forward Deliver(Persistent(evt), accountProcessorPath)

      case msg =>
        log.error("updateState not supported: {}", msg)
    }

  }
}
