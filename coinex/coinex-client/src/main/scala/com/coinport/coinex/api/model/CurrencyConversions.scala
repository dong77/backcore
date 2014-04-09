/**
 * Copyright (C) 2014 Coinport Inc.
 */

package com.coinport.coinex.api.model

object CurrencyUnits extends Enumeration {
  type CurrencyUnit = Value
  val NO_UNIT, BTC, MBTC, CNY, CNY2 = Value

  val factors: Map[(CurrencyUnit, CurrencyUnit), Double] = Map(
    (BTC, MBTC) -> 1000.0,
    (MBTC, BTC) -> 0.001,
    (CNY, CNY2) -> 100.0,
    (CNY2, CNY) -> 0.01
  ) ++ CurrencyUnits.values.map(u => (u, u) -> 1.0).toMap

  // get user-friendly unit
  def userUnit(unit: CurrencyUnit): CurrencyUnit = {
    unit match {
      case CNY2 => CNY
      case MBTC => BTC
      case _ => unit
    }
  }

  // get inner unit
  def innerUnit(unit: CurrencyUnit): CurrencyUnit = {
    unit match {
      case CNY => CNY2
      case BTC => MBTC
      case _ => unit
    }
  }
}

import CurrencyUnits._

case class CurrencyValue(value: Double, unit: CurrencyUnit = NO_UNIT) {
  def unit(unit: CurrencyUnit): CurrencyValue = {
    copy(unit = unit)
  }

  def /(operand: CurrencyValue): PriceValue = {
    val units = (unit, operand.unit)
    if (units._1 == units._2) { PriceValue(value / operand.value) }
    else {
      factors.get(units) match {
        case Some(factor) => // same family of currency
          PriceValue(value * factor / operand.value)
        case None => // price
          PriceValue(value / operand.value, units)
      }
    }
  }

  def *(operand: PriceValue): CurrencyValue = {
    if (operand.unit == (NO_UNIT, NO_UNIT)) {
      CurrencyValue(value * operand.value, unit)
    } else {
      factors.get(this.unit, operand.unit._2) match {
        case Some(factor) => CurrencyValue(value * operand.value * factor, operand.unit._1)
        case None => this // can't multiply
      }
    }
  }

  def to(newUnit: CurrencyUnit): CurrencyValue = {
    //    println("convert from " + currencyUnit + " to " + newUnit)
    if (unit == newUnit) { this }
    else {
      factors.get((unit, newUnit)) match {
        case Some(factor) => CurrencyValue(value * factor, newUnit)
        case None => this // can't convert between different currencies
      }
    }
  }

  def userValue: Double = {
    to(userUnit(unit)).value
  }

  def innerValue: Double = {
    to(innerUnit(unit)).value.toLong
  }

  def toLong = value.toLong
  def toDouble = value

  override def toString: String = {
    "[" + value + " " + unit + "]"
  }

  override def equals(obj: scala.Any): Boolean = {
    if (obj.isInstanceOf[CurrencyValue]) {
      val other = obj.asInstanceOf[CurrencyValue]
      val me = this to other.unit
      me.value == other.value && me.unit == other.unit
    } else {
      false
    }
  }
}

case class PriceValue(value: Double, unit: (CurrencyUnit, CurrencyUnit) = (NO_UNIT, NO_UNIT)) {

  def inverse = PriceValue(1.0 / value, (unit._2, unit._1))

  def unit(unit: (CurrencyUnit, CurrencyUnit)): PriceValue = {
    PriceValue(value, unit)
  }

  def *(operand: CurrencyValue): CurrencyValue = {
    if (unit == (NO_UNIT, NO_UNIT)) {
      operand
    } else {
      factors.get(operand.unit, this.unit._2) match {
        case Some(factor) => CurrencyValue(value * operand.value * factor, this.unit._1)
        case None => operand // can't multiply
      }
    }
  }

  def to(newUnit: (CurrencyUnit, CurrencyUnit)): PriceValue = {
    if (newUnit.swap == unit)
      inverse
    else {
      factors.get(unit._1, newUnit._1) match {
        case Some(factor1) =>
          factors.get(unit._2, newUnit._2) match {
            case Some(factor2) =>
              PriceValue(value * factor1 / factor2, newUnit)
            case None => this
          }
        case None => this
      }
    }
  }

  def userValue: Double = {
    val newUnit: (CurrencyUnit, CurrencyUnit) = (userUnit(unit._1), userUnit(unit._2))
    to(newUnit).value
  }

  def innerValue: Double = {
    val newUnit: (CurrencyUnit, CurrencyUnit) = (innerUnit(unit._1), innerUnit(unit._2))
    to(newUnit).value
  }

  override def equals(obj: scala.Any): Boolean = {
    if (obj.isInstanceOf[PriceValue]) {
      val other = obj.asInstanceOf[PriceValue]
      val result = this.value == other.value && this.unit == other.unit
      result
    } else {
      false
    }
  }

  override def toString: String = {
    "[" + value + " " + unit._1 + "/" + unit._2 + "]"
  }
}