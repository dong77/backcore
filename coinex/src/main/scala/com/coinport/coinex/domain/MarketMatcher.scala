package com.coinport.coinex.domain.models

class MarketMatcher(currency1: Currency, currency2: Currency) {
  private var market = Market(currency1, currency2)

  def apply() = market

  def addOrder(order: Order) = {
    checkOrder(order)
    val side = order.side
    val data = order.data
    var sellAmount = data.amount

    def mpos = market.getMarketPriceOrderPool(order.side)
    def lpos = market.getLimitPriceOrderPool(order.side)
    def reverseMpos = market.getMarketPriceOrderPool(order.side.reverse)
    def reverseLpos = market.getLimitPriceOrderPool(order.side.reverse)

    var continue = true

    // if the top order on the same side is a market price order, it means
    // the reverse side has no limit-price-order to match at all, we stop.
    if (mpos.nonEmpty) {
      continue = false // we have already a pending market-price order
    }

    while (continue && sellAmount > 0) {
      reverseMpos.headOption match {
        case Some(reverse) if data.price > 0 =>
          // new LPO to match existing MPOs
          val dealAmount = reverse.amount / data.price
          sellAmount -= dealAmount
          if (sellAmount >= 0) {
            // new order is not fully executed but reverse order is.
            market = market.removeOrder(reverse.id)
          } else {
            // new order is fully executed but reverse is not.
            market = market.addOrder(Order(side.reverse, reverse.copy(amount = -sellAmount * data.price)))
          }

        case _ =>
          reverseLpos.headOption match {
            case Some(reverse) if reverse.price * data.price <= 1 =>
              // new LPO or MPO to match existing LPOs, using reverse price as deal price
              val dealAmount = reverse.amount * reverse.price
              sellAmount -= dealAmount
              if (sellAmount >= 0) {
                // new order is not fully executed but reverse order is.
                market = market.removeOrder(reverse.id)
              } else {
                // new order is fully executed but reverse order is not.
                market = market.addOrder(Order(side.reverse, reverse.copy(amount = -sellAmount / reverse.price)))
              }

            case _ => continue = false
          }
      }
    }

    if (sellAmount > 0) {
      market = market.addOrder(Order(side, data.copy(amount = sellAmount)))
    }
  }

  def checkOrder(order: Order) {
    assert(order.side == market.side1 || order.side == market.side2)
  }
}