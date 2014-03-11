/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 * All non persistent messages (non-persistent commands/queries and responses)
 */

package com.coinport.coinex.data

// ------------------------------------------------------------------------------------------------
// Data used inside messages
sealed trait AccountOperationCode 
case object InsuffcientFund extends AccountOperationCode
case object InvalidAmount extends AccountOperationCode

// ------------------------------------------------------------------------------------------------
// Non-persistent message
case object SaveSnapshotNow extends Msg
case class AccountOperationOK extends Msg
case class AccountOperationFailed(error: AccountOperationCode) extends Msg
case class BuyOrderSubmissionOK(market: MarketSide, order: Order, txs: Seq[Transaction]) extends Msg
case class SellOrderSubmissionOK(market: MarketSide, order: Order, txs: Seq[Transaction]) extends Msg