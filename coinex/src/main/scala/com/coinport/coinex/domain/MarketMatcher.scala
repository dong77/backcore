package com.coinport.coinex.domain.models

class MarketMatcher(m: Market) {
  private var market = m
/*
  def addOrder(order: Order) = {
    checkOrder(order)
    val side = order.side
    val data = order.data
    val price = data.getPrice
    var sellAmount = data.amount

    var reverseSideHasMoreOrderToMatch = true

    // if the top order on the same side is a market price order, it means
    // the reverse side has no limit-price-order to match at all, we stop.
    market.getPendingOrders(side).headOption match {
      case Some(d) if d.price.isEmpty =>
        reverseSideHasMoreOrderToMatch = false
      case _ =>
    }

    val reverseOrders = market.getPendingOrders(order.side.reverse)

    while (reverseSideHasMoreOrderToMatch && sellAmount > 0) {
      reverseOrders.headOption match {
        case Some(d) =>
          data.price match {
            case Some(p0) if p0 > 0 =>
              // new order is a limit price order
              d.price match {
                case Some(p1) if p1 > 0 =>
                  // reverse order is a limit price order
                  if (p0 * p1 <= 1) { // we have a match and we use p1 as the deal price
                    if (p1 * d.amount >= sellAmount) {
                      market = market.addOrder(Order(side.reverse, d.copy(amount = d.amount - sellAmount / p1)))
                      sellAmount = 0
                    } else {
                      market = market.removeOrder(d.id)
                      sellAmount -= p1 * d.amount
                    }
                  }
                case _ =>
                  // reverse order is a market price order, so we have a match, we use p0 as deal price
                  if (sellAmount * p0 < d.amount) {
                    market = market.addOrder(Order(side.reverse, d.copy(amount = d.amount - sellAmount * p0)))
                    sellAmount = 0
                  } else {
                    market = market.removeOrder(d.id)
                    sellAmount -= d.amount / p0
                  }
              }
            case _ =>
              // new order is a market price order
              d.price match {
                case Some(p1) if p1 > 0 =>
                  // reverse order is a limit price order, we use p1 as deal price
                  if (p1 * d.amount >= sellAmount) {
                    market = market.addOrder(Order(side.reverse, d.copy(amount = d.amount - sellAmount / p1)))
                    sellAmount = 0
                  } else {
                    market = market.removeOrder(d.id)
                    sellAmount -= p1 * d.amount
                  }

                case _ =>
                // reverse order is a market price order, so we have a match, we use p0 as deal price
              }
          }

        case _ =>
          reverseSideHasMoreOrderToMatch = false
      }
    }

    if (sellAmount > 0) {
      market = market.addOrder(Order(side, data.copy(amount = sellAmount)))
    }
  }

  def checkOrder(order: Order) {
    assert(order.side == m.side1 || order.side == m.side2)
  }*/
}