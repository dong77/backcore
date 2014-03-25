/**
 * Copyright {C} 2014 Coinport Inc. <http://www.coinport.com>
 *
 * All classes here are case-classes or case-objects. This is required since we are
 * maintaining an in-memory state that's immutable, so that while snapshot is taken,
 * the in-memory state can still be updated.
 */

// WARNING: all structs must have at least 1 parameters, otherwise serialization fails.

namespace java com.coinport.coinex.data

//---------------------------------------------------------------------
// Data and Structs
enum Currency {
    UNKNOWN = 0
    RMB = 1
    USD = 2
    BTC = 1000
}

enum OrderStatus {
    PENDING = 0
    PARTIALLY_EXECUTED = 1
    FULLY_EXECUTED = 2
    CANCELLED = 3
    MARKET_AUTO_CANCELLED = 4
    MARKET_AUTO_PARTIALLY_CANCELLED = 5
}

enum AccountOperationCode {
    OK = 0
    INSUFFICIENT_FUND = 1
    INVALID_AMOUNT = 2
}

enum OrderSubmissionFailReason {
    PRICE_OUT_OF_RANGE = 1
}

enum RegisterationFailureReason {
    EMAIL_ALREADY_REGISTERED = 1
    MISSING_INFORMATION = 2
}

enum LoginFailureReason {
    USER_NOT_EXIST = 1
    PASSWORD_NOT_MATCH = 2
}

enum ResetPasswordFailureReason {
    USER_NOT_EXIST = 1
    TOKEN_NOT_MATCH = 2
}

enum RequestPasswordResetFailureReason {
    USER_NOT_EXIST = 1
    TOKEN_NOT_UNIQUE = 2
}

enum UserStatus {
    NORMAL = 0
    SUSPENDED = 1
}

enum EmailType {
    REGISTER_VERIFY = 1
    LOGIN_TOKEN = 2
    PASSWORD_RESET_TOKEN = 3
}

//---------------------------------------------------------------------
// User profile related
struct UserProfile {
    1: i64 id
    2: string email
    3: string realName
    4: string nationalId
    5: optional list<byte> passwordHash
    6: bool emailVerified
    8: optional string mobile
    9: bool mobileVerified
    10: optional string passwordResetToken
    11: optional string verificationToken
    12: optional string loginToken
    14: UserStatus status
}

enum ChartTimeDimension {
    ONE_MINUTE = 1
    THREE_MINUTES = 2
    FIVE_MINUTES = 3
    FIFTEEN_MINUTES = 4
    THIRTY_MINUTES = 5
    ONE_HOUR = 6
    TWO_HOURS = 7
    FOUR_HOURS = 8
    SIX_HOURS = 9
    TWELVE_HOURS = 10
    ONE_DAY = 11
    THREE_DAYS = 12
    ONE_WEEK = 13
}

struct MarketSide {
    1: Currency outCurrency
    2: Currency inCurrency
}

struct Order {
    1: i64 userId
    2: i64 id
    3: i64 quantity
    4: optional double price
    5: optional i64 takeLimit
    6: optional i64 timestamp
}

struct OrderInfo {
    1: MarketSide side
    2: Order order
    3: i64 outAmount
    4: i64 inAmount
    5: OrderStatus status
    6: optional i64 lastTxTimestamp
}

struct OrderUpdate {
    1: Order previous
    2: Order current
}

struct Transaction{
    1: i64 timestamp
    2: OrderUpdate takerUpdate
    3: OrderUpdate makerUpdate
}
struct CashAccount{
    1: Currency currency
    2: i64 available
    3: i64 locked
    4: i64 pendingWithdrawal
}

struct UserAccount {
    1: i64 userId
    2: map<Currency, CashAccount> cashAccounts
}

struct PersistentAccountState {
    1: i64 lastOrderId
    2: map<i64, UserAccount> userAccountsMap
}

struct UserLogsState {
    1: map<i64, list<OrderInfo>> orderInfoMap
}

struct MarketDepthItem {
    1: double price
    2: i64 quantity
}

struct MarketDepth {
    1: MarketSide side
    2: list<MarketDepthItem> asks
    3: list<MarketDepthItem> bids
}

struct CandleDataItem {
    1: i64 timestamp
    2: i64 volumn
    3: double open
    4: double close
    5: double low
    6: double high
}

struct CandleData {
    1: i64 timestamp
    2: list<CandleDataItem> items
}

