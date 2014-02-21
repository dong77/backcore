package com.coinport.coinex
import scala.concurrent.duration._
import akka.actor._
import akka.persistence._
import scala.collection.mutable
import java.util.Random
import Domain._


class AccountProcessor(marketProcessorPath: ActorPath)
  extends common.ExtendedProcessor[AccountProcessorState] with ActorLogging {
  override val processorId = "coinex_account_processor"
  var state = new AccountProcessorState()

  override val receiveMessage: Receive = {
    case SnapshotOffer(_, _) =>

    case DebugDump =>
      println("-" * 100 + "\n" + state.toString)

    case DebugResetState =>
      state = new AccountProcessorState()
      log.info("processor state reset")

    case DoDeposit(deposit) =>
    case DoWithdrawal(withdrawal) =>
    case cmd @ SubmitOrder(o: BuyOrder) =>
    case cmd @ SubmitOrder(o: SellOrder) =>
    case CancelOrder(o: BuyOrder) =>
    case CancelOrder(o: SellOrder) =>

    case msg =>
      log.error("updateState not supported: {}", msg)

  }
}

class AccountProcessorState {
  // use immutable collections
}