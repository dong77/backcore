/**
 *Author: yangli - yangli@coinport.com
 *Last modified: 2014-06-13 21:59
 *Filename: makeRawTxOffline.js
 *Copyright 2014 Coinport Inc. All Rights Reserved.
 */
var Bitcore                       = require('bitcore'),
    Crypto                        = require('crypto'),
    DataTypes                     = require('../../../../gen-nodejs/data_types');

var program = require('commander');
var Async = require('async');
var fs = require('fs');
var RpcClient   = require('bitcore').RpcClient;
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

var ltc = {
    cryptoRpcConfig: {
        protocol: 'http',
        user: 'user',
        pass: 'pass',
        host: '127.0.0.1',
        port: '9332',
    },
    minerFee: 0.0001,
};


var dog = {
    cryptoRpcConfig: {
        protocol: 'http',
        user: 'user',
        pass: 'pass',
        host: '127.0.0.1',
        port: '44555',
    },
    minerFee: 0.0001,
};
var minerFee = 0.0001;
var coldAddr = '';
var privateKeys = [];
var destAddr = '';
var amount = 0;
var recieves = [];
var transactions = [];
var addresses = {};
var prevTxs = [];
var rpc = new Object();
var timeBegin = new Date().getTime();

var initData_ = function(callback) {
    program.parse(process.argv); 
    console.log("%j", program);
    var currency = program.args[0]
    coldAddr = program.args[1];
    privateKeys.push(program.args[2]);
    destAddr = program.args[3];
    amount = program.args[4];
    var config = new Object();
    switch (currency) {
        case 'btc':
            config = btc;
            break;
        case 'ltc':
            config = ltc;
            break;
        case 'dog':
            config = dog;
            break;
        default:
            console.log('unknown currency!');
    }
    rpc = new RpcClient(config.cryptoRpcConfig);
    minerFee = config.minerFee;
    callback();
};

var readFile_ = function(callback) {
    var fileName = './coldWallet/' + coldAddr.toString();
    console.log(fileName);
    fs.readFile(fileName, function(error, data){
        if (!error) {
            if (data.length != 0) {
                var jsonObj = JSON.parse(data);
                console.log('raw data in file: %j', jsonObj);
                if (jsonObj) {
                    height = jsonObj.txHistory.latestHeight; 
                    recieves = jsonObj.txHistory.recieves;
                }
            }
            callback();
        } else {
            console.log(error);
            callback(error);
        }
    });
};

var jsonToAmount_ = function(value) {
    return Math.round(1e8 * value)/1e8;
};

var constructRawData_ = function(callback) {
    var spentAmount = 0;
    for (var i = 0; i < recieves.length; i++) {
        if (recieves[i].unSpent) {
            spentAmount += recieves[i].value;
            var transaction = {txid: recieves[i].txid, vout: recieves[i].n};
            transactions.push(transaction);
            var prevTx = {txid: recieves[i].txid, vout: recieves[i].n, scriptPubKey: recieves[i].hex};
            prevTxs.push(prevTx);
            if (spentAmount > jsonToAmount_(Number(amount) + Number(minerFee))
                || spentAmount == jsonToAmount_(Number(amount) + Number(minerFee))) {
                break;
            }
        }
    }
    console.log('spentAmout:', spentAmount);
    console.log('minerFee:', minerFee);
    addresses[destAddr] = Number(amount);
    if (spentAmount > jsonToAmount_(Number(amount) + Number(minerFee))) {
        addresses[coldAddr] = jsonToAmount_(Number(spentAmount) - Number(amount) - Number(minerFee));
    }
    var rawData = {transactions: transactions, addresses: addresses};
    callback();
    return rawData;
}

var createRawTransaction_ = function(callback) {
    console.log("transactions %j", transactions);
    console.log("addresses %j", addresses);
    rpc.createRawTransaction(transactions, addresses, function(errCreate, createRet) {
        if (errCreate) {
            console.log("errCreate: %j", errCreate);
            callback(errCreate);
        } else {
            console.log('%j', createRet);
            hexString = createRet.result;
            callback();
        }
    });
};

var signRawTransaction_ = function(callback) {
    console.log("hexString:", hexString);
    console.log("prevTxs:", prevTxs);
    rpc.signRawTransaction(hexString, prevTxs, privateKeys, function(errSign, sign) {
        if (errSign) {
            callback(errSign);
            console.log('%j', errSign);
        } else {
            console.log('sign %j', sign);
            callback();
        }
    });
};

Async.auto({
    initData: function(callback) {
        initData_(callback);
    },
    readFile: ['initData', function(callback) {
        readFile_(callback);
    }],
    constructRawData: ['readFile', function(callback) {
        constructRawData_(callback);
    }],
    createRawTransaction: ['constructRawData', function(callback) {
        createRawTransaction_(callback);
    }],   
    signRawTransaction: ['createRawTransaction', function(callback) {
        signRawTransaction_(callback);
    }]
}, function(err, results) {
    console.log(err);
});