struct MarketByMetrics {
    1: MarketSide side
    2: double price
}

struct RobotMetrics {
    1: map<MarketSide, MarketByMetrics> marketByMetrics
}

struct TransactionItem {
    1: i64 timestamp
    2: double price
    3: i64 volumn
    4: i64 amount
    5: i64 taker
    6: i64 maker
    7: bool sameSide
}

struct TransactionData {
    1: list<TransactionItem> items
}

// ------------------------------------------------------------------------------------------------
// Non-persistent message.
struct RegisterUserFailed{1: RegisterationFailureReason reason, 2: optional UserProfile userProfile}
struct RegisterUserSucceeded{1: UserProfile userProfile}

struct Login{1: string email, 2: string password}
struct LoginFailed{1: LoginFailureReason reason}
struct LoginSucceeded{1: i64 id, 2: string email}

struct RequestPasswordResetFailed{1: RequestPasswordResetFailureReason reason}
struct RequestPasswordResetSucceeded{1: i64 id, 2: string email, 3: string passwordResetToken}

struct ValidatePasswordResetToken{1: string passwordResetToken}
struct ValidatePasswordResetTokenResult{1: optional UserProfile userProfile}

struct ResetPasswordFailed{1: ResetPasswordFailureReason reason}
struct ResetPasswordSucceeded{1: i64 id, 2: string email}

struct AccountOperationResult{1: AccountOperationCode code, 2: CashAccount cashAccount}
struct OrderSubmissionDone{1: MarketSide side, 2: Order order, 3: list<Transaction> txs}

struct QueryUserOrders{1: i64 userId, 2: optional i32 numOrders, 3: optional i32 skipOrders, 4: optional OrderStatus status}
struct QueryUserOrdersResult{1: i64 userId, 2: list<OrderInfo> orders}

struct QueryAccount{1: i64 userId}
struct QueryAccountResult{1: UserAccount userAccount}

struct QueryMarket{1: MarketSide side, 2: i32 maxDepth}
struct QueryMarketResult{1: MarketDepth marketDepth}
struct QueryMarketUnsupportedMarketFailure{1: MarketSide side}

struct QueryCandleData{1: MarketSide side, 2: ChartTimeDimension dimension, 3: i64 from, 4: i64 to}
struct QueryCandleDataResult{1: CandleData candleData}

struct QueryTransactionData{1: MarketSide side, 2: i64 from, 3: i32 num}
struct QueryTransactionDataResult{1: TransactionData transactionData}

struct OrderSubmissionInProgross{1: MarketSide side, 2: Order order}

struct SendMailRequest{1: string email, 2: EmailType emailType, 3: map<string, string> params}
// ----------------------------------------------------------------------------
// Persistent Commands - all commands are sent by outside world.
// Please name all commands starting with "Do"

// UserProcessor commands
struct DoRegisterUser{1: UserProfile userProfile, 2: string password}
struct DoRequestPasswordReset{1: string email, 2: string passwordResetToken}
struct DoResetPassword{1: string email, 2: string password, 3: optional string passwordResetToken}

// AccountProcessor commands
struct DoSubmitOrder{1: MarketSide side, 2: Order order}
struct DoDepositCash{1: i64 userId, 2: Currency currency, 3: i64 amount}
struct DoRequestCashWithdrawal{1: i64 userId, 2: Currency currency, 3: i64 amount}
struct DoConfirmCashWithdrawalSuccess{1: i64 userId, 2: Currency currency, 3: i64 amount}
struct DoConfirmCashWithdrawalFailed{1: i64 userId, 2: Currency currency, 3: i64 amount}

// MarketProcessor commands
struct DoCancelOrder{1: MarketSide side, 2: i64 id}

// ------------------------------------------------------------------------------------------------
// Persistent Events. All events are generated by a certain processor and handeled by another processor.
// For each event, we'll comment it in the form of "origin -> handler".

// AccountProcessor -> MarketProcessor events
struct OrderCashLocked{1: MarketSide side, 2: Order order}

// MarketProcessor -> AccountProcessor/MarketUpdateProcessor events
struct OrderCancelled{1: MarketSide side, 2: Order order}
struct OrderSubmissionFailed{1: MarketSide side, 2: Order order, 3: OrderSubmissionFailReason reason}
struct OrderSubmitted{1: OrderInfo originOrderInfo, 2: list<Transaction> txs}
