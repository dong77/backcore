
/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 * This file was auto generated by auto_gen_serializer.sh on Thu Apr 10 15:00:25 CST 2014
 */

package com.coinport.coinex.serializers

import org.json4s.CustomSerializer
import org.json4s._
import com.coinport.coinex.data._
import org.json4s.native.Serialization

object ThriftEnumJson4sSerialization {

  class ChartTimeDimensionSerializer extends CustomSerializer[ChartTimeDimension](format => (
    { case JInt(s) => ChartTimeDimension(s.intValue) }, {
      case x: ChartTimeDimension => JInt(x.value)
    }))

  class CurrencySerializer extends CustomSerializer[Currency](format => (
    { case JInt(s) => Currency(s.intValue) }, {
      case x: Currency => JInt(x.value)
    }))

  class DirectionSerializer extends CustomSerializer[Direction](format => (
    { case JInt(s) => Direction(s.intValue) }, {
      case x: Direction => JInt(x.value)
    }))

  class EmailTypeSerializer extends CustomSerializer[EmailType](format => (
    { case JInt(s) => EmailType(s.intValue) }, {
      case x: EmailType => JInt(x.value)
    }))

  class ErrorCodeSerializer extends CustomSerializer[ErrorCode](format => (
    { case JInt(s) => ErrorCode(s.intValue) }, {
      case x: ErrorCode => JInt(x.value)
    }))

  class OrderStatusSerializer extends CustomSerializer[OrderStatus](format => (
    { case JInt(s) => OrderStatus(s.intValue) }, {
      case x: OrderStatus => JInt(x.value)
    }))

  class TransferStatusSerializer extends CustomSerializer[TransferStatus](format => (
    { case JInt(s) => TransferStatus(s.intValue) }, {
      case x: TransferStatus => JInt(x.value)
    }))

  class UserStatusSerializer extends CustomSerializer[UserStatus](format => (
    { case JInt(s) => UserStatus(s.intValue) }, {
      case x: UserStatus => JInt(x.value)
    }))

  implicit val formats = Serialization.formats(NoTypeHints) +
    new ChartTimeDimensionSerializer +
    new CurrencySerializer +
    new DirectionSerializer +
    new EmailTypeSerializer +
    new ErrorCodeSerializer +
    new OrderStatusSerializer +
    new TransferStatusSerializer +
    new UserStatusSerializer
}
