/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
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

enum CommandStats {
    SUCCESS = 1,
    FAIL = 2,
    TBR = 3,
}

struct Wallet {
    1: required CoinType coinType,
    2: optional i64 valid = 0,
    3: optional i64 frozen = 0,
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

struct TradePair {
    1: required CoinType from = CoinType.CNY,
    2: required CoinType to = CoinType.BTC,
}

struct OrderInfo {
    1: required i64 id,
    2: required i64 uid,
    3: required TradePair tradePair;  // TODO(c) How to give a default value?
    4: optional i64 quantity = 1,
    5: optional i64 timestamp,
    6: optional BOS bos = BOS.BUY,
    7: optional MOL mol = MOL.LIMIT,
    8: optional SOM som = SOM.MORE,
    9: optional Strategy strategy = Strategy.NORMAL,
    10: optional i64 price,  // 单位为(1/100000000)
    11: optional double percentage,
    12: optional i64 actPrice,
    13: optional i64 expired = -1, // TODO(c): is this good enough?
    14: optional string routing,  // choose a exchange or auto
}

struct BPCommand {
    1: optional i64 id,  // TBU
    2: optional BPCommandType type,
    3: optional UserInfo userInfo,
    4: optional DWInfo dwInfo,
    5: optional OrderInfo orderInfo,
    6: optional i64 timestamp,
    7: optional i64 index,  // used for ordering the order
    8: optional CommandStats stats = CommandStats.TBR,
}
