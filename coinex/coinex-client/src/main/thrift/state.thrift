/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

namespace java com.coinport.coinex.data

include "messages.thrift"

typedef messages.MarketSide MarketSide
typedef messages.Order Order

struct TMarketState {
    1: MarketSide side
    2: map<MarketSide, list<Order>> orderPools
    3: map<i64, Order> orderMap
    4: optional double priceRestriction
}
