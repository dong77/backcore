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
    10: required i64 id,
    20: required i64 uid,
    30: required i32 quantity,
    40: optional i64 timestamp,
    50: optional BOS bos = BOS.BUY,
    60: optional MOL mol = MOL.LIMIT,
    70: optional SOM som = SOM.MORE,
    80: optional Strategy strategy = Strategy.NORMAL,
    90: optional CoinType from = CoinType.CNY,
    100: optional CoinType to = CoinType.BTC,
    110: optional i64 price,  // 单位为(1/100000000)
    120: optional double percentage,
    130: optional i64 actPrice,
    140: optional i64 expired = -1, // TODO(c): is this good enough?
    150: optional string routing,  // choose a exchange or auto
}

struct BPCommand {
    1: required BPCommandType type,
    2: optional UserInfo userInfo,
    3: optional DWInfo dwInfo,
    4: optional OrderInfo orderInfo,
    5: optional i64 timestamp,
    6: optional i64 index,  // used for ordering the order
}
