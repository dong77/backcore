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
typedef data.RedeliverFilterData   RedeliverFilterData

struct TUserState {
    1: map<i64, UserProfile> profileMap
    2: map<string, i64> passwordResetTokenMap
    3: map<string, i64> verificationTokenMap
    4: i64 numUsers
}

struct TAccountState {
    1: map<i64, UserAccount> userAccountsMap
    2: map<string, RedeliverFilterData> filters
}

struct TMarketState {
    1: MarketSide side
    2: map<MarketSide, list<Order>> orderPools
    3: map<i64, Order> orderMap
    4: optional double priceRestriction
    5: map<string, RedeliverFilterData> filters
}

struct TApiSecretState {
    1: map<string, ApiSecret> identifierLookupMap // key is identifier
    2: map<i64, list<ApiSecret>> userSecretMap // key is userId
    3: string seed
}

struct TMarketDepthState {
    1: map<double, i64> askMap
    2: map<double, i64> bidMap
}
struct TExportToMongoState {
    1: i64 snapshotIndex
    2: i64 index
    3: string hash
}
