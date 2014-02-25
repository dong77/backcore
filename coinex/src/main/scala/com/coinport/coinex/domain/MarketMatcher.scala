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
      if (data.price > 0) {
        reverseMpos.headOption match {
          case Some(reverse) =>
            // new LPO to match existing MPOs
            if (data.price * sellAmount >= reverse.amount) {
              // new order is not fully executed but reverse order is.
              market = market.removeOrder(reverse.id)
              val sold = reverse.amount / data.price
              sellAmount -= sold
            } else {
              // new order is fully executed but reverse is not.
              val newReverse = reverse.copy(amount = reverse.amount - data.price * sellAmount)
              market = market.addOrder(Order(side.reverse, newReverse))
              sellAmount = 0
            }

          case None =>
            reverseLpos.headOption match {
              case Some(reverse) if reverse.price * data.price <= 1 =>
                // new LPO to match existing LPOs, using reverse price as deal price
                if (sellAmount >= reverse.amount * reverse.price) {
                  // new order is not fully executed but reverse order is.
                  market = market.removeOrder(reverse.id)
                  sellAmount -= reverse.amount * reverse.price
                } else {
                  // new order is fully executed but reverse order is not.
                  val newReverse = reverse.copy(amount = reverse.amount - sellAmount / reverse.price)
                  market = market.addOrder(Order(side.reverse, newReverse))
                  sellAmount = 0
                }

              case _ =>
                // new LPO to match nothing (empty reverse)
                continue = false
            }
        }
      } else {
        reverseLpos.headOption match {
          case Some(reverse) =>
            // new MPO to match existing LPOs
            if (sellAmount >= reverse.price * reverse.amount) {
              // reverse order will be fully executed, but new order is not.
              market = market.removeOrder(reverse.id)
              sellAmount -= reverse.amount * reverse.price
            } else {
              // new order will be fully executed, but reverse is not.
              val newReverse = reverse.copy(amount = reverse.amount - sellAmount / reverse.price)
              market = market.addOrder(Order(side.reverse, newReverse))
              sellAmount = 0
            }
          case None =>
            // new MPO to match nothing (empty reverse)
            continue = false
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