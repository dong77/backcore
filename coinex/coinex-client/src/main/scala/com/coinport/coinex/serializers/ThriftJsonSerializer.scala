
/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 * This file was auto generated by auto_gen_serializer.sh on Thu Apr 10 15:00:25 CST 2014
 */

package com.coinport.coinex.serializers

import akka.serialization.Serializer
import com.twitter.bijection.scrooge.BinaryScalaCodec
import com.coinport.coinex.data._

class ThriftJsonSerializer extends Serializer {
  val includeManifest: Boolean = true
  val identifier = 607100416
  lazy val _cApiSecret = JsonScalaCodec(ApiSecret)
  lazy val _cCandleData = JsonScalaCodec(CandleData)
  lazy val _cCandleDataItem = JsonScalaCodec(CandleDataItem)
  lazy val _cCashAccount = JsonScalaCodec(CashAccount)
  lazy val _cCursor = JsonScalaCodec(Cursor)
  lazy val _cDeposit = JsonScalaCodec(Deposit)
  lazy val _cFee = JsonScalaCodec(Fee)
  lazy val _cMarketDepth = JsonScalaCodec(MarketDepth)
  lazy val _cMarketDepthItem = JsonScalaCodec(MarketDepthItem)
  lazy val _cMarketSide = JsonScalaCodec(MarketSide)
  lazy val _cMetrics = JsonScalaCodec(Metrics)
  lazy val _cMetricsByMarket = JsonScalaCodec(MetricsByMarket)
  lazy val _cOrder = JsonScalaCodec(Order)
  lazy val _cOrderInfo = JsonScalaCodec(OrderInfo)
  lazy val _cOrderUpdate = JsonScalaCodec(OrderUpdate)
  lazy val _cRedeliverFilterData = JsonScalaCodec(RedeliverFilterData)
  lazy val _cSpanCursor = JsonScalaCodec(SpanCursor)
  lazy val _cTransaction = JsonScalaCodec(Transaction)
  lazy val _cTransactionItem = JsonScalaCodec(TransactionItem)
  lazy val _cUserAccount = JsonScalaCodec(UserAccount)
  lazy val _cUserLogsState = JsonScalaCodec(UserLogsState)
  lazy val _cUserProfile = JsonScalaCodec(UserProfile)
  lazy val _cWithdrawal = JsonScalaCodec(Withdrawal)
  lazy val _cAdminCommandResult = JsonScalaCodec(AdminCommandResult)
  lazy val _cAdminConfirmCashDepositFailure = JsonScalaCodec(AdminConfirmCashDepositFailure)
  lazy val _cAdminConfirmCashDepositSuccess = JsonScalaCodec(AdminConfirmCashDepositSuccess)
  lazy val _cAdminConfirmCashWithdrawalFailure = JsonScalaCodec(AdminConfirmCashWithdrawalFailure)
  lazy val _cAdminConfirmCashWithdrawalSuccess = JsonScalaCodec(AdminConfirmCashWithdrawalSuccess)
  lazy val _cApiSecretOperationResult = JsonScalaCodec(ApiSecretOperationResult)
  lazy val _cCancelOrderFailed = JsonScalaCodec(CancelOrderFailed)
  lazy val _cDoAddNewApiSecret = JsonScalaCodec(DoAddNewApiSecret)
  lazy val _cDoCancelOrder = JsonScalaCodec(DoCancelOrder)
  lazy val _cDoDeleteApiSecret = JsonScalaCodec(DoDeleteApiSecret)
  lazy val _cDoRegisterUser = JsonScalaCodec(DoRegisterUser)
  lazy val _cDoRequestCashDeposit = JsonScalaCodec(DoRequestCashDeposit)
  lazy val _cDoRequestCashWithdrawal = JsonScalaCodec(DoRequestCashWithdrawal)
  lazy val _cDoRequestPasswordReset = JsonScalaCodec(DoRequestPasswordReset)
  lazy val _cDoResetPassword = JsonScalaCodec(DoResetPassword)
  lazy val _cDoSendEmail = JsonScalaCodec(DoSendEmail)
  lazy val _cDoSubmitOrder = JsonScalaCodec(DoSubmitOrder)
  lazy val _cDoUpdateMetrics = JsonScalaCodec(DoUpdateMetrics)
  lazy val _cDoUpdateUserProfile = JsonScalaCodec(DoUpdateUserProfile)
  lazy val _cGoogleAuthCodeVerificationResult = JsonScalaCodec(GoogleAuthCodeVerificationResult)
  lazy val _cLogin = JsonScalaCodec(Login)
  lazy val _cLoginFailed = JsonScalaCodec(LoginFailed)
  lazy val _cLoginSucceeded = JsonScalaCodec(LoginSucceeded)
  lazy val _cMessageNotSupported = JsonScalaCodec(MessageNotSupported)
  lazy val _cOrderCancelled = JsonScalaCodec(OrderCancelled)
  lazy val _cOrderFundFrozen = JsonScalaCodec(OrderFundFrozen)
  lazy val _cOrderSubmitted = JsonScalaCodec(OrderSubmitted)
  lazy val _cPasswordResetTokenValidationResult = JsonScalaCodec(PasswordResetTokenValidationResult)
  lazy val _cQueryAccount = JsonScalaCodec(QueryAccount)
  lazy val _cQueryAccountResult = JsonScalaCodec(QueryAccountResult)
  lazy val _cQueryApiSecrets = JsonScalaCodec(QueryApiSecrets)
  lazy val _cQueryApiSecretsResult = JsonScalaCodec(QueryApiSecretsResult)
  lazy val _cQueryCandleData = JsonScalaCodec(QueryCandleData)
  lazy val _cQueryCandleDataResult = JsonScalaCodec(QueryCandleDataResult)
  lazy val _cQueryDeposit = JsonScalaCodec(QueryDeposit)
  lazy val _cQueryDepositResult = JsonScalaCodec(QueryDepositResult)
  lazy val _cQueryMarketDepth = JsonScalaCodec(QueryMarketDepth)
  lazy val _cQueryMarketDepthResult = JsonScalaCodec(QueryMarketDepthResult)
  lazy val _cQueryOrder = JsonScalaCodec(QueryOrder)
  lazy val _cQueryOrderResult = JsonScalaCodec(QueryOrderResult)
  lazy val _cQueryTransaction = JsonScalaCodec(QueryTransaction)
  lazy val _cQueryTransactionResult = JsonScalaCodec(QueryTransactionResult)
  lazy val _cQueryWithdrawal = JsonScalaCodec(QueryWithdrawal)
  lazy val _cQueryWithdrawalResult = JsonScalaCodec(QueryWithdrawalResult)
  lazy val _cRegisterUserFailed = JsonScalaCodec(RegisterUserFailed)
  lazy val _cRegisterUserSucceeded = JsonScalaCodec(RegisterUserSucceeded)
  lazy val _cRequestCashDepositFailed = JsonScalaCodec(RequestCashDepositFailed)
  lazy val _cRequestCashDepositSucceeded = JsonScalaCodec(RequestCashDepositSucceeded)
  lazy val _cRequestCashWithdrawalFailed = JsonScalaCodec(RequestCashWithdrawalFailed)
  lazy val _cRequestCashWithdrawalSucceeded = JsonScalaCodec(RequestCashWithdrawalSucceeded)
  lazy val _cRequestPasswordResetFailed = JsonScalaCodec(RequestPasswordResetFailed)
  lazy val _cRequestPasswordResetSucceeded = JsonScalaCodec(RequestPasswordResetSucceeded)
  lazy val _cResetPasswordFailed = JsonScalaCodec(ResetPasswordFailed)
  lazy val _cResetPasswordSucceeded = JsonScalaCodec(ResetPasswordSucceeded)
  lazy val _cSubmitOrderFailed = JsonScalaCodec(SubmitOrderFailed)
  lazy val _cUpdateUserProfileFailed = JsonScalaCodec(UpdateUserProfileFailed)
  lazy val _cUpdateUserProfileSucceeded = JsonScalaCodec(UpdateUserProfileSucceeded)
  lazy val _cValidatePasswordResetToken = JsonScalaCodec(ValidatePasswordResetToken)
  lazy val _cVerifyGoogleAuthCode = JsonScalaCodec(VerifyGoogleAuthCode)
  lazy val _cTAccountState = JsonScalaCodec(TAccountState)
  lazy val _cTApiSecretState = JsonScalaCodec(TApiSecretState)
  lazy val _cTMarketState = JsonScalaCodec(TMarketState)

