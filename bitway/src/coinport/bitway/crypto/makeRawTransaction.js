/**
 *Author: yangli - yangli@coinport.com
 *Last modified: 2014-06-12 20:39
 *Filename: makeRawTransaction.js
 *Copyright 2014 Coinport Inc. All Rights Reserved.
 */
var Bitcore                       = require('bitcore'),
    Crypto                        = require('crypto'),
    Redis                         = require('redis'),
    DataTypes                     = require('../../../../gen-nodejs/data_types'),
    MessageTypes                  = require('../../../../gen-nodejs/message_types'),
    BitwayMessage                 = MessageTypes.BitwayMessage,
    CryptoCurrencyBlockMessage    = MessageTypes.CryptoCurrencyBlockMessage,
    BitwayResponseType            = DataTypes.BitwayResponseType,
    Currency                      = DataTypes.Currency,
    BlockIndex                    = DataTypes.BlockIndex,
    CryptoAddress                 = DataTypes.CryptoAddress;

var program = require('commander');
var Async = require('async');
var fs = require('fs');
var CryptoProxy = require('./crypto_proxy').CryptoProxy;
var RpcClient   = require('bitcore').RpcClient;
var dog = {
    currency: Currency.DOG,
    cryptoConfig: {
        cryptoRpcConfig: {
            protocol: 'http',
            user: 'user',
            pass: 'pass',
            host: '127.0.0.1',
            port: '44555',
        },
        minConfirm: 1,
        checkInterval : 5000,
        dataFile: './dogData'
    },
    redisProxyConfig: {
        currency: Currency.DOG,
        ip: 'bitway',
        port: '6379',
    }
};
var btc = {
    currency: Currency.BTC,
    cryptoConfig: {
        cryptoRpcConfig: {
            protocol: 'http',
            user: 'user',
            pass: 'pass',
            host: 'bitway',
            port: '8332',
        },
        height: 305380,
        checkInterval : 5000,
        dataFile: './btcData'
    },
    redisProxyConfig: {
        currency: Currency.BTC,
        ip: 'bitway',
        port: '6379',
    }
};
var cryptoConfig = btc.cryptoConfig;
var height = cryptoConfig.height;
var dataFile = cryptoConfig.dataFile;
//var addr = 'nhS7fCAjLpq1X5udwNZu51HpouY3m5PeT4';
program.parse(process.argv); 
var addr = program.args[0];
var destAddr = program.args[1];
var amount = program.args[2];
var recieves = [];
var latest = 0;
var timeBegin = new Date().getTime();
rpc = new RpcClient(cryptoConfig.cryptoRpcConfig);

var readFile_ = function() {
    fs.readFile(dataFile, function(err, data){
        console.log(data.length);
        if (data.length != 0) {
            var jsonObj = JSON.parse(data);
            if (jsonObj) {
                height = jsonObj.latestHeight; 
                recieves = jsonObj.recieves;
            }
        }
    });
};

var writeFile_ = function() {
    var fileData = {latestHeight: latest, recieves: recieves};
    var str = JSON.stringify(fileData);
    fs.writeFile(dataFile, str, function(error) {

    });
};

var getLatestHeight_ = function() {
    rpc.getBlockCount(function(errHeight, height) {
        if (errHeight) {
            console.log('errHeight: ', errHeight);
        } else {
            latestHeight = height.result;
        }
    });
};


var ifATxBelongToAddr_ = function(tx) {
    for (var i = 0; i < tx.vout.length; i++) {
        if (tx.vout[i].scriptPubKey.addresses != undefined) {
            for (var j = 0; j < tx.vout[i].scriptPubKey.addresses.length; j++) {
                if (tx.vout[i].scriptPubKey.addresses[j] == addr) {
                    console.log('txid: ' + tx.txid + ', n: ' + tx.vout[i].n);
                    var recv = {txid: tx.txid, n: tx.vout[i].n,
                        value: tx.vout[i].value, unSpent: true};
                    recieves.push(recv);
                    console.log('** %j', recieves);
                } else {
                }
            }
        }
    }
};

var ifATxUnspent_ = function(tx) {
    for (var m = 0; m < tx.vin.length; m++) {
        for (var n = 0; n < recieves.length; n++) {
            if (tx.vin[m].txid == recieves[n].txid
                && tx.vin[m].vout == recieves[n].n) {
                recieves[n].unSpent = false;
                console.log('already used: txid(' + recieves[n].txid + ')' + ', n: ' + recieves[n].n);
            }
        }
    }
};

var constructRawData_ = function() {
    var transactions = [];
    var addresses = {};
    var spentAmount = 0;
    for (var i = 0; i < recieves.length; i++) {
        if (recieves[i].unSpent) {
            spentAmount += recieves[i].value;
            var transaction = {txid: recieves[i].txid, vout: recieves[i].n};
            transactions.push(transaction);
            if (spentAmount > amount) {
                break;
            }
        }
    }
    addresses[destAddr] = Number(amount);
    if (spentAmount > amount) {
        addresses[addr] = Number(spentAmount - amount);
    }
    var rawData = {transactions: transactions, addresses: addresses};
    console.log(transactions);
    console.log(addresses);
    return rawData;
}

var createRawTransaction_ = function(transactions, addresses) {
    rpc.createRawTransaction(transactions, addresses, function(errCreate, createRet) {
        if (errCreate) {
        } else {
            console.log('%j', createRet);
        }
    });
};

//Async.auto({
//    readFile: function(callback) {
//        readFile_();
//    },
//    getLatestCount: function(callback) {
//        getLatestHeight_();
//    },
//    initData: ['getLatestCount', 'readFile', function(callback) {
//        initData_();
//    }],
//    getData: function(callback) {
//        getDate_();
//    },
//    writeFile: ['getData', 'initData', function(callback) {
//        writeFile_();
//    }]
//}, function(err, results) {
//
//});
var getBlockHash_ = function(index, callback) {
    rpc.getBlockHash(index, function(error, hash) {
        if (error) {                                                            
            callback(error);                                                    
        } else {                                                                
               callback(null, {'index': index, 'hash': hash.result});              
        }                                                                       
    });                                                                         
};                  

var getBlock_ = function(hash, callback) {
    rpc.getBlock(hash.hash, function(error, block) {
        if (error) {                                                            
            callback(error);                                                    
        } else {                                                                
            callback(null, block.result);              
        }
    });
};

var checkTxs_ = function(block, callback) {
    var txCount = 0;
    Async.whilst(
        function() {
            return (txCount < block.tx.length)},
        function(cb1) {
            rpc.getRawTransaction(block.tx[txCount++], 1, function(errTx, txRet) {
                ifATxBelongToAddr_(txRet.result);
                ifATxUnspent_(txRet.result);
                setTimeout(cb1, 0);
            });
        },
        function(err1) {
            console.log('inner height: ' + height);
            //console.log('inner %j', recieves);
            callback(true);
        }
    );
};

//var getData_ = function() {
    rpc.getBlockCount(function(errCount, count) {
        var latestHeight = count.result;
        latest = latestHeight;
        Async.whilst(
            function() {
                return (height < latestHeight)},
            function(cb) {
                Async.compose(checkTxs_,
                    getBlock_,
                    getBlockHash_)(height++, function(errInner) {
                    setTimeout(cb, 0);
                });
            },
            function(error) {
                console.log('%j', recieves);
                var rawData = constructRawData_();
                console.log('rawData %j', rawData);
                createRawTransaction_(rawData.transactions, rawData.addresses);
                writeFile_();
                console.log('Total time: ' + (new Date().getTime() - timeBegin));
            }
        );
    });
//};
