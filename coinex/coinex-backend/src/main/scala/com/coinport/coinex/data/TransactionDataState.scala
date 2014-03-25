/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 * All classes here are case-classes or case-objects. This is required since we are
 * maintaining an in-memory state that's immutable, so that while snapshot is taken,
 * the in-memory state can still be updated.
 */

package com.coinport.coinex.data

object TransactionDataState {
  type ItemSeq = Seq[TransactionItem]
  val EmptyItemSeq = Seq.empty[TransactionItem]
  val ARCHIVE_SIZE = 50000
  val MAX_MAINTAIN_SIZE = 150000
}

case class TransactionDataState(
    archiveSize: Long = TransactionDataState.ARCHIVE_SIZE,
    maxMaintainSize: Long = TransactionDataState.MAX_MAINTAIN_SIZE,
    transactionItems: TransactionDataState.ItemSeq = TransactionDataState.EmptyItemSeq,
    transactionReverseItems: TransactionDataState.ItemSeq = TransactionDataState.EmptyItemSeq) {

  def addItem(item: TransactionItem) = {
    if (transactionItems.size >= maxMaintainSize)
      copy(transactionItems = archiveItems ++ Seq(item))
    else copy(transactionItems = transactionItems ++ Seq(item))
  }

  def addReverseItem(item: TransactionItem) = {
    if (transactionReverseItems.size >= maxMaintainSize)
      copy(transactionReverseItems = archiveItems ++ Seq(item))
    else copy(transactionReverseItems = transactionReverseItems ++ Seq(item))
  }

  def getItems(from: Long, num: Int): (Seq[TransactionItem]) = {
    transactionItems.reverse.slice(from.toInt, from.toInt + num)
  }

  def getReverseItems(from: Long, num: Int): (Seq[TransactionItem]) = {
    transactionReverseItems.reverse.slice(from.toInt, from.toInt + num)
  }

  def archiveItems = {
    //TODO(chenxi) we drop redundant transaction records temporarily, & we will persistent those records in next version.
    transactionItems.drop(archiveSize.toInt)
  }
}