  def toBinary(obj: AnyRef): Array[Byte] = obj match {
    case m: ApiSecret => _cApiSecret(m)
    case m: CandleData => _cCandleData(m)
    case m: CandleDataItem => _cCandleDataItem(m)
    case m: CashAccount => _cCashAccount(m)
    case m: Cursor => _cCursor(m)
    case m: Deposit => _cDeposit(m)
    case m: Fee => _cFee(m)
    case m: MarketDepth => _cMarketDepth(m)
    case m: MarketDepthItem => _cMarketDepthItem(m)
    case m: MarketSide => _cMarketSide(m)
    case m: Metrics => _cMetrics(m)
    case m: MetricsByMarket => _cMetricsByMarket(m)
    case m: Order => _cOrder(m)
    case m: OrderInfo => _cOrderInfo(m)
    case m: OrderUpdate => _cOrderUpdate(m)
    case m: RedeliverFilterData => _cRedeliverFilterData(m)
    case m: SpanCursor => _cSpanCursor(m)
    case m: Transaction => _cTransaction(m)
    case m: TransactionItem => _cTransactionItem(m)
    case m: UserAccount => _cUserAccount(m)
    case m: UserLogsState => _cUserLogsState(m)
    case m: UserProfile => _cUserProfile(m)
    case m: Withdrawal => _cWithdrawal(m)
    case m: AdminCommandResult => _cAdminCommandResult(m)
    case m: AdminConfirmCashDepositFailure => _cAdminConfirmCashDepositFailure(m)
    case m: AdminConfirmCashDepositSuccess => _cAdminConfirmCashDepositSuccess(m)
    case m: AdminConfirmCashWithdrawalFailure => _cAdminConfirmCashWithdrawalFailure(m)
    case m: AdminConfirmCashWithdrawalSuccess => _cAdminConfirmCashWithdrawalSuccess(m)
    case m: ApiSecretOperationResult => _cApiSecretOperationResult(m)
    case m: CancelOrderFailed => _cCancelOrderFailed(m)
    case m: DoAddNewApiSecret => _cDoAddNewApiSecret(m)
    case m: DoCancelOrder => _cDoCancelOrder(m)
    case m: DoDeleteApiSecret => _cDoDeleteApiSecret(m)
    case m: DoRegisterUser => _cDoRegisterUser(m)
    case m: DoRequestCashDeposit => _cDoRequestCashDeposit(m)
    case m: DoRequestCashWithdrawal => _cDoRequestCashWithdrawal(m)
    case m: DoRequestPasswordReset => _cDoRequestPasswordReset(m)
    case m: DoResetPassword => _cDoResetPassword(m)
    case m: DoSendEmail => _cDoSendEmail(m)
    case m: DoSubmitOrder => _cDoSubmitOrder(m)
    case m: DoUpdateMetrics => _cDoUpdateMetrics(m)
    case m: DoUpdateUserProfile => _cDoUpdateUserProfile(m)
    case m: GoogleAuthCodeVerificationResult => _cGoogleAuthCodeVerificationResult(m)
    case m: Login => _cLogin(m)
    case m: LoginFailed => _cLoginFailed(m)
    case m: LoginSucceeded => _cLoginSucceeded(m)
    case m: MessageNotSupported => _cMessageNotSupported(m)
    case m: OrderCancelled => _cOrderCancelled(m)
    case m: OrderFundFrozen => _cOrderFundFrozen(m)
    case m: OrderSubmitted => _cOrderSubmitted(m)
    case m: PasswordResetTokenValidationResult => _cPasswordResetTokenValidationResult(m)
    case m: QueryAccount => _cQueryAccount(m)
    case m: QueryAccountResult => _cQueryAccountResult(m)
    case m: QueryApiSecrets => _cQueryApiSecrets(m)
    case m: QueryApiSecretsResult => _cQueryApiSecretsResult(m)
    case m: QueryCandleData => _cQueryCandleData(m)
    case m: QueryCandleDataResult => _cQueryCandleDataResult(m)
    case m: QueryDeposit => _cQueryDeposit(m)
    case m: QueryDepositResult => _cQueryDepositResult(m)
    case m: QueryMarketDepth => _cQueryMarketDepth(m)
    case m: QueryMarketDepthResult => _cQueryMarketDepthResult(m)
    case m: QueryOrder => _cQueryOrder(m)
    case m: QueryOrderResult => _cQueryOrderResult(m)
    case m: QueryTransaction => _cQueryTransaction(m)
    case m: QueryTransactionResult => _cQueryTransactionResult(m)
    case m: QueryWithdrawal => _cQueryWithdrawal(m)
    case m: QueryWithdrawalResult => _cQueryWithdrawalResult(m)
    case m: RegisterUserFailed => _cRegisterUserFailed(m)
    case m: RegisterUserSucceeded => _cRegisterUserSucceeded(m)
    case m: RequestCashDepositFailed => _cRequestCashDepositFailed(m)
    case m: RequestCashDepositSucceeded => _cRequestCashDepositSucceeded(m)
    case m: RequestCashWithdrawalFailed => _cRequestCashWithdrawalFailed(m)
    case m: RequestCashWithdrawalSucceeded => _cRequestCashWithdrawalSucceeded(m)
    case m: RequestPasswordResetFailed => _cRequestPasswordResetFailed(m)
    case m: RequestPasswordResetSucceeded => _cRequestPasswordResetSucceeded(m)
    case m: ResetPasswordFailed => _cResetPasswordFailed(m)
    case m: ResetPasswordSucceeded => _cResetPasswordSucceeded(m)
    case m: SubmitOrderFailed => _cSubmitOrderFailed(m)
    case m: UpdateUserProfileFailed => _cUpdateUserProfileFailed(m)
    case m: UpdateUserProfileSucceeded => _cUpdateUserProfileSucceeded(m)
    case m: ValidatePasswordResetToken => _cValidatePasswordResetToken(m)
    case m: VerifyGoogleAuthCode => _cVerifyGoogleAuthCode(m)
    case m: TAccountState => _cTAccountState(m)
    case m: TApiSecretState => _cTApiSecretState(m)
    case m: TMarketState => _cTMarketState(m)

    case m => throw new IllegalArgumentException("Cannot serialize object: " + m)
  }

