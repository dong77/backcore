/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 * TODO(c) refactoring this file
 */

package com.coinport.coinex.fee

import com.coinport.coinex.data._
import com.coinport.coinex.fee.rules._
import FeeRuleTypeEnum._

// TODO(c): consider the type of the user
class TransactionFeeMaker(r: FeeRules) extends FeeMaker {
  val rules: TransactionFeeRules = r.asInstanceOf[TransactionFeeRules]

  override def count[Transaction](serviceTakeItem: Transaction): List[Fee] = {
    serviceTakeItem match {
      case Transaction(_, _, side, takerUpdate, makerUpdate) =>
        val takerInAmount = makerUpdate.previous.quantity - makerUpdate.current.quantity
        val makerInAmount = takerUpdate.previous.quantity - takerUpdate.current.quantity
        val takerRobotType = takerUpdate.current.robotType.getOrElse(-1)
        val makerRobotType = makerUpdate.current.robotType.getOrElse(-1)
        var takerAmount = 0L
        var makerAmount = 0L
        rules.marketItems.getOrElse(side, null) match {
          case null => None
          case item => item match {
            case FeeRuleItem(PERCENTAGE, percentage, _) =>
              takerAmount += (takerInAmount * percentage).toLong
              makerAmount += (makerInAmount * percentage).toLong
            case FeeRuleItem(CONST_PAY, _, tip) =>
              takerAmount += tip
              makerAmount += tip
          }
        }
        rules.robotItems.getOrElse(takerRobotType, null) match {
          case null => None
          case item => item match {
            case FeeRuleItem(PERCENTAGE, percentage, _) =>
              takerAmount += (takerInAmount * percentage).toLong
            case FeeRuleItem(CONST_PAY, _, tip) =>
              takerAmount += tip
          }
        }
        rules.robotItems.getOrElse(makerRobotType, null) match {
          case null => None
          case item => item match {
            case FeeRuleItem(PERCENTAGE, percentage, _) =>
              makerAmount += (makerInAmount * percentage).toLong
            case FeeRuleItem(CONST_PAY, _, tip) =>
              makerAmount += tip
          }
        }
        var result = List.empty[Fee]
        if (takerAmount != 0) {
          result ::= Fee(takerUpdate.current.userId, None, side.inCurrency, takerAmount)
        }
        if (makerAmount != 0) {
          result ::= Fee(makerUpdate.current.userId, None, side.outCurrency, makerAmount)
        }
        result
      case _ => Nil
    }
  }
}
