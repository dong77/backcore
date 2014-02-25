package com.coinport.coinex.domain

case class OrderData(id: Long, amount: Double, price: Double = 0)
case class Order(side: MarketSide, data: OrderData)

sealed trait OrderCondition {
  def eval: Boolean
}

case class ConditionalOrder(condition: OrderCondition, order: Order)