  def fromBinary(bytes: Array[Byte],
    clazz: Option[Class[_]]): AnyRef = clazz match {
    case Some(c) if c == classOf[ApiSecret.Immutable] => _cApiSecret.invert(bytes).get
    case Some(c) if c == classOf[CandleData.Immutable] => _cCandleData.invert(bytes).get
    case Some(c) if c == classOf[CandleDataItem.Immutable] => _cCandleDataItem.invert(bytes).get
    case Some(c) if c == classOf[CashAccount.Immutable] => _cCashAccount.invert(bytes).get
    case Some(c) if c == classOf[Cursor.Immutable] => _cCursor.invert(bytes).get
    case Some(c) if c == classOf[Deposit.Immutable] => _cDeposit.invert(bytes).get
    case Some(c) if c == classOf[Fee.Immutable] => _cFee.invert(bytes).get
    case Some(c) if c == classOf[MarketDepth.Immutable] => _cMarketDepth.invert(bytes).get
    case Some(c) if c == classOf[MarketDepthItem.Immutable] => _cMarketDepthItem.invert(bytes).get
    case Some(c) if c == classOf[MarketSide.Immutable] => _cMarketSide.invert(bytes).get
    case Some(c) if c == classOf[Metrics.Immutable] => _cMetrics.invert(bytes).get
    case Some(c) if c == classOf[MetricsByMarket.Immutable] => _cMetricsByMarket.invert(bytes).get
    case Some(c) if c == classOf[Order.Immutable] => _cOrder.invert(bytes).get
    case Some(c) if c == classOf[OrderInfo.Immutable] => _cOrderInfo.invert(bytes).get
    case Some(c) if c == classOf[OrderUpdate.Immutable] => _cOrderUpdate.invert(bytes).get
    case Some(c) if c == classOf[RedeliverFilterData.Immutable] => _cRedeliverFilterData.invert(bytes).get
    case Some(c) if c == classOf[SpanCursor.Immutable] => _cSpanCursor.invert(bytes).get
    case Some(c) if c == classOf[Transaction.Immutable] => _cTransaction.invert(bytes).get
    case Some(c) if c == classOf[TransactionItem.Immutable] => _cTransactionItem.invert(bytes).get
    case Some(c) if c == classOf[UserAccount.Immutable] => _cUserAccount.invert(bytes).get
    case Some(c) if c == classOf[UserLogsState.Immutable] => _cUserLogsState.invert(bytes).get
    case Some(c) if c == classOf[UserProfile.Immutable] => _cUserProfile.invert(bytes).get
    case Some(c) if c == classOf[Withdrawal.Immutable] => _cWithdrawal.invert(bytes).get
    case Some(c) if c == classOf[AdminCommandResult.Immutable] => _cAdminCommandResult.invert(bytes).get
    case Some(c) if c == classOf[AdminConfirmCashDepositFailure.Immutable] => _cAdminConfirmCashDepositFailure.invert(bytes).get
    case Some(c) if c == classOf[AdminConfirmCashDepositSuccess.Immutable] => _cAdminConfirmCashDepositSuccess.invert(bytes).get
    case Some(c) if c == classOf[AdminConfirmCashWithdrawalFailure.Immutable] => _cAdminConfirmCashWithdrawalFailure.invert(bytes).get
    case Some(c) if c == classOf[AdminConfirmCashWithdrawalSuccess.Immutable] => _cAdminConfirmCashWithdrawalSuccess.invert(bytes).get
    case Some(c) if c == classOf[ApiSecretOperationResult.Immutable] => _cApiSecretOperationResult.invert(bytes).get
    case Some(c) if c == classOf[CancelOrderFailed.Immutable] => _cCancelOrderFailed.invert(bytes).get
    case Some(c) if c == classOf[DoAddNewApiSecret.Immutable] => _cDoAddNewApiSecret.invert(bytes).get
    case Some(c) if c == classOf[DoCancelOrder.Immutable] => _cDoCancelOrder.invert(bytes).get
    case Some(c) if c == classOf[DoDeleteApiSecret.Immutable] => _cDoDeleteApiSecret.invert(bytes).get
    case Some(c) if c == classOf[DoRegisterUser.Immutable] => _cDoRegisterUser.invert(bytes).get
    case Some(c) if c == classOf[DoRequestCashDeposit.Immutable] => _cDoRequestCashDeposit.invert(bytes).get
    case Some(c) if c == classOf[DoRequestCashWithdrawal.Immutable] => _cDoRequestCashWithdrawal.invert(bytes).get
    case Some(c) if c == classOf[DoRequestPasswordReset.Immutable] => _cDoRequestPasswordReset.invert(bytes).get
    case Some(c) if c == classOf[DoResetPassword.Immutable] => _cDoResetPassword.invert(bytes).get
    case Some(c) if c == classOf[DoSendEmail.Immutable] => _cDoSendEmail.invert(bytes).get
    case Some(c) if c == classOf[DoSubmitOrder.Immutable] => _cDoSubmitOrder.invert(bytes).get
    case Some(c) if c == classOf[DoUpdateMetrics.Immutable] => _cDoUpdateMetrics.invert(bytes).get
    case Some(c) if c == classOf[DoUpdateUserProfile.Immutable] => _cDoUpdateUserProfile.invert(bytes).get
    case Some(c) if c == classOf[GoogleAuthCodeVerificationResult.Immutable] => _cGoogleAuthCodeVerificationResult.invert(bytes).get
    case Some(c) if c == classOf[Login.Immutable] => _cLogin.invert(bytes).get
    case Some(c) if c == classOf[LoginFailed.Immutable] => _cLoginFailed.invert(bytes).get
    case Some(c) if c == classOf[LoginSucceeded.Immutable] => _cLoginSucceeded.invert(bytes).get
    case Some(c) if c == classOf[MessageNotSupported.Immutable] => _cMessageNotSupported.invert(bytes).get
    case Some(c) if c == classOf[OrderCancelled.Immutable] => _cOrderCancelled.invert(bytes).get
    case Some(c) if c == classOf[OrderFundFrozen.Immutable] => _cOrderFundFrozen.invert(bytes).get
    case Some(c) if c == classOf[OrderSubmitted.Immutable] => _cOrderSubmitted.invert(bytes).get
    case Some(c) if c == classOf[PasswordResetTokenValidationResult.Immutable] => _cPasswordResetTokenValidationResult.invert(bytes).get
    case Some(c) if c == classOf[QueryAccount.Immutable] => _cQueryAccount.invert(bytes).get
    case Some(c) if c == classOf[QueryAccountResult.Immutable] => _cQueryAccountResult.invert(bytes).get
    case Some(c) if c == classOf[QueryApiSecrets.Immutable] => _cQueryApiSecrets.invert(bytes).get
    case Some(c) if c == classOf[QueryApiSecretsResult.Immutable] => _cQueryApiSecretsResult.invert(bytes).get
    case Some(c) if c == classOf[QueryCandleData.Immutable] => _cQueryCandleData.invert(bytes).get
    case Some(c) if c == classOf[QueryCandleDataResult.Immutable] => _cQueryCandleDataResult.invert(bytes).get
    case Some(c) if c == classOf[QueryDeposit.Immutable] => _cQueryDeposit.invert(bytes).get
    case Some(c) if c == classOf[QueryDepositResult.Immutable] => _cQueryDepositResult.invert(bytes).get
    case Some(c) if c == classOf[QueryMarketDepth.Immutable] => _cQueryMarketDepth.invert(bytes).get
    case Some(c) if c == classOf[QueryMarketDepthResult.Immutable] => _cQueryMarketDepthResult.invert(bytes).get
    case Some(c) if c == classOf[QueryOrder.Immutable] => _cQueryOrder.invert(bytes).get
    case Some(c) if c == classOf[QueryOrderResult.Immutable] => _cQueryOrderResult.invert(bytes).get
    case Some(c) if c == classOf[QueryTransaction.Immutable] => _cQueryTransaction.invert(bytes).get
    case Some(c) if c == classOf[QueryTransactionResult.Immutable] => _cQueryTransactionResult.invert(bytes).get
    case Some(c) if c == classOf[QueryWithdrawal.Immutable] => _cQueryWithdrawal.invert(bytes).get
    case Some(c) if c == classOf[QueryWithdrawalResult.Immutable] => _cQueryWithdrawalResult.invert(bytes).get
    case Some(c) if c == classOf[RegisterUserFailed.Immutable] => _cRegisterUserFailed.invert(bytes).get
    case Some(c) if c == classOf[RegisterUserSucceeded.Immutable] => _cRegisterUserSucceeded.invert(bytes).get
    case Some(c) if c == classOf[RequestCashDepositFailed.Immutable] => _cRequestCashDepositFailed.invert(bytes).get
    case Some(c) if c == classOf[RequestCashDepositSucceeded.Immutable] => _cRequestCashDepositSucceeded.invert(bytes).get
    case Some(c) if c == classOf[RequestCashWithdrawalFailed.Immutable] => _cRequestCashWithdrawalFailed.invert(bytes).get
    case Some(c) if c == classOf[RequestCashWithdrawalSucceeded.Immutable] => _cRequestCashWithdrawalSucceeded.invert(bytes).get
    case Some(c) if c == classOf[RequestPasswordResetFailed.Immutable] => _cRequestPasswordResetFailed.invert(bytes).get
    case Some(c) if c == classOf[RequestPasswordResetSucceeded.Immutable] => _cRequestPasswordResetSucceeded.invert(bytes).get
    case Some(c) if c == classOf[ResetPasswordFailed.Immutable] => _cResetPasswordFailed.invert(bytes).get
    case Some(c) if c == classOf[ResetPasswordSucceeded.Immutable] => _cResetPasswordSucceeded.invert(bytes).get
    case Some(c) if c == classOf[SubmitOrderFailed.Immutable] => _cSubmitOrderFailed.invert(bytes).get
    case Some(c) if c == classOf[UpdateUserProfileFailed.Immutable] => _cUpdateUserProfileFailed.invert(bytes).get
    case Some(c) if c == classOf[UpdateUserProfileSucceeded.Immutable] => _cUpdateUserProfileSucceeded.invert(bytes).get
    case Some(c) if c == classOf[ValidatePasswordResetToken.Immutable] => _cValidatePasswordResetToken.invert(bytes).get
    case Some(c) if c == classOf[VerifyGoogleAuthCode.Immutable] => _cVerifyGoogleAuthCode.invert(bytes).get
    case Some(c) if c == classOf[TAccountState.Immutable] => _cTAccountState.invert(bytes).get
    case Some(c) if c == classOf[TApiSecretState.Immutable] => _cTApiSecretState.invert(bytes).get
    case Some(c) if c == classOf[TMarketState.Immutable] => _cTMarketState.invert(bytes).get

    case Some(c) => throw new IllegalArgumentException("Cannot deserialize class: " + c.getCanonicalName)
    case None => throw new IllegalArgumentException("No class found in EventSerializer when deserializing array: " + bytes.mkString("").take(100))
  }
}
