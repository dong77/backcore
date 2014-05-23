/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 */

package com.coinport.coinex.serializers

import org.json4s._
import org.json4s.JsonAST.JField
import org.json4s.native.Serialization._
import com.coinport.coinex.data._
import com.coinport.coinex.data.Implicits._

object PrettyJsonSerializer extends BaseJsonSerializer {
  implicit val formats = ThriftEnumJson4sSerialization.formats + new CustomTypeSerializer
}

object DebugJsonSerializer extends BaseJsonSerializer {
  implicit val formats = ThriftEnumJson4sSerialization.formats + new DebugTypeSerializer
}

trait BaseJsonSerializer {
  implicit val formats: Formats

  def toJValue(obj: Any) = {
    Extraction.decompose(obj)
      .removeField {
        case JField(name, value) =>
          name.startsWith("_") // filter fields starting with underscore
        case _ => false
      }
  }

  def toJson(obj: Any): String = {
    writePretty(toJValue(obj))
  }
}

class CustomTypeSerializer extends DebugTypeSerializer {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] =
    super.serialize orElse {
      case m: RedeliverFilters => JNothing
    }
}

class DebugTypeSerializer extends Serializer[Any] {
  def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case m: Currency => JString(m.toString.toUpperCase)
    case m: MarketSide => JString(m.S)
    case m: scala.collection.Map[_, _] =>
      JObject(m.map({
        case (k, v) =>
          JField(
            k match {
              case ks: String => ks
              case ks: MarketSide => ks.S
              case ks: Any => ks.toString
            },
            Extraction.decompose(v))
      }).toList)
  }

  def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), Map[Any, Any]] = {
    sys.error("Not interested.")
  }
}

object Main extends App {
  override def main(ars: Array[String]) {
    val order = Order(1000L, 100L, 100, Some(RDouble(0.5, true)))
    val orderPools = Map(MarketSide(Currency.Btc, Currency.Ltc) -> List(order))
    val orderMap = Map(100L -> order)
    val priceRestriction = Some(100.0)
    val filters = RedeliverFilters(Map("ff" -> RedeliverFilterData(List(100, 200), 20)))
    val state: TMarketState = TMarketState(orderPools, orderMap, priceRestriction, filters)
    println("$" * 50 + PrettyJsonSerializer.toJson(state))
    println(">" * 50 + DebugJsonSerializer.toJson(state))
  }
}