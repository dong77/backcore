/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.fee

import com.coinport.coinex.data.Fee
import com.coinport.coinex.data._
import com.coinport.coinex.common.Constants
import Implicits._

object FeeCounter {
  val goocMarket = MarketSide(Currency.Gooc, Currency.Cny)
  val goocReverseMarket = MarketSide(Currency.Cny, Currency.Gooc)
}

final class FeeCounter(feeConfig: FeeConfig) {
  import FeeCounter._

  def count(event: Any): Seq[Fee] = if (countFee.isDefinedAt(event)) countFee(event) else Nil

  val countFee: PartialFunction[Any, Seq[Fee]] = {
    case tx: Transaction =>
      val (takerInAmount, makerInAmount) = (tx.makerUpdate.outAmount, tx.takerUpdate.outAmount)
      var (takerFee, makerFee) = (0L, 0L)

      if (tx.takerUpdate.userId > feeConfig.freeOfTxChargeUserIdThreshold) {
        feeConfig.marketFeeRules.get(tx.side) foreach { rule =>
          takerFee += rule.getFee(takerInAmount)
        }

        for {
          robotType <- tx.takerUpdate.current.robotType
          rule <- feeConfig.robotFeeRules.get(robotType)
        } { takerFee += rule.getFee(takerInAmount) }
      }

      if (tx.makerUpdate.userId > feeConfig.freeOfTxChargeUserIdThreshold) {
        feeConfig.marketFeeRules.get(tx.side) foreach { rule =>
          makerFee += rule.getFee(makerInAmount)
        }

        for {
          robotType <- tx.makerUpdate.current.robotType
          rule <- feeConfig.robotFeeRules.get(robotType)
        } { makerFee += rule.getFee(makerInAmount) }
      }

      val payee = if (tx.side == goocMarket || tx.side == goocReverseMarket)
        Some(Constants.GOOC_TEAM_ID)
      else
        None

      val result = Seq(Fee(tx.makerUpdate.current.userId, payee, tx.side.outCurrency, makerFee), Fee(tx.takerUpdate.current.userId, payee, tx.side.inCurrency, takerFee))

      result.filter(_.amount > 0)

    case t: AccountTransfer if t.`type` == TransferType.Withdrawal =>
      feeConfig.transferFeeRules.get(t.currency) match {
        case Some(rule) =>
          if (t.userId == Constants.GOOC_TEAM_ID) {
            Nil
          } else {
            Seq(Fee(t.userId, None, t.currency, rule.getFee(t.amount)))
          }
        case None =>
          Nil
      }
  }
}
