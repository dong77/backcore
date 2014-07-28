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
var btsx = {
    currency: Currency.BTSX,
    cryptoConfig: {
        cryptoRpcConfig: {
            protocol: 'http',
            user: 'test',
            pass: 'test',
            host: 'bitway',
            port: 9989,
        },
        hotAccountName: "yangli",
        minConfirm: 20,
        checkInterval : 5000,
        walletName: "default",
        walletPassPhrase: "coinport"
    },
    redisProxyConfig: {
        currency: Currency.BTSX,
        ip: 'bitway',
        port: '6379',
    }
};
var cryptoProxy = new CryptoProxy(btsx.currency, btsx.cryptoConfig);

var time1 = (new Date()).getTime();
console.log((new Date()).getTime());
var request = null;
var transferInfo = new Object({id: 6000001, to: "alwayslater"});
//cryptoProxy.synchronousHotAddr(request, function(error, result) {
cryptoProxy.addWithdrawalAccount_(transferInfo, function(error, result) {
    console.log("%j", error);
    console.log("%j", result);
    console.log((new Date()).getTime()-time1);
});
