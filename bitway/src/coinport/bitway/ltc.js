/**
 *Copyright 2014 Coinport Inc. All Rights Reserved.
 *Author: YangLi--ylautumn84@gmail.com
 *Filename: ltc.js
 *Description: 
 */

var CryptoProxy = require('./cryptoProxy').CryptoProxy;
var RedisProxy  = require('./redis/redis_proxy').RedisProxy;
var DataTypes                       = require('../../../gen-nodejs/data_types')
    MessageTypes                    = require('../../../gen-nodejs/message_types')
    Bitcore                         = require('bitcore'),
    BitwayRequestType               = DataTypes.BitwayRequestType,
    BitwayResponseType              = DataTypes.BitwayResponseType,
    GenerateAddressesResult         = MessageTypes.GenerateAddressesResult,
    CryptoCurrencyTransaction       = DataTypes.CryptoCurrencyTransaction,
    CryptoCurrencyTransactionPort   = DataTypes.CryptoCurrencyTransactionPort,
    CryptoCurrencyBlock             = DataTypes.CryptoCurrencyBlock,
    TransferStatus                  = DataTypes.TransferStatus,
    TransferType                    = DataTypes.TransferType,
    CryptoCurrencyAddressType       = DataTypes.CryptoCurrencyAddressType,
    Currency                        = DataTypes.Currency,
    BlockIndex                      = DataTypes.BlockIndex,
    CryptoCurrencyBlocksMessage     = MessageTypes.CryptoCurrencyBlocksMessage,
    GetMissedCryptoCurrencyBlocks   = MessageTypes.GetMissedCryptoCurrencyBlocks,
    ErrorCode                       = DataTypes.ErrorCode,
    BitwayMessage                   = MessageTypes.BitwayMessage,
    Peer                            = Bitcore.Peer,
    Networks                        = Bitcore.networks;

var ltcRpcConfig = {
    protocol: 'http',
    user: 'user',
    pass: 'pass',
    host: '127.0.0.1',
    port: '19332',
    };

var ltcRedisProxy = new RedisProxy("LTC", "127.0.0.1", "6379");
ltcRedisProxy.start();

var minConfirmNum = 6;
var ltcProxy = new CryptoProxy("ltc", ltcRpcConfig, minConfirmNum,  ltcRedisProxy);

ltcRedisProxy.on(RedisProxy.EventType.GENERATE_ADDRESS, function(currency, request) {
   ltcProxy.generateUserAddress(ltcProxy, request, ltcRedisProxy);
});

ltcRedisProxy.on(RedisProxy.EventType.TRANSFER, function(currency, request) {
    ltcProxy.transfer(ltcProxy, request, ltcRedisProxy);
});

ltcRedisProxy.on(RedisProxy.EventType.GET_MISSED_BLOCKS, function(currency, request) {
    ltcProxy.getMissedBlocks(ltcProxy, request, ltcRedisProxy);
});

function checkTxAndBlock () {
    ltcProxy.checkTx(ltcProxy);
    ltcProxy.checkBlock(ltcProxy);

    //var request = {
    //    num: 1,
    //};
    //ltcProxy.generateUserAddress(ltcProxy, request, ltcRedisProxy);
    //ltcProxy.generateUserAddress(ltcProxy, request, ltcRedisRroxy);

    //var startIndexs = [];
    //var startA = new BlockIndex({id: '00000000000475132295d80ad03f64dcb5ca63d72da1c8fe4cf00b92fb2a4d4c', height:242673});
    //var startB = new BlockIndex({id: '00000000b71ef84d66c0c96df20673001c78842fdca2ae0775b4c8f1e0bea902', height:242674});
    //var endIndex = new BlockIndex({id: '0000000000003b7df09b2bda05ab287bf9c6d0b4f7b4c4c298dcf20c9914bee7', height:242677});  
    //var startA = new BlockIndex({id: '74a95e4a12204f0fbb97048c618cfdde3a94f2c6cbb3c4fa261566f10064f956', height:568717});
    //var startB = new BlockIndex({id: '48425b55e61f1402fb1540f2961f14fec669b73bc52aae8c80663a5f894448d8', height:568718});
    //var endIndex = new BlockIndex({id: '1f01ae47ea309b3f7190f709adff76b38632db5fedad59b0e78c9b77f128802a', height:568723});

    //startIndexs.push(startA);
    //startIndexs.push(startB);
    //var request = new GetMissedCryptoCurrencyBlocks({startIndexs: startIndexs, endIndex: endIndex});
    //console.log(request);
    //console.log("endIndex hash:" + request.endIndex.id); 
    //ltcProxy.getMissedBlocks(ltcProxy, request, ltcRedisProxy);
    //ltcProxy.getMissedBlocks(ltcProxy, request, ltcRedisProxy);

    //var transferInfos = [];
    //var transfer1 = new CryptoCurrencyTransferInfo({id: 0, to: 'mhzTpgNvVSnwesA9MgzcWD6gVEwfpZqZQB',
    //    amount: 0.01, from: 'mqvyjzCuV873EZ3vd3FTkUN1KoopSXmdog'});
    //var transfer2 = new CryptoCurrencyTransferInfo({id: 0, to: 'mqWs5kNcb6W2oiogTWXYXHBip8E4S6NzA5',
    //    amount: 0.01, from: 'n3vJJnkJBRQfwfoN7miz1FfYknk382DCaw'});
    //transferInfos.push(transfer1);
    //transferInfos.push(transfer2);
    //var fromAddresses = [];
    //var toAddress = 'mqWs5kNcb6W2oiogTWXYXHBip8E4S6NzA5';
    //var amount = 0.01;
    //var minConfirmedNum = 6;
    //var maxConfirmedNum = 9999999;
    //var addresses = {};
    //var request = new TransferCryptoCurrency({currency: Currency.ltc,
    //    transferInfos: transferInfos, type:TransferType.WITHDRAWAL});
    //var request = new TransferCryptoCurrency({currency: Currency.ltc,
    //    transferInfos: transferInfos, type:TransferType.USER_TO_HOT});
    //ltcProxy.transfer(ltcProxy, request, ltcRedisProxy);
};

var timer = setInterval(checkTxAndBlock, 5000);

