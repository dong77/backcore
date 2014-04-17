/**
 * Copyright {C} 2014 Coinport Inc. <http://www.coinport.com>
 *
 * WARNING:
 *  All structs must have at least 1 parameters, otherwise AKKA serialization fails.
 */

namespace java com.coinport.coinex.data

include "data.thrift"

///////////////////////////////////////////////////////////////////////
///////////////////// PROCESSOR AND VIEW STATES ///////////////////////
///////////////////////////////////////////////////////////////////////

typedef data.Order                 Order
typedef data.MarketSide            MarketSide
typedef data.ApiSecret             ApiSecret
typedef data.UserAccount           UserAccount
typedef data.UserProfile           UserProfile
typedef data.RedeliverFilters      RedeliverFilters
typedef data.ChartTimeDimension    ChartTimeDimension
typedef data.CandleDataItem        CandleDataItem
typedef data.TMetricsObserver      TMetricsObserver
typedef data.MarketPrice           MarketPrice
typedef data.TimePrice             TimePrice
typedef data.CashAccount           CashAccount
typedef data.Currency              Currency


struct TUserState {
    1: map<i64, UserProfile> profileMap
    2: map<string, i64> passwordResetTokenMap
    3: map<string, i64> verificationTokenMap
    4: i64 numUsers
}

struct TAccountState {
    1: map<i64, UserAccount> userAccountsMap
    2: RedeliverFilters filters
    3: UserAccount aggregation
}

struct TMarketState {
    1: i64 lastOrderId
    2: i64 lastTxId
    3: map<MarketSide, list<Order>> orderPools
    4: map<i64, Order> orderMap
    5: optional double priceRestriction
    6: RedeliverFilters filters
}

struct TApiSecretState {
    1: map<string, ApiSecret> identifierLookupMap // key is identifier
    2: map<i64, list<ApiSecret>> userSecretMap // key is userId
    3: string seed
}

struct TDepositWithdrawState {
    1: i64 lastDepositId
    2: i64 lastWithdrawId
    3: RedeliverFilters filters
}
struct TMarketDepthState {
    1: map<double, i64> askMap
    2: map<double, i64> bidMap
}

struct TExportToMongoState {
    1: i64 snapshotIndex
    2: i64 index
    3: string hash
    4: i64 lastSnapshotTimestamp
}

struct TCandleDataState {
    1: map<ChartTimeDimension, map<i64, CandleDataItem>> candleMap
}

struct TAssetState {
    1: map<i64, map<i64, map<Currency, i64>>> userAssetMap
    2: map<MarketSide, map<i64, double>> marketPriceMap
}

struct TSimpleState {
    1: RedeliverFilters filters
}

struct TMetricsState {
    1: map<MarketSide, TMetricsObserver> observers
    2: RedeliverFilters filters
}
