package com.coinport.coinex.serializers

import akka.serialization.Serializer
import com.twitter.bijection.scrooge.BinaryScalaCodec
import com.coinport.coinex.data._

// TODO(d): Auto-generate this file.

class EventSerializer extends Serializer {
  val includeManifest: Boolean = true
  val identifier = 870725

  def toBinary(obj: AnyRef): Array[Byte] = obj match {
    case m: AccountOperationResult => BinaryScalaCodec(AccountOperationResult)(m)
    case m: DoCancelOrder => BinaryScalaCodec(DoCancelOrder)(m)
    case m: DoConfirmCashWithdrawalFailed => BinaryScalaCodec(DoConfirmCashWithdrawalFailed)(m)
    case m: DoConfirmCashWithdrawalSuccess => BinaryScalaCodec(DoConfirmCashWithdrawalSuccess)(m)
    case m: DoDepositCash => BinaryScalaCodec(DoDepositCash)(m)
    case m: DoRequestCashWithdrawal => BinaryScalaCodec(DoRequestCashWithdrawal)(m)
    case m: DoSubmitOrder => BinaryScalaCodec(DoSubmitOrder)(m)
    case m: OrderSubmissionDone => BinaryScalaCodec(OrderSubmissionDone)(m)
    case m: OrderSubmissionInProgross => BinaryScalaCodec(OrderSubmissionInProgross)(m)
    case m: OrderCashLocked => BinaryScalaCodec(OrderCashLocked)(m)
    case m: QueryAccount => BinaryScalaCodec(QueryAccount)(m)
    case m: QueryAccountResult => BinaryScalaCodec(QueryAccountResult)(m)
    case m: QueryMarket => BinaryScalaCodec(QueryMarket)(m)
    case m: QueryMarketResult => BinaryScalaCodec(QueryMarketResult)(m)
    case m: OrderCancelled => BinaryScalaCodec(OrderCancelled)(m)
    case m: OrderSubmitted => BinaryScalaCodec(OrderSubmitted)(m)
    case m: QueryUserOrders => BinaryScalaCodec(QueryUserOrders)(m)
    case m: QueryUserOrdersResult => BinaryScalaCodec(QueryUserOrdersResult)(m)
    case m: QueryMarketCandleData => BinaryScalaCodec(QueryMarketCandleData)(m)
    case m: QueryMarketCandleDataResult => BinaryScalaCodec(QueryMarketCandleDataResult)(m)
    case m => throw new IllegalArgumentException("Cannot serialize object: " + m + ". Talk to wangdong!")
  }

  def fromBinary(bytes: Array[Byte],
    clazz: Option[Class[_]]): AnyRef = clazz match {
    case Some(c) if c == classOf[AccountOperationResult.Immutable] => BinaryScalaCodec(AccountOperationResult).invert(bytes).get
    case Some(c) if c == classOf[DoCancelOrder.Immutable] => BinaryScalaCodec(DoCancelOrder).invert(bytes).get
    case Some(c) if c == classOf[DoConfirmCashWithdrawalFailed.Immutable] => BinaryScalaCodec(DoConfirmCashWithdrawalFailed).invert(bytes).get
    case Some(c) if c == classOf[DoConfirmCashWithdrawalSuccess.Immutable] => BinaryScalaCodec(DoConfirmCashWithdrawalSuccess).invert(bytes).get
    case Some(c) if c == classOf[DoDepositCash.Immutable] => BinaryScalaCodec(DoDepositCash).invert(bytes).get
    case Some(c) if c == classOf[DoRequestCashWithdrawal.Immutable] => BinaryScalaCodec(DoRequestCashWithdrawal).invert(bytes).get
    case Some(c) if c == classOf[DoSubmitOrder.Immutable] => BinaryScalaCodec(DoSubmitOrder).invert(bytes).get
    case Some(c) if c == classOf[OrderSubmissionDone.Immutable] => BinaryScalaCodec(OrderSubmissionDone).invert(bytes).get
    case Some(c) if c == classOf[OrderSubmissionInProgross.Immutable] => BinaryScalaCodec(OrderSubmissionInProgross).invert(bytes).get
    case Some(c) if c == classOf[OrderCashLocked.Immutable] => BinaryScalaCodec(OrderCashLocked).invert(bytes).get
    case Some(c) if c == classOf[QueryAccount.Immutable] => BinaryScalaCodec(QueryAccount).invert(bytes).get
    case Some(c) if c == classOf[QueryAccountResult.Immutable] => BinaryScalaCodec(QueryAccountResult).invert(bytes).get
    case Some(c) if c == classOf[QueryMarket.Immutable] => BinaryScalaCodec(QueryMarket).invert(bytes).get
    case Some(c) if c == classOf[QueryMarketResult.Immutable] => BinaryScalaCodec(QueryMarketResult).invert(bytes).get
    case Some(c) if c == classOf[OrderCancelled.Immutable] => BinaryScalaCodec(OrderCancelled).invert(bytes).get
    case Some(c) if c == classOf[OrderSubmitted.Immutable] => BinaryScalaCodec(OrderSubmitted).invert(bytes).get
    case Some(c) if c == classOf[QueryUserOrders.Immutable] => BinaryScalaCodec(QueryUserOrders).invert(bytes).get
    case Some(c) if c == classOf[QueryUserOrdersResult.Immutable] => BinaryScalaCodec(QueryUserOrdersResult).invert(bytes).get
    case Some(c) if c == classOf[QueryMarketCandleData.Immutable] => BinaryScalaCodec(QueryMarketCandleData).invert(bytes).get
    case Some(c) if c == classOf[QueryMarketCandleDataResult.Immutable] => BinaryScalaCodec(QueryMarketCandleDataResult).invert(bytes).get

    case Some(c) => throw new IllegalArgumentException("Cannot deserialize class: " + c.getCanonicalName + ". Talk to wangdong!")
    case None => throw new IllegalArgumentException("No class found in EventSerializer when deserializing array: " + bytes.mkString(""))
  }
}