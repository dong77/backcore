package com.coinport.coinex.serializers

import akka.serialization.Serializer
import com.twitter.bijection.scrooge.BinaryScalaCodec
import com.coinport.coinex.data._

// TODO(d): Auto-generate this file.

class EventSerializer extends Serializer {
  val includeManifest: Boolean = true
  val identifier = 870725

  def toBinary(obj: AnyRef): Array[Byte] = obj match {
    case m: AccountOperationFailed => BinaryScalaCodec(AccountOperationFailed)(m)
    case m: AccountOperationOk => BinaryScalaCodec(AccountOperationOk)(m)
    case m: CashAccount => BinaryScalaCodec(CashAccount)(m)
    case m: DebugDump => BinaryScalaCodec(DebugDump)(m)
    case m: DoCancelOrder => BinaryScalaCodec(DoCancelOrder)(m)
    case m: DoConfirmCashWithdrawalFailed => BinaryScalaCodec(DoConfirmCashWithdrawalFailed)(m)
    case m: DoConfirmCashWithdrawalSuccess => BinaryScalaCodec(DoConfirmCashWithdrawalSuccess)(m)
    case m: DoDepositCash => BinaryScalaCodec(DoDepositCash)(m)
    case m: DoRequestCashWithdrawal => BinaryScalaCodec(DoRequestCashWithdrawal)(m)
    case m: DoSubmitOrder => BinaryScalaCodec(DoSubmitOrder)(m)
    case m: MarketSide => BinaryScalaCodec(MarketSide)(m)
    case m: NewTxPriceSeen => BinaryScalaCodec(NewTxPriceSeen)(m)
    case m: Order => BinaryScalaCodec(Order)(m)
    case m: OrderCancelled => BinaryScalaCodec(OrderCancelled)(m)
    case m: OrderSubmissionOk => BinaryScalaCodec(OrderSubmissionOk)(m)
    case m: OrderSubmitted => BinaryScalaCodec(OrderSubmitted)(m)
    case m: Price => BinaryScalaCodec(Price)(m)
    case m: QueryAccount => BinaryScalaCodec(QueryAccount)(m)
    case m: QueryAccountResult => BinaryScalaCodec(QueryAccountResult)(m)
    case m: QueryMarket => BinaryScalaCodec(QueryMarket)(m)
    case m: QueryMarketResult => BinaryScalaCodec(QueryMarketResult)(m)
    case m: TakeSnapshotNow => BinaryScalaCodec(TakeSnapshotNow)(m)
    case m: Transaction => BinaryScalaCodec(Transaction)(m)
    case m: TransactionsCreated => BinaryScalaCodec(TransactionsCreated)(m)
    case m: Transfer => BinaryScalaCodec(Transfer)(m)
    case m: User => BinaryScalaCodec(User)(m)
    case m: UserAccount => BinaryScalaCodec(UserAccount)(m)
    case m => throw new IllegalArgumentException("Cannot serialize object: " + m + ". Talk to wangdong!")
  }

  def fromBinary(bytes: Array[Byte],
    clazz: Option[Class[_]]): AnyRef = clazz match {
    case Some(c) if c == classOf[AccountOperationFailed.Immutable] => BinaryScalaCodec(AccountOperationFailed).invert(bytes).get
    case Some(c) if c == classOf[AccountOperationOk.Immutable] => BinaryScalaCodec(AccountOperationOk).invert(bytes).get
    case Some(c) if c == classOf[CashAccount.Immutable] => BinaryScalaCodec(CashAccount).invert(bytes).get
    case Some(c) if c == classOf[DebugDump.Immutable] => BinaryScalaCodec(DebugDump).invert(bytes).get
    case Some(c) if c == classOf[DoCancelOrder.Immutable] => BinaryScalaCodec(DoCancelOrder).invert(bytes).get
    case Some(c) if c == classOf[DoConfirmCashWithdrawalFailed.Immutable] => BinaryScalaCodec(DoConfirmCashWithdrawalFailed).invert(bytes).get
    case Some(c) if c == classOf[DoConfirmCashWithdrawalSuccess.Immutable] => BinaryScalaCodec(DoConfirmCashWithdrawalSuccess).invert(bytes).get
    case Some(c) if c == classOf[DoDepositCash.Immutable] => BinaryScalaCodec(DoDepositCash).invert(bytes).get
    case Some(c) if c == classOf[DoRequestCashWithdrawal.Immutable] => BinaryScalaCodec(DoRequestCashWithdrawal).invert(bytes).get
    case Some(c) if c == classOf[DoSubmitOrder.Immutable] => BinaryScalaCodec(DoSubmitOrder).invert(bytes).get
    case Some(c) if c == classOf[MarketSide.Immutable] => BinaryScalaCodec(MarketSide).invert(bytes).get
    case Some(c) if c == classOf[NewTxPriceSeen.Immutable] => BinaryScalaCodec(NewTxPriceSeen).invert(bytes).get
    case Some(c) if c == classOf[Order.Immutable] => BinaryScalaCodec(Order).invert(bytes).get
    case Some(c) if c == classOf[OrderCancelled.Immutable] => BinaryScalaCodec(OrderCancelled).invert(bytes).get
    case Some(c) if c == classOf[OrderSubmissionOk.Immutable] => BinaryScalaCodec(OrderSubmissionOk).invert(bytes).get
    case Some(c) if c == classOf[OrderSubmitted.Immutable] => BinaryScalaCodec(OrderSubmitted).invert(bytes).get
    case Some(c) if c == classOf[Price.Immutable] => BinaryScalaCodec(Price).invert(bytes).get
    case Some(c) if c == classOf[QueryAccount.Immutable] => BinaryScalaCodec(QueryAccount).invert(bytes).get
    case Some(c) if c == classOf[QueryAccountResult.Immutable] => BinaryScalaCodec(QueryAccountResult).invert(bytes).get
    case Some(c) if c == classOf[QueryMarket.Immutable] => BinaryScalaCodec(QueryMarket).invert(bytes).get
    case Some(c) if c == classOf[QueryMarketResult.Immutable] => BinaryScalaCodec(QueryMarketResult).invert(bytes).get
    case Some(c) if c == classOf[TakeSnapshotNow.Immutable] => BinaryScalaCodec(TakeSnapshotNow).invert(bytes).get
    case Some(c) if c == classOf[Transaction.Immutable] => BinaryScalaCodec(Transaction).invert(bytes).get
    case Some(c) if c == classOf[TransactionsCreated.Immutable] => BinaryScalaCodec(TransactionsCreated).invert(bytes).get
    case Some(c) if c == classOf[Transfer.Immutable] => BinaryScalaCodec(Transfer).invert(bytes).get
    case Some(c) if c == classOf[User.Immutable] => BinaryScalaCodec(User).invert(bytes).get
    case Some(c) if c == classOf[UserAccount.Immutable] => BinaryScalaCodec(UserAccount).invert(bytes).get
    case Some(c) => throw new IllegalArgumentException("Cannot deserialize class: " + c.getCanonicalName + ". Talk to wangdong!")
    case None => throw new IllegalArgumentException("No class found in EventSerializer when deserializing array: " + bytes.mkString(""))
  }
}