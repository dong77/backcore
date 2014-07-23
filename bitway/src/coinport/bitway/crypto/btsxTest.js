var Async                         = require('async'),
    Bitcore                       = require('bitcore'),
    Events                        = require('events'),
    Util                          = require("util"),
    Crypto                        = require('crypto'),
    Redis                         = require('redis'),
    DataTypes                     = require('../../../../gen-nodejs/data_types'),
    MessageTypes                  = require('../../../../gen-nodejs/message_types'),
    BitwayMessage                 = MessageTypes.BitwayMessage,
    CryptoCurrencyBlockMessage    = MessageTypes.CryptoCurrencyBlockMessage,
    BitwayResponseType            = DataTypes.BitwayResponseType,
    ErrorCode                     = DataTypes.ErrorCode,
    Currency                      = DataTypes.Currency,
    GenerateAddressesResult       = MessageTypes.GenerateAddressesResult,
    TransferStatus                = DataTypes.TransferStatus,
    CryptoCurrencyAddressType     = DataTypes.CryptoCurrencyAddressType,
    TransferType                  = DataTypes.TransferType,
    BlockIndex                    = DataTypes.BlockIndex,
    CryptoAddress                 = DataTypes.CryptoAddress,
    SyncHotAddressesResult        = MessageTypes.SyncHotAddressesResult;

var CryptoProxy = require('./btsx_proxy').CryptoProxy;
var RpcClient   = require('bitcore').RpcClient;
var dog = {
    currency: Currency.DOGE,
    cryptoConfig: {
        cryptoRpcConfig: {
            protocol: 'http',
            user: 'user',
            pass: 'pass',
            host: '127.0.0.1',
            port: '22555',
        },
        minConfirm: 1,
        checkInterval : 5000,
        walletPassPhrase: "zhouhang"
    },
    redisProxyConfig: {
        currency: Currency.DOGE,
        ip: 'bitway',
        port: '6379',
    }
};
var cryptoConfig = dog.cryptoConfig;
cryptoConfig.cryptoRpc = new RpcClient(cryptoConfig.cryptoRpcConfig);
var cryptoProxy = new CryptoProxy(dog.currency, dog.cryptoConfig);

var addresses = [];
addresses.push('njUFyRxUrD5EgssHhX14Nr31k9piigMwQg');
addresses.push('nhS7fCAjLpq1X5udwNZu51HpouY3m5PeT4');

//cryptoProxy.getUnspentByUserAddresses_(addresses, function(error, txs) {
//    console.log('%j', txs);
//});
//
//
//cryptoProxy.getMinerFeeFromHot_(function(error, txs) {
//    console.log('%j', txs);
//});

//var transferInfos = [];
//var transInfo1 = {from: 'njUFyRxUrD5EgssHhX14Nr31k9piigMwQg', amount: 0.00001};
//var transInfo2 = {from: 'nhS7fCAjLpq1X5udwNZu51HpouY3m5PeT4', amount: 50000};
//transferInfos.push(transInfo1);
//transferInfos.push(transInfo2);
//var request = new Object();
//console.log(1e8);
//request.type = TransferType.USER_TO_HOT;
//request.transferInfos = transferInfos;
//cryptoProxy.constructRawTransaction_(request, function(error, rawData) {
//    console.log('%j', rawData);
//});


var request = new Object();
request.num = 10;
var time1 = (new Date()).getTime();
console.log((new Date()).getTime());
//cryptoProxy.generateNewAccount_(function(error, result) {
//cryptoProxy.generateNewAccountName_(function(error, result) {
//cryptoProxy.generateNewAccount_("customers10", function(error, result) {
//cryptoProxy.getPrivateKeyByAccount_("customers2", function(error, result) {
//cryptoProxy.generateUserAddress(request, function(error, result) {
//cryptoProxy.getKeyByAccountName_("yangli9", function(error, result) {
//cryptoProxy.findUserAccountByKey_("BTSX5hzCPsQpBxd81SznNKMsVKzbd1i8YmyC6mVDFShJDLYC4bh5pA", function(error, result) {
//cryptoProxy.getSigIdByTxId_("4e715691b90ff7a357d30c52f809acb6daae2562", function(error, result) {
//cryptoProxy.walletTransfer_(10, "yangli", "customers2", 100000000, function(error, result) {
//cryptoProxy.getWalletTransactionByIndex_(26646, function(error, result) {
//cryptoProxy.batchRegister_(2000, function(error, result) {
cryptoProxy.synchronousHotAddr(request, function(error, result) {
    console.log("%j", error);
    console.log("%j", result);
    console.log((new Date()).getTime()-time1);
});
