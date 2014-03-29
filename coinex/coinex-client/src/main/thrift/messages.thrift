/**
 * Copyright {C} 2014 Coinport Inc. <http://www.coinport.com>
 *
 * All classes here are case-classes or case-objects. This is required since we are
 * maintaining an in-memory state that's immutable, so that while snapshot is taken,
 * the in-memory state can still be updated.
 */

// WARNING: all structs must have at least 1 parameters, otherwise serialization fails.

namespace java com.coinport.coinex.data

////////////////////////////////////////////////////////////////
////////////////////// PERSISTENT ENUMS ////////////////////////
////////////////////////////////////////////////////////////////
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

enum UserStatus {
    NORMAL = 0
    SUSPENDED = 1
}

enum EmailType {
    REGISTER_VERIFY = 1
    LOGIN_TOKEN = 2
    PASSWORD_RESET_TOKEN = 3
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

////////////////////////////////////////////////////////////////
/////////////////////// PERSISTENT DATA ////////////////////////
////////////////////////////////////////////////////////////////
// User profile related
struct UserProfile {
    1:  i64 id
    2:  string email
    3:  optional string realName
    4:  optional string nationalId
    5:  optional string passwordHash
    6:  bool emailVerified
    8:  optional string mobile
    9:  bool mobileVerified
    10: optional string passwordResetToken
    11: optional string verificationToken
    12: optional string loginToken
    14: UserStatus status
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
    7: optional i64 robotId
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
    8: i64 tOrder
    9: i64 mOrder
}

struct TransactionData {
    1: list<TransactionItem> items
}

struct ApiSecret {
    1: string secret
    2: optional string identifier
    3: optional i64 userId
}

struct ApiSecretState {
	1: map<string, ApiSecret> identifierLookupMap // key is identifier
	2: map<i64, list<ApiSecret>> userSecretMap // key is userId
	3: string seed
}

struct Fee {
    1: i64 payer
    2: i64 payee
    3: Currency currency
    4: i64 amount
    5: optional string basis
}

////////////////////////////////////////////////////////////////
///////////////////////// ERROR CODES //////////////////////////
////////////////////////////////////////////////////////////////

enum ErrorCode {
    OK = 0

    // User related
    EMAIL_ALREADY_REGISTERED         = 1001
    MISSING_INFORMATION              = 1002
    USER_NOT_EXIST                   = 1003
    PASSWORD_NOT_MATCH               = 1004
    TOKEN_NOT_MATCH                  = 1005
    TOKEN_NOT_UNIQUE                 = 1006
    
    // Account related
    PRICE_OUT_OF_RANGE               = 2001
    INSUFFICIENT_FUND                = 2002
    INVALID_AMOUNT                   = 2003

    // Market related

