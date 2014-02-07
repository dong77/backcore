/**
 * Author: Ma Chao(c@coinport.com)
 * 2014-02-04
 * Copyright 2014 Coinport All Rights Reserved.
 */

namespace java com.coinport.f1

enum BPCommandType {
    REGISTER_USER = 1,
    DW = 2,
    PLACE_ORDER = 3,
    CANCEL_ORDER = 4,
}

enum BOS {
    BUY = 1,
    SELL = 2,
}

enum MOL {
    MARKET = 1,
    LIMIT = 2,
}

enum SOM {
    MORE = 1,
    SHORT = 2,
}

enum Strategy {
    NORMAL = 1,
    STOP = 2,
    TRAILING_STOP = 3,
}

enum CoinType {
    BTC = 1,
    LTC = 2,
    PTS = 3,

    CNY = 1000,
    USD = 2000,
    EUR = 3000,
    JPY = 4000,
    CAD = 5000,
}

enum DOW {
    DEPOSIT = 1,
    WITHDRAWAL = 2,
}

struct OrderTypes {
    1: optional BOS bos,
    2: optional MOL mol,
    3: optional SOM som,
    4: optional Strategy strategy,
}

struct Wallet {
    1: required CoinType coinType,
    2: optional i64 valid,
    3: optional i64 frozen,
}

struct UserInfo {
    1: required i64 id,
    2: optional string nickname,
    3: optional string password,
    4: optional map<CoinType, Wallet> wallets,
}

struct DWInfo {
    1: required i64 uid,
    2: required DOW dwtype,
    3: required CoinType coinType,
    4: required i64 amount,
}

struct OrderInfo {
    1: required i32 id,
    2: optional string uid,
    3: optional OrderTypes orderTypes,
    4: optional CoinType from,
    5: optional CoinType to,
    6: optional i64 price,  // 单位为(1/100000000)
    7: optional double percentage,
    8: optional i64 actPrice,
    9: optional i32 quantity,
    10: optional i64 expired, // TODO(c): is this good enough?
    11: optional string routing,
}

struct BPCommand {
    1: required BPCommandType type,
    2: optional UserInfo userInfo,
    3: optional DWInfo dwInfo,
    4: optional OrderInfo orderInfo,
    5: optional i64 timestamp,
    6: optional i64 index,  // used for ordering the order
}
