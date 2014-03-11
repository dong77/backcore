/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 * Persistent commands.
 */

package com.coinport.coinex.data

// Please name all commands starting with "Do"
// AccountProcessor commands
case class DoSubmitOrder(market: MarketSide, order: Order) extends Cmd

case class DoDepositCash(userId: Long, currency: Currency, amount: Long) extends Cmd
case class DoRequestCashWithdrawal(userId: Long, currency: Currency, amount: Long) extends Cmd
case class DoConfirmCashWithdrawalSuccess(userId: Long, currency: Currency, amount: Long) extends Cmd
case class DoConfirmCashWithdrawalFailed(userId: Long, currency: Currency, amount: Long) extends Cmd

// MarketProcessor commands
case class DoCancelOrder(market: MarketSide, id: Long) extends Cmd
