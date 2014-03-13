/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
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
}

enum OrderStatus {
	PENDING = 0
	PARTIALLY_EXECUTED = 1
	FULLY_EXECUTED = 2
	CANCELLED = 3
}

struct Transfer{
	1: i64 userId
	2: i64 orderId
	3: Currency currency
	4: i64 quantity
	5: bool fullyExecuted
}
struct Transaction{
	1: Transfer taker
	2: Transfer maker
}
struct CashAccount{
	1: Currency currency
	2: i64 available
	3: i64 locked
	4: i64 pendingWithdrawal
}

enum AccountOperationCode {
	OK = 0
	INSUFFICIENT_FUND = 1
	INVALID_AMOUNT = 2
}

struct CashAccount {
	1: Currency currency
	2: i64 available = 0
	3: i64 locked = 0
	4: i64 pendingWithdrawal = 0
}

struct UnlockFund {
	1: i64 userId
	2: Currency currency
	3: i64 amount
}

struct UserAccount {
	1: i64 userId
	2: map<Currency, CashAccount> cashAccounts
}

struct Price {
	1: MarketSide side
	2: double price
}

struct User{
}

struct OrderInfo {
	1: MarketSide side
	2: Order order
	3: OrderStatus status
}

struct UserLog {
	1: list<OrderInfo> orderInfos
	2: list<Transaction> txs
}

struct UserLogs {
 	1: map<i64, UserLog> userLogs
}

// ------------------------------------------------------------------------------------------------
// Non-persistent message.
struct AccountOperationResult{1: AccountOperationCode code, 2: CashAccount cashAccount}
struct OrderSubmissionDone{1: MarketSide side, 2: Order order, 3: list<Transaction> txs}

struct QueryUserLog{1: i64 userId, 2: optional i32 numOrders, 3: optional i32 skipOrders, 4: optional OrderStatus status, 5: optional i32 numTxs, 6: optional i32 skipTxs}
struct QueryUserLogResult{1: i64 userId, 2: UserLog userLog}

struct QueryAccount{1: i64 userId}
struct QueryAccountResult{1: UserAccount userAccount}

struct QueryMarket{1: MarketSide side, 2: i32 depth}
struct QueryMarketResult{1: optional Price price, 2: list<Order> orders1, 3: list<Order> orders2}

struct OrderSubmissionInProgross{1: MarketSide side, 2: Order order}
// ----------------------------------------------------------------------------
// Persistent Commands - all commands are sent by outside world.
// Please name all commands starting with "Do"

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
struct OrderSubmitted{1: MarketSide side, 2: Order order}

// MarketProcessor -> AccountProcessor events
struct OrderCancelled{1: MarketSide side, 2:Order order}
struct NewTxPriceSeen{1: MarketSide side, 2: double price}

// MarketProcessor -> AggregateUserView
struct MarketUpdate{1: OrderInfo originOrderInfo, 2: i64 currentQuantity, 3: list<Order> fullyExecutedOrders, 4: list<Order> partiallyExecutedOrders, 5: list<Transaction> txs, 6: list<UnlockFund> unlockFunds}