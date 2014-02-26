package com.coinport.coinex.domain

/**
 * MarketManager's job is to update an in-memory state of type `Market`
 * by matching new orders before they are ever added into the Market.
 *
 * When orders match, transactions will be returned.
 *
 * MarketManager can be used by an Akka persistent processor or a view
 * to reflect pending orders and market depth.
 */

class MarketManager(headSide: MarketSide) {

  private var market = Market(headSide)

  def apply() = market

  def reset(market: Option[Market] = None) = market match {
    case Some(m) => this.market = m
    case None => this.market = Market(headSide)
  }

  def addOrder(order: Order): List[Transaction] = {
    checkOrder(order)
    val (takerSide, makerSide) = (order.side, order.side.reverse)
    val takerOrder = order.data

    def takerMpos = market.marketPriceOrderPool(takerSide)
    def takerLpos = market.limitPriceOrderPool(takerSide)
    def makerMpos = market.marketPriceOrderPool(makerSide)
    def makerLpos = market.limitPriceOrderPool(makerSide)

    var (takerQuantity, continue) = (takerOrder.quantity, true)
    var txs = List.empty[Transaction]

    // if the top order on the taker side is a market price order, it means
    // the maker side has no limit-price-order to match at all, we stop.
    if (takerMpos.nonEmpty) {
      continue = false // we have already a pending market-price order
    }

    // a common internal method to deal with repeated actions.
    def matchOrder(makerOrder: OrderData, actualTakerPrice: Double) {
      val actualMakerPrice = 1 / actualTakerPrice
      if (takerQuantity >= makerOrder.quantity * actualMakerPrice) {
        // new taker order is not fully executed but maker order is.
        market = market.removeOrder(makerOrder.id)
        takerQuantity -= makerOrder.quantity * actualMakerPrice
        txs ::= Transaction(
          Transfer(takerOrder.id, takerSide.outCurrency, makerOrder.quantity * actualMakerPrice, takerQuantity == 0),
          Transfer(makerOrder.id, makerSide.outCurrency, makerOrder.quantity, true))

      } else {
        // new taker order is fully executed but maker order is not.
        val updatedMakerOrder = Order(makerSide, makerOrder.copy(quantity = makerOrder.quantity - takerQuantity * actualTakerPrice))
        market = market.addOrder(updatedMakerOrder)
        txs ::= Transaction(
          Transfer(takerOrder.id, takerSide.outCurrency, takerQuantity, true),
          Transfer(makerOrder.id, makerSide.outCurrency, takerQuantity * actualTakerPrice, false))
        takerQuantity = 0
      }
    }

    while (continue && takerQuantity > 0) {
      makerMpos.headOption match {
        // new LPO to match existing MPOs
        case Some(makerOrder) if takerOrder.price > 0 =>
          matchOrder(makerOrder, actualTakerPrice = takerOrder.price)

        case _ =>
          makerLpos.headOption match {
            // new LPO or MPO to match existing LPOs
            case Some(makerOrder) if makerOrder.price * takerOrder.price <= 1 =>
              matchOrder(makerOrder, actualTakerPrice = 1 / makerOrder.price)

            case _ =>
              continue = false
          }
      }
    }

    if (takerQuantity > 0) {
      market = market.addOrder(order.copy(data = takerOrder.copy(quantity = takerQuantity)))
    }

    txs
  }

  def checkOrder(order: Order) {
    assert(market.bothSides.contains(order.side))
  }
}