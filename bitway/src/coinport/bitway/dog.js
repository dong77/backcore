/**
 *Copyright 2014 Coinport Inc. All Rights Reserved.
 *Author: YangLi--ylautumn84@gmail.com
 *Filename: dog.js
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

var dogRpcConfig = {
    protocol: 'http',
    user: 'user',
    pass: 'pass',
    host: '127.0.0.1',
    port: '44555',
    };

var dogRedisProxy = new RedisProxy("dog", "127.0.0.1", "6379");
dogRedisProxy.start();

var minConfirmNum = 1;
var dogProxy = new CryptoProxy(Currency.DOG, dogRpcConfig, minConfirmNum,  dogRedisProxy);

dogRedisProxy.on(RedisProxy.EventType.GENERATE_ADDRESS, function(currency, request) {
   dogProxy.generateUserAddress(dogProxy, request, dogRedisProxy);
});

dogRedisProxy.on(RedisProxy.EventType.TRANSFER, function(currency, request) {
    dogProxy.transfer(dogProxy, request, dogRedisProxy);
});

dogRedisProxy.on(RedisProxy.EventType.GET_MISSED_BLOCKS, function(currency, request) {
    dogProxy.getMissedBlocks(dogProxy, request, dogRedisProxy);
});

function checkTxAndBlock () {
    dogProxy.checkTx(dogProxy);
    dogProxy.checkBlock(dogProxy);

    //var request = {
    //    num: 1,
    //};
    //dogProxy.generateUserAddress(dogProxy, request, dogRedisProxy);
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
    //dogProxy.getMissedBlocks(dogProxy, request, dogRedisProxy);
    //ltcProxy.getMissedBlocks(ltcProxy, request, ltcRedisProxy);

    //var transferInfos = [];
    //var transfer1 = new CryptoCurrencyTransferInfo({id: 311, to: '',
    //    amount: 5, from: 'nqisaGbdUjQuvQCYKky6KMMaGJJ8FA2bt7'});
    //transferInfos.push(transfer1);
    //var request = new TransferCryptoCurrency({currency: Currency.dog,
    //    transferInfos: transferInfos, type:TransferType.WITHDRAWAL});
    //var request = new TransferCryptoCurrency({currency: Currency.dog,
    //    transferInfos: transferInfos, type:TransferType.USER_TO_HOT});
    //dogProxy.transfer(dogProxy, request, dogRedisProxy);
};

var timer = setInterval(checkTxAndBlock, 5000);

