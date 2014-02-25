package com.coinport.coinex.domain

class MarketMatcher(outCurrency: Currency, inCurrency: Currency) {
  private var market = Market(outCurrency, inCurrency)

  def apply() = market

  def addOrder(order: Order): List[Transaction] = {
    checkOrder(order)
    val (sellSide, buySide) = (order.side, order.side.reverse)
    val sellOrder = order.data

    def sellMpos = market.getMarketPriceOrderPool(sellSide)
    def sellLpos = market.getLimitPriceOrderPool(sellSide)
    def buyMpos = market.getMarketPriceOrderPool(buySide)
    def buyLpos = market.getLimitPriceOrderPool(buySide)

    var (sellAmount, continue) = (sellOrder.amount, true)
    var txs = List.empty[Transaction]

    // if the top order on the same side is a market price order, it means
    // the reverse side has no limit-price-order to match at all, we stop.
    if (sellMpos.nonEmpty) {
      continue = false // we have already a pending market-price order
    }

    // a common internal method to deal with repeated actions.
    def matchOrder(buyOrder: OrderData, actualSellPrice: Double) {
      val actualBuyPrice = 1 / actualSellPrice
      if (sellAmount >= buyOrder.amount * actualBuyPrice) {
        // new sell order is not fully executed but buy order is.
        market = market.removeOrder(buyOrder.id)
        txs ::= Transaction(
          Transfer(sellOrder.id, sellSide.outCurrency, buyOrder.amount * actualBuyPrice),
          Transfer(buyOrder.id, buySide.outCurrency, buyOrder.amount))
        sellAmount -= buyOrder.amount * actualBuyPrice
      } else {
        // new sell order is fully executed but buy order is not.
        val updatedBuyOrder = Order(buySide, buyOrder.copy(amount = buyOrder.amount - sellAmount * actualSellPrice))
        market = market.addOrder(updatedBuyOrder)
        txs ::= Transaction(
          Transfer(sellOrder.id, sellSide.outCurrency, sellAmount),
          Transfer(buyOrder.id, buySide.outCurrency, sellAmount * actualSellPrice))
        sellAmount = 0
      }
    }

    while (continue && sellAmount > 0) {
      buyMpos.headOption match {
        // new LPO to match existing MPOs
        case Some(buyOrder) if sellOrder.price > 0 =>
          matchOrder(buyOrder, actualSellPrice = sellOrder.price)

        case _ =>
          buyLpos.headOption match {
            // new LPO or MPO to match existing LPOs
            case Some(buyOrder) if buyOrder.price * sellOrder.price <= 1 =>
              matchOrder(buyOrder, actualSellPrice = 1 / buyOrder.price)

            case _ =>
              continue = false
          }
      }
    }

    if (sellAmount > 0) {
      market = market.addOrder(Order(sellSide, sellOrder.copy(amount = sellAmount)))
    }

    txs
  }

  def checkOrder(order: Order) {
    assert(market.bothSides.contains(order.side))
  }
}