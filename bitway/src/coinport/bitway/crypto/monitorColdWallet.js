/**
 *Author: yangli - yangli@coinport.com
 *Last modified: 2014-06-12 20:39
 *Filename: monitorColdWallet.js
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
    height: 112750,
};
var btc = {
    cryptoRpcConfig: {
        protocol: 'http',
        user: 'user',
        pass: 'pass',
        host: 'bitway',
        port: '8332',
    },
    height: 305380,
};

var height = 0;
var addr = '';
var destAddr = '';
var amount = 0;
var recieves = [];
var latest = 0;
var rpc = new Object();
var timeBegin = new Date().getTime();

var initData_ = function() {
    program.parse(process.argv); 
    var currency = program.args[0]
    addr = program.args[1];
    destAddr = program.args[2];
    amount = program.args[3];
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
    height = config.height;
};

var readFile_ = function() {
    var fileName = './coldWallet/' + addr.toString();
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

var writeFile_ = function() {
    var fileData = {latestHeight: latest, recieves: recieves};
    var str = JSON.stringify(fileData);
    var fileName = './coldWallet/' + addr.toString();
    console.log(fileName);
    fs.writeFile(fileName, str, function(error) {
        if (error) {
            console.log(error);
        }
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
                        value: tx.vout[i].value,
                        hex: tx.vout[i].scriptPubKey.hex,
                        unSpent: true};
                    recieves.push(recv);
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
            if (spentAmount > amount) {
                break;
            }
        }
    }
    addresses[destAddr] = Number(amount);
    if (spentAmount > amount) {
        addresses[addr] = jsonToAmount_(Number(spentAmount - amount));
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
            console.log('height: ' + height);
            callback(true);
        }
    );
};

var getData_ = function() {
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
};

Async.auto({
    initData: function(callback) {
        initData_();
    },
    readFile: function(callback) {
        readFile_();
    },
    getData: function(callback) {
        getData_();
    },
    writeFile: ['getData', 'readFile', 'initData', function(callback) {
        writeFile_();
    }]
}, function(err, results) {

});