    // Api Auth related
    TOO_MANY_SECRETS                 = 5001
    INVALID_SECRET                   = 5002
}

////////////////////////////////////////////////////////////////
///////////////////// PROCESSOR MESSAGES ///////////////////////
////////////////////////////////////////////////////////////////
// 'C' stands for external command,
// 'P' stands for persistent event derived from a external command,
// 'Q' for query,
// 'I' stands for inter-processor commands
// 'R+' stands for response to sender on command success,
// 'R-' stands for response to sender on command failure,
// 'R' stands for response to sender regardless of failure or success.

////////// Admin
/* R    */ struct AdminCommandResult                  {1: ErrorCode error = ErrorCode.OK}

////////// UserProcessor
/* C,P  */ struct DoRegisterUser                      {1: UserProfile userProfile, 2: string password}
/* R-   */ struct RegisterUserFailed                  {1: ErrorCode error}
/* R+   */ struct RegisterUserSucceeded               {1: UserProfile userProfile}

/* C,P  */ struct DoRequestPasswordReset              {1: string email}
/* R-   */ struct RequestPasswordResetFailed          {1: ErrorCode error}
/* R+   */ struct RequestPasswordResetSucceeded       {1: i64 id, 2: string email, 3: string passwordResetToken}

/* C,P  */ struct DoResetPassword                     {1: string email, 2: string password, 3: optional string passwordResetToken}
/* R-   */ struct ResetPasswordFailed                 {1: ErrorCode error}
/* R+   */ struct ResetPasswordSucceeded              {1: i64 id, 2: string email}

/* C    */ struct Login                               {1: string email, 2: string password} // TODO: this may also be a persistent command
/* R-   */ struct LoginFailed                         {1: ErrorCode error}
/* R+   */ struct LoginSucceeded                      {1: i64 id, 2: string email}

/* Q    */ struct ValidatePasswordResetToken          {1: string passwordResetToken}
/* R    */ struct PasswordResetTokenValidationResult  {1: optional UserProfile userProfile}

/* C,P  */ struct DoRequestCashDeposit                {1: i64 userId, 2: Currency currency, 3: i64 amount}
/* R-   */ struct RequestCashDepositFailed            {1: ErrorCode error}
/* R+   */ struct RequestCashDepositSucceeded         {1: i64 userId, 2: Currency currency, 3: i64 amount}

/* C,P  */ struct DoRequestCashWithdrawal             {1: i64 userId, 2: Currency currency, 3: i64 amount}
/* R-   */ struct RequestCashWithdrawalFailed         {1: ErrorCode error}
/* R+   */ struct RequestCashWithdrawalSucceeded      {1: i64 userId, 2: Currency currency, 3: i64 amount}

/* C,P  */ struct AdminConfirmCashDepositFailure      {1: i64 userId, 2: Currency currency, 3: i64 amount, 4:ErrorCode error}
/* C,P  */ struct AdminConfirmCashDepositSuccess      {1: i64 userId, 2: Currency currency, 3: i64 amount}
/* C,P  */ struct AdminConfirmCashWithdrawalFailure   {1: i64 userId, 2: Currency currency, 3: i64 amount, 4:ErrorCode error}
/* C,P  */ struct AdminConfirmCashWithdrawalSuccess   {1: i64 userId, 2: Currency currency, 3: i64 amount}

/* C,P  */ struct DoSubmitOrder                       {1: MarketSide side, 2: Order order}
/* I,R- */ struct SubmitOrderFailed                   {1: MarketSide side, 2: Order order, 3: ErrorCode error}
/* I,R+ */ struct OrderFundFrozen                     {1: MarketSide side, 2: Order order}


////////// ApiAuthProcessor
/* C,P  */ struct DoAddNewApiSecret                   {1: i64 userId}
/* C,P  */ struct DoDeleteApiSecret                   {1: ApiSecret secret}
/* R    */ struct ApiSecretOperationResult            {1: ErrorCode error, 2: list<ApiSecret> secrets}

/* Q    */ struct QueryApiSecrets                     {1: i64 userId, 2: optional string identifier}
/* R    */ struct QueryApiSecretsResult               {1: i64 userId, 2: list<ApiSecret> secrets}


////////// MarketProcessor
/* C,P  */ struct DoCancelOrder                       {1: MarketSide side, 2: i64 id, 3: i64 userId}
/* R-   */ struct CancelOrderFailed                   {1: ErrorCode error}

/* I    */ struct OrderSubmitted                      {1: OrderInfo originOrderInfo, 2: list<Transaction> txs}
/* I,R+ */ struct OrderCancelled                      {1: MarketSide side, 2: Order order}

////////// RobotProcessor commands
/* C,P  */ struct DoUpdateMetrics                     {1: RobotMetrics metrics}

////////// Mailer
/* C    */ struct DoSendEmail                         {1: string email, 2: EmailType emailType, 3: map<string, string> params}

////////////////////////////////////////////////////////////////
//////////////////////// VIEW MESSAGES /////////////////////////
////////////////////////////////////////////////////////////////

////////// AccountView
/* Q    */ struct QueryAccount                        {1: i64 userId}
/* R    */ struct QueryAccountResult                  {1: UserAccount userAccount}

////////// MarketDepthView
/* Q    */ struct QueryMarketDepth                    {1: MarketSide side, 2: i32 maxDepth}
/* R    */ struct QueryMarketDepthResult              {1: MarketDepth marketDepth}

////////// CandleDataView
/* Q    */ struct QueryCandleData                     {1: MarketSide side, 2: ChartTimeDimension dimension, 3: i64 from, 4: i64 to}
/* R    */ struct QueryCandleDataResult               {1: CandleData candleData}

////////// UserTransactionView
/* Q    */ struct QueryUserTransaction                {1: MarketSide side, 2: i64 userId, 3: optional i64 orderId, 4: i64 from, 5: i32 num}
/* R    */ struct QueryUserTransactionResult          {1: TransactionData transactionData}

////////// UserOrdersView
/* Q    */ struct QueryUserOrders                     {1: i64 userId, 2: optional i32 numOrders, 3: optional i32 skipOrders, 4: optional OrderStatus status}
/* R    */ struct QueryUserOrdersResult               {1: i64 userId, 2: list<OrderInfo> orders}

////////// TransactionDataView
/* Q    */ struct QueryTransactionData                {1: MarketSide side, 2: i64 from, 3: i32 num}
/* R    */ struct QueryTransactionDataResult          {1: TransactionData transactionData}

////////// OrderDataView
/* Q    */ struct QueryOrderData                      {1: MarketSide side, 2: optional i64 orderId, 4: i64 from, 5: i64 to}
/* R    */ struct QueryOrderDataResult                {1: list<OrderInfo> orderInfos}