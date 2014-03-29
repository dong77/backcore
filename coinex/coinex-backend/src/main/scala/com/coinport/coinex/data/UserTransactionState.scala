/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 * All classes here are case-classes or case-objects. This is required since we are
 * maintaining an in-memory state that's immutable, so that while snapshot is taken,
 * the in-memory state can still be updated.
 */

package com.coinport.coinex.data

import scala.collection.SortedMap
import org.slf4s.Logger

object UserTransactionState {
  type TransactionList = Seq[TransactionItem]
  type orderTransMap = SortedMap[Long, TransactionList]
  type UserTransaction = (TransactionList, orderTransMap)
  type ItemMap = Map[Long, UserTransaction]
  val EmptyItemMap = Map.empty[Long, UserTransaction]
  val ARCHIVE_SIZE = 1000
  val MAX_MAINTAIN_SIZE = 2000
}

case class UserTransactionState(
    maxMaintainSize: Int = UserTransactionState.MAX_MAINTAIN_SIZE,
    archiveSize: Int = UserTransactionState.ARCHIVE_SIZE,
    itemMap: UserTransactionState.ItemMap = UserTransactionState.EmptyItemMap,
    itemReverseMap: UserTransactionState.ItemMap = UserTransactionState.EmptyItemMap) {

  def addItem(userId: Long, orderId: Long, item: TransactionItem) = {
    val map = itemMap - userId
    copy(itemMap = map + (userId -> generateUserTransaction(itemMap, userId, orderId, item)))
  }

  def addReverseItem(userId: Long, orderId: Long, item: TransactionItem) = {
    val map = itemReverseMap - userId
    copy(itemReverseMap = map + (userId -> generateUserTransaction(itemReverseMap, userId, orderId, item)))
  }

  def getItems(userId: Long, orderId: Option[Long], from: Long, num: Int): Seq[TransactionItem] = {
    itemMap.get(userId) match {
      case Some(userTrans) =>
        if (!orderId.isDefined) userTrans._1.reverse.slice(from.toInt, from.toInt + num)
        else userTrans._2.get(orderId.get) match {
          case Some(items) => items.reverse.slice(from.toInt, from.toInt + num)
          case None => Nil
        }
      case None => Nil
    }
  }

  def getReverseItems(userId: Long, orderId: Option[Long], from: Long, num: Int): Seq[TransactionItem] = {
    itemReverseMap.get(userId) match {
      case Some(userTrans) =>
        if (!orderId.isDefined) userTrans._1.reverse.slice(from.toInt, from.toInt + num)
        else userTrans._2.get(orderId.get) match {
          case Some(items) => items.reverse.slice(from.toInt, from.toInt + num)
          case None => Nil
        }
      case None => Nil
    }
  }

  private def archiveItems(userTransaction: UserTransactionState.UserTransaction) = {
    //TODO(chenxi) we drop redundant transaction records temporarily, & we will persistent those records in next version.
    val tranList = if (userTransaction._1.size > maxMaintainSize) userTransaction._1.reverse.drop(archiveSize)
    else userTransaction._1
    val orderMap = if (userTransaction._2.size > maxMaintainSize) userTransaction._2.drop(archiveSize)
    else userTransaction._2
    (tranList, orderMap)
  }

  private def generateUserTransaction(itemMap: UserTransactionState.ItemMap, userId: Long, orderId: Long, item: TransactionItem) = {
    val userTran = itemMap.get(userId)
    if (userTran.isDefined) {
      val (transList, orderMap) = archiveItems(userTran.get)
      val orderMap2 = orderMap - orderId

      val newItems = orderMap.get(orderId) match {
        case Some(items) => items ++ Seq(item)
        case None => Seq(item)
      }

      (transList ++ Seq(item), orderMap2 + (orderId -> newItems))
    } else (Seq(item), SortedMap(orderId -> Seq(item.copy())))
  }
}
