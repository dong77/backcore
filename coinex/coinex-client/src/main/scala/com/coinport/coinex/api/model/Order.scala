/**
 * Copyright (C) 2014 Coinport Inc.
 */

package com.coinport.coinex.api.model

import com.coinport.coinex.data._
import com.coinport.coinex.data.Implicits._
import scala.Some
import com.coinport.coinex.data.Currency._

object Operations extends Enumeration {
  type Operation = Value
  val Buy, Sell = Value

  implicit def reverse(operation: Operation) = operation match {
    case Buy => Sell
    case Sell => Buy
  }
}

import com.coinport.coinex.api.model.Operations._

// Order from the view of users
case class UserOrder(
    uid: String,
    operation: Operation,
    subject: Currency,
    currency: Currency,
    price: Option[Double],
    amount: Option[Double],
    total: Option[Double],
    status: Int = 0,
    id: String = "0",
    submitTime: Long = 0L,
    finishedQuantity: Double = 0.0,
    finishedAmount: Double = 0.0) {
  //  buy: money   out, subject in
  // sell: subject out, money   in
  val marketSide = operation match {
    case Buy => currency ~> subject
    case Sell => subject ~> currency
  }

  def toDoSubmitOrder(): DoSubmitOrder = {
    operation match {
      case Buy =>
        // convert price
        val newPrice = price map {
          value => value.internalValue(marketSide.reverse).reverse
        }
        // regard total as quantity
        val quantity: Long = total match {
          case Some(t) => t.internalValue(currency)
          case None => (amount.get * price.get).internalValue(currency)
        }
        val limit = amount.map(_.internalValue(subject))
        DoSubmitOrder(marketSide, Order(uid.toLong, id.toLong, quantity, newPrice, limit))
      case Sell =>
        // TODO: handle None total or price
        val newPrice = price.map(p => p.internalValue(marketSide))
        val quantity: Long = amount match {
          case Some(a) => a.internalValue(subject)
          case None =>
            if (total.isDefined && price.isDefined) {
              val totalValue = price.get.reverse * total.get
              totalValue.internalValue(subject)
            } else 0
        }
        val limit = total map {
          total => total.internalValue(currency)
        }
        DoSubmitOrder(marketSide, Order(uid.toLong, id.toLong, quantity, newPrice, limit))
    }
  }
}

object UserOrder {
  def fromOrderInfo(orderInfo: OrderInfo): UserOrder = {
    // all are sell-orders
    val marketSide = orderInfo.side
    val order = orderInfo.order

    val unit1 = marketSide._1
    val unit2 = marketSide._2
    if (marketSide.ordered) {
      // sell
      val price: Option[Double] = order.price.map {
        p => p.externalValue(marketSide)
      }
      val amount: Option[Double] = Some(order.quantity.externalValue(unit1))
      val total: Option[Double] = order.takeLimit.map(t => t.externalValue(unit2))

      // finished quantity = out
      val finishedQuantity = orderInfo.outAmount.externalValue(unit1)
      // finished amount = in
      val finishedAmount = orderInfo.inAmount.externalValue(unit2)

      val status = orderInfo.status
      val id = order.id
      val timestamp = order.timestamp.getOrElse(0L)

      UserOrder(order.userId.toString, Sell, unit1, unit2, price, amount, total, status.value, id.toString, timestamp)
        .copy(finishedQuantity = finishedQuantity, finishedAmount = finishedAmount)
    } else {
      // buy
      val price: Option[Double] = order.price.map {
        p => p.reverse.externalValue(marketSide.reverse)
      }

      val amount = order.takeLimit.map(t => t.externalValue(unit2))
      val total = Some(order.quantity.externalValue(unit1))

      // finished quantity = in
      val finishedQuantity = orderInfo.inAmount.externalValue(unit2)

      // finished amount = out
      val finishedAmount = orderInfo.outAmount.externalValue(unit1)

      val status = orderInfo.status
      val id = order.id
      val timestamp = order.timestamp.getOrElse(0L)

      UserOrder(order.userId.toString, Buy, unit2, unit1, price, amount, total, status.value, id.toString, timestamp)
        .copy(finishedQuantity = finishedQuantity, finishedAmount = finishedAmount)
    }
  }
}