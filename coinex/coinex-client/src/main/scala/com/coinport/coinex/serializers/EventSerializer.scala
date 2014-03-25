
/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 * This file was auto generated by auto_gen_serializer.sh on Tue Mar 25 17:58:54 CST 2014
 */

package com.coinport.coinex.serializers

import akka.serialization.Serializer
import com.twitter.bijection.scrooge.BinaryScalaCodec
import com.coinport.coinex.data._

class EventSerializer extends Serializer {
  val includeManifest: Boolean = true
  val identifier = 870725
  val s_0 = BinaryScalaCodec(UserProfile)
  val s_1 = BinaryScalaCodec(MarketSide)
  val s_2 = BinaryScalaCodec(Order)
  val s_3 = BinaryScalaCodec(OrderInfo)
  val s_4 = BinaryScalaCodec(OrderUpdate)
  val s_5 = BinaryScalaCodec(Transaction)
  val s_6 = BinaryScalaCodec(CashAccount)
  val s_7 = BinaryScalaCodec(UserAccount)
  val s_8 = BinaryScalaCodec(PersistentAccountState)
  val s_9 = BinaryScalaCodec(UserLogsState)
  val s_10 = BinaryScalaCodec(MarketDepthItem)
  val s_11 = BinaryScalaCodec(MarketDepth)
  val s_12 = BinaryScalaCodec(CandleDataItem)
  val s_13 = BinaryScalaCodec(CandleData)
  val s_14 = BinaryScalaCodec(MarketByMetrics)
  val s_15 = BinaryScalaCodec(RobotMetrics)
  val s_16 = BinaryScalaCodec(TransactionItem)
  val s_17 = BinaryScalaCodec(TransactionData)
  val s_18 = BinaryScalaCodec(RegisterUserFailed)
  val s_19 = BinaryScalaCodec(RegisterUserSucceeded)
  val s_20 = BinaryScalaCodec(Login)
  val s_21 = BinaryScalaCodec(LoginFailed)
  val s_22 = BinaryScalaCodec(LoginSucceeded)
  val s_23 = BinaryScalaCodec(RequestPasswordResetFailed)
  val s_24 = BinaryScalaCodec(RequestPasswordResetSucceeded)
  val s_25 = BinaryScalaCodec(ValidatePasswordResetToken)
  val s_26 = BinaryScalaCodec(ValidatePasswordResetTokenResult)
  val s_27 = BinaryScalaCodec(ResetPasswordFailed)
  val s_28 = BinaryScalaCodec(ResetPasswordSucceeded)
  val s_29 = BinaryScalaCodec(AccountOperationResult)
  val s_30 = BinaryScalaCodec(OrderSubmissionDone)
  val s_31 = BinaryScalaCodec(QueryUserOrders)
  val s_32 = BinaryScalaCodec(QueryUserOrdersResult)
  val s_33 = BinaryScalaCodec(QueryAccount)
  val s_34 = BinaryScalaCodec(QueryAccountResult)
  val s_35 = BinaryScalaCodec(QueryMarket)
  val s_36 = BinaryScalaCodec(QueryMarketResult)
  val s_37 = BinaryScalaCodec(QueryMarketUnsupportedMarketFailure)
  val s_38 = BinaryScalaCodec(QueryCandleData)
  val s_39 = BinaryScalaCodec(QueryCandleDataResult)
  val s_40 = BinaryScalaCodec(QueryTransactionData)
  val s_41 = BinaryScalaCodec(QueryTransactionDataResult)
  val s_42 = BinaryScalaCodec(OrderSubmissionInProgross)
  val s_43 = BinaryScalaCodec(SendMailRequest)
  val s_44 = BinaryScalaCodec(DoRegisterUser)
  val s_45 = BinaryScalaCodec(DoRequestPasswordReset)
  val s_46 = BinaryScalaCodec(DoResetPassword)
  val s_47 = BinaryScalaCodec(DoSubmitOrder)
  val s_48 = BinaryScalaCodec(DoDepositCash)
  val s_49 = BinaryScalaCodec(DoRequestCashWithdrawal)
  val s_50 = BinaryScalaCodec(DoConfirmCashWithdrawalSuccess)
  val s_51 = BinaryScalaCodec(DoConfirmCashWithdrawalFailed)
  val s_52 = BinaryScalaCodec(DoCancelOrder)
  val s_53 = BinaryScalaCodec(OrderCashLocked)
  val s_54 = BinaryScalaCodec(OrderCancelled)
  val s_55 = BinaryScalaCodec(OrderSubmissionFailed)
  val s_56 = BinaryScalaCodec(OrderSubmitted)

  def toBinary(obj: AnyRef): Array[Byte] = obj match {
    case m: UserProfile => s_0(m)
    case m: MarketSide => s_1(m)
    case m: Order => s_2(m)
    case m: OrderInfo => s_3(m)
    case m: OrderUpdate => s_4(m)
    case m: Transaction => s_5(m)
    case m: CashAccount => s_6(m)
    case m: UserAccount => s_7(m)
    case m: PersistentAccountState => s_8(m)
    case m: UserLogsState => s_9(m)
    case m: MarketDepthItem => s_10(m)
    case m: MarketDepth => s_11(m)
    case m: CandleDataItem => s_12(m)
    case m: CandleData => s_13(m)
    case m: MarketByMetrics => s_14(m)
    case m: RobotMetrics => s_15(m)
    case m: TransactionItem => s_16(m)
    case m: TransactionData => s_17(m)
    case m: RegisterUserFailed => s_18(m)
    case m: RegisterUserSucceeded => s_19(m)
    case m: Login => s_20(m)
    case m: LoginFailed => s_21(m)
    case m: LoginSucceeded => s_22(m)
    case m: RequestPasswordResetFailed => s_23(m)
    case m: RequestPasswordResetSucceeded => s_24(m)
    case m: ValidatePasswordResetToken => s_25(m)
    case m: ValidatePasswordResetTokenResult => s_26(m)
    case m: ResetPasswordFailed => s_27(m)
    case m: ResetPasswordSucceeded => s_28(m)
    case m: AccountOperationResult => s_29(m)
    case m: OrderSubmissionDone => s_30(m)
    case m: QueryUserOrders => s_31(m)
    case m: QueryUserOrdersResult => s_32(m)
    case m: QueryAccount => s_33(m)
    case m: QueryAccountResult => s_34(m)
    case m: QueryMarket => s_35(m)
    case m: QueryMarketResult => s_36(m)
    case m: QueryMarketUnsupportedMarketFailure => s_37(m)
    case m: QueryCandleData => s_38(m)
    case m: QueryCandleDataResult => s_39(m)
    case m: QueryTransactionData => s_40(m)
    case m: QueryTransactionDataResult => s_41(m)
    case m: OrderSubmissionInProgross => s_42(m)
    case m: SendMailRequest => s_43(m)
    case m: DoRegisterUser => s_44(m)
    case m: DoRequestPasswordReset => s_45(m)
    case m: DoResetPassword => s_46(m)
    case m: DoSubmitOrder => s_47(m)
    case m: DoDepositCash => s_48(m)
    case m: DoRequestCashWithdrawal => s_49(m)
    case m: DoConfirmCashWithdrawalSuccess => s_50(m)
    case m: DoConfirmCashWithdrawalFailed => s_51(m)
    case m: DoCancelOrder => s_52(m)
    case m: OrderCashLocked => s_53(m)
    case m: OrderCancelled => s_54(m)
    case m: OrderSubmissionFailed => s_55(m)
    case m: OrderSubmitted => s_56(m)

    case m => throw new IllegalArgumentException("Cannot serialize object: " + m)
  }

  def fromBinary(bytes: Array[Byte],
    clazz: Option[Class[_]]): AnyRef = clazz match {
    case Some(c) if c == classOf[UserProfile.Immutable] => s_0.invert(bytes).get
    case Some(c) if c == classOf[MarketSide.Immutable] => s_1.invert(bytes).get
    case Some(c) if c == classOf[Order.Immutable] => s_2.invert(bytes).get
    case Some(c) if c == classOf[OrderInfo.Immutable] => s_3.invert(bytes).get
    case Some(c) if c == classOf[OrderUpdate.Immutable] => s_4.invert(bytes).get
    case Some(c) if c == classOf[Transaction.Immutable] => s_5.invert(bytes).get
    case Some(c) if c == classOf[CashAccount.Immutable] => s_6.invert(bytes).get
    case Some(c) if c == classOf[UserAccount.Immutable] => s_7.invert(bytes).get
    case Some(c) if c == classOf[PersistentAccountState.Immutable] => s_8.invert(bytes).get
    case Some(c) if c == classOf[UserLogsState.Immutable] => s_9.invert(bytes).get
    case Some(c) if c == classOf[MarketDepthItem.Immutable] => s_10.invert(bytes).get
    case Some(c) if c == classOf[MarketDepth.Immutable] => s_11.invert(bytes).get
    case Some(c) if c == classOf[CandleDataItem.Immutable] => s_12.invert(bytes).get
    case Some(c) if c == classOf[CandleData.Immutable] => s_13.invert(bytes).get
    case Some(c) if c == classOf[MarketByMetrics.Immutable] => s_14.invert(bytes).get
    case Some(c) if c == classOf[RobotMetrics.Immutable] => s_15.invert(bytes).get
    case Some(c) if c == classOf[TransactionItem.Immutable] => s_16.invert(bytes).get
    case Some(c) if c == classOf[TransactionData.Immutable] => s_17.invert(bytes).get
    case Some(c) if c == classOf[RegisterUserFailed.Immutable] => s_18.invert(bytes).get
    case Some(c) if c == classOf[RegisterUserSucceeded.Immutable] => s_19.invert(bytes).get
    case Some(c) if c == classOf[Login.Immutable] => s_20.invert(bytes).get
    case Some(c) if c == classOf[LoginFailed.Immutable] => s_21.invert(bytes).get
    case Some(c) if c == classOf[LoginSucceeded.Immutable] => s_22.invert(bytes).get
    case Some(c) if c == classOf[RequestPasswordResetFailed.Immutable] => s_23.invert(bytes).get
    case Some(c) if c == classOf[RequestPasswordResetSucceeded.Immutable] => s_24.invert(bytes).get
    case Some(c) if c == classOf[ValidatePasswordResetToken.Immutable] => s_25.invert(bytes).get
    case Some(c) if c == classOf[ValidatePasswordResetTokenResult.Immutable] => s_26.invert(bytes).get
    case Some(c) if c == classOf[ResetPasswordFailed.Immutable] => s_27.invert(bytes).get
    case Some(c) if c == classOf[ResetPasswordSucceeded.Immutable] => s_28.invert(bytes).get
    case Some(c) if c == classOf[AccountOperationResult.Immutable] => s_29.invert(bytes).get
    case Some(c) if c == classOf[OrderSubmissionDone.Immutable] => s_30.invert(bytes).get
    case Some(c) if c == classOf[QueryUserOrders.Immutable] => s_31.invert(bytes).get
    case Some(c) if c == classOf[QueryUserOrdersResult.Immutable] => s_32.invert(bytes).get
    case Some(c) if c == classOf[QueryAccount.Immutable] => s_33.invert(bytes).get
    case Some(c) if c == classOf[QueryAccountResult.Immutable] => s_34.invert(bytes).get
    case Some(c) if c == classOf[QueryMarket.Immutable] => s_35.invert(bytes).get
    case Some(c) if c == classOf[QueryMarketResult.Immutable] => s_36.invert(bytes).get
    case Some(c) if c == classOf[QueryMarketUnsupportedMarketFailure.Immutable] => s_37.invert(bytes).get
    case Some(c) if c == classOf[QueryCandleData.Immutable] => s_38.invert(bytes).get
    case Some(c) if c == classOf[QueryCandleDataResult.Immutable] => s_39.invert(bytes).get
    case Some(c) if c == classOf[QueryTransactionData.Immutable] => s_40.invert(bytes).get
    case Some(c) if c == classOf[QueryTransactionDataResult.Immutable] => s_41.invert(bytes).get
    case Some(c) if c == classOf[OrderSubmissionInProgross.Immutable] => s_42.invert(bytes).get
    case Some(c) if c == classOf[SendMailRequest.Immutable] => s_43.invert(bytes).get
    case Some(c) if c == classOf[DoRegisterUser.Immutable] => s_44.invert(bytes).get
    case Some(c) if c == classOf[DoRequestPasswordReset.Immutable] => s_45.invert(bytes).get
    case Some(c) if c == classOf[DoResetPassword.Immutable] => s_46.invert(bytes).get
    case Some(c) if c == classOf[DoSubmitOrder.Immutable] => s_47.invert(bytes).get
    case Some(c) if c == classOf[DoDepositCash.Immutable] => s_48.invert(bytes).get
    case Some(c) if c == classOf[DoRequestCashWithdrawal.Immutable] => s_49.invert(bytes).get
    case Some(c) if c == classOf[DoConfirmCashWithdrawalSuccess.Immutable] => s_50.invert(bytes).get
    case Some(c) if c == classOf[DoConfirmCashWithdrawalFailed.Immutable] => s_51.invert(bytes).get
    case Some(c) if c == classOf[DoCancelOrder.Immutable] => s_52.invert(bytes).get
    case Some(c) if c == classOf[OrderCashLocked.Immutable] => s_53.invert(bytes).get
    case Some(c) if c == classOf[OrderCancelled.Immutable] => s_54.invert(bytes).get
    case Some(c) if c == classOf[OrderSubmissionFailed.Immutable] => s_55.invert(bytes).get
    case Some(c) if c == classOf[OrderSubmitted.Immutable] => s_56.invert(bytes).get

    case Some(c) => throw new IllegalArgumentException("Cannot deserialize class: " + c.getCanonicalName)
    case None => throw new IllegalArgumentException("No class found in EventSerializer when deserializing array: " + bytes.mkString(""))
  }
}
