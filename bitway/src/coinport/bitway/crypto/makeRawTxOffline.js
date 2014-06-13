/**
 *Author: yangli - yangli@coinport.com
 *Last modified: 2014-06-13 21:59
 *Filename: makeRawTxOffline.js
 *Copyright 2014 Coinport Inc. All Rights Reserved.
 */
var Bitcore                       = require('bitcore'),
    Crypto                        = require('crypto'),
    DataTypes                     = require('../../../../gen-nodejs/data_types'),
    Currency                      = DataTypes.Currency;

var program = require('commander');
var Async = require('async');
var fs = require('fs');
var CryptoProxy = require('./crypto_proxy').CryptoProxy;
var RpcClient   = require('bitcore').RpcClient;
var dog = {
    cryptoRpcConfig: {
        protocol: 'http',
        user: 'user',
        pass: 'pass',
        host: '127.0.0.1',
        port: '44555',
    },
    minerFee: 1,
};
var btc = {
    cryptoRpcConfig: {
        protocol: 'http',
        user: 'user',
        pass: 'pass',
        host: 'bitway',
        port: '8332',
    },
    minerFee: 0.0001,
};

var minerFee = 0.0001;
var coldAddr = '';
var privateKeys = [];
var destAddr = '';
var amount = 0;
var recieves = [];
var prevTxs = [];
var rpc = new Object();
var timeBegin = new Date().getTime();

var initData_ = function() {
    program.parse(process.argv); 
    var currency = program.args[0]
    coldAddr = program.args[1];
    privateKeys.push(program.args[2]);
    destAddr = program.args[3];
    amount = program.args[4];
    var config = new Object();
    switch (Number(currency)) {
        case Currency.BTC:
            config = btc;
            break;
        case Currency.LTC:
            config = ltc;
            break;
        case Currency.DOG:
            config = dog;
            break;
        default:
            console.log('unknown currency!');
    }
    rpc = new RpcClient(config.cryptoRpcConfig);
    minerFee = config.minerFee;
};

var readFile_ = function() {
    var fileName = './coldWallet/' + coldAddr.toString();
    console.log(fileName);
    fs.readFile(fileName, function(error, data){
        if (!error) {
            if (data.length != 0) {
                var jsonObj = JSON.parse(data);
                console.log('%j', jsonObj);
                if (jsonObj) {
                    height = jsonObj.latestHeight; 
                    recieves = jsonObj.recieves;
                }
            }
        } else {
            console.log(error);
        }
    });
};

var jsonToAmount_ = function(value) {
    return Math.round(1e8 * value)/1e8;
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
            var prevTx = {txid: recieves[i].txid, vout: recieves[i].n, scriptPubKey: recieves[i].hex};
            prevTxs.push(prevTx);
            if (spentAmount > jsonToAmount(Number(amount + minerFee))
                || spentAmount == jsonToAmount(Number(amount + minerFee))) {
                break;
            }
        }
    }
    addresses[destAddr] = Number(amount);
    if (spentAmount > jsonToAmount(Number(amount + minerFee))) {
        addresses[coldAddr] = jsonToAmount_(Number(spentAmount - amount - minerFee));
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
            hexString = createRet.result;
        }
    });
};

var signRawTransaction_ = function() {
    rpc.signRawTransaction(hexString, prevTxs, privateKeys, function(errSign, sign) {
        if (errSign) {
            console.log('%j', errSign);
        } else {
            console.log('%j', sign);
        }
    });

};

Async.auto({
    initData: function(callback) {
        initData_();
    },
    readFile: function(callback) {
        readFile_();
    },
    constructRawData: function(callback) {
        constructRawData_();
    },
    createRawTransaction: function(callback) {
        createRawTransaction_();
    },   
    signRawTransaction: ['createRawTransaction', 'constructRawData', 'readFile', 'initData', function(callback) {
        signRawTransaction_();
    }]
}, function(err, results) {

});
