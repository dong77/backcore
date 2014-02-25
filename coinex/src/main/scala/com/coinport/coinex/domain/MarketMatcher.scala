package com.coinport.coinex.domain

class MarketMatcher(outCurrency: Currency, inCurrency: Currency) {
  private var market = Market(outCurrency, inCurrency)

  def apply() = market

  def addOrder(order: Order) = {
    checkOrder(order)
    val (sellSide, buySide) = (order.side, order.side.reverse)
    val sellOrder = order.data
    
    def sellMpos = market.getMarketPriceOrderPool(sellSide)
    def sellLpos = market.getLimitPriceOrderPool(sellSide)
    def buyMpos = market.getMarketPriceOrderPool(buySide)
    def buyLpos = market.getLimitPriceOrderPool(buySide)

    var (sellAmount, continue) = (sellOrder.amount, true)

    // if the top order on the same side is a market price order, it means
    // the reverse side has no limit-price-order to match at all, we stop.
    if (sellMpos.nonEmpty) {
      continue = false // we have already a pending market-price order
    }

    while (continue && sellAmount > 0) {
      buyMpos.headOption match {
        case Some(buyOrder) if sellOrder.price > 0 =>
          // new LPO to match existing MPOs
          val dealAmount = buyOrder.amount / sellOrder.price
          sellAmount -= dealAmount
          if (sellAmount >= 0) {
            // new order is not fully executed but reverse order is.
            market = market.removeOrder(buyOrder.id)
          } else {
            // new order is fully executed but reverse is not.
            market = market.addOrder(Order(buySide, buyOrder.copy(amount = -sellAmount * sellOrder.price)))
          }

        case _ =>
          buyLpos.headOption match {
            case Some(reverse) if reverse.price * sellOrder.price <= 1 =>
              // new LPO or MPO to match existing LPOs, using reverse price as deal price
              val dealAmount = reverse.amount * reverse.price
              sellAmount -= dealAmount
              if (sellAmount >= 0) {
                // new order is not fully executed but reverse order is.
                market = market.removeOrder(reverse.id)
              } else {
                // new order is fully executed but reverse order is not.
                market = market.addOrder(Order(buySide, reverse.copy(amount = -sellAmount / reverse.price)))
              }

            case _ => continue = false
          }
      }
    }

    if (sellAmount > 0) {
      market = market.addOrder(Order(sellSide, sellOrder.copy(amount = sellAmount)))
    }
  }

  def checkOrder(order: Order) {
    assert(market.bothSides.contains(order.side))
  }
}