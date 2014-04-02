/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.fee

sealed trait FeeRule {
  def getFee(amount: Long): Long
}

case class PercentageFee(percentage: Double = 0.0) extends FeeRule {
  def getFee(amount: Long) = (amount * percentage).round
}

case class ConstantFee(fee: Long) extends FeeRule {
  def getFee(amount: Long) = fee
}