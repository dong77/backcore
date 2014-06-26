/**
 *Author: yangli - yangli@coinport.com
 *Last modified: 2014-06-12 20:39
 *Filename: monitorColdWallet.js
 *Copyright 2014 Coinport Inc. All Rights Reserved.
 */
var Bitcore                       = require('bitcore');

var program = require('commander');
var Async = require('async');
var fs = require('fs');
var RpcClient   = require('bitcore').RpcClient;
var dog = {
    cryptoRpcConfig: {
        protocol: 'http',
        user: 'user',
        pass: 'pass',
        host: '127.0.0.1',
        port: '44555',
    },
    height: 117750,
};
var btc = {
    cryptoRpcConfig: {
        protocol: 'http',
        user: 'user',
        pass: 'pass',
        host: 'bitway',
        port: '8332',
    },
    height: 306050,
};

var height = 0;
var currency = '';
var addr = '';
var destAddr = '';
var amount = 0;
var recieves = [];
var spentRecs = [];
var latest = 0;
var rpc = new Object();
var timeBegin = new Date().getTime();

var initData_ = function(callback) {
    program.parse(process.argv); 
    currency = program.args[0];
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
    height = config.height;
    callback();
};

var readHistoryFile_ = function(address, callback) {
    var fileName = './coldWallet/' + address;
    console.log(fileName);
    fs.readFile(fileName, function(error, data){
        var defaultData = {latestHeight: 0, unspentAmount: 0, spentAmount: 0, recieves: []};
        var record = {address: address, txHistory: defaultData};
        if (error) {
            console.log(error);
        } else {
            var jsonData = JSON.parse(data);
            record = jsonData;
        }
        callback(null, record);
    });
};


var readFile_ = function(callback) {
    var fileName = './coldWallet/' + currency;
    console.log(fileName);
    fs.readFile(fileName, function(error, data){
        if (!error) {
            if (data.length != 0) {
                var coldAddrs = JSON.parse(data);
                console.log('%j', coldAddrs);
                if (coldAddrs) {
                    currency = coldAddrs.currency;
                    addresses = coldAddrs.addresses;
                    Async.map(coldAddrs.addresses, readHistoryFile_, function(err, records) {
                        console.log('records: %j', records);
                        spentRecs = records;
                        for (var i = 0; i < records.length; i++) {
                            if (records[i].txHistory && records[i].txHistory.latestHeight) {
                                console.log(height);
                                console.log(records[i].txHistory.latestHeight);
                                height = records[i].txHistory.latestHeight;
                                
                            }
                        }
                        callback();
                    });
                }
            }
        } else {
            console.log(error);
            callback(error);
        }
    });
};

var countTotalAmount_ = function(record) {
    record.txHistory.unspentAmount = 0;
    record.txHistory.spentAmount = 0;
    if (record.txHistory.recieves) {
        for (var i = 0; i < record.txHistory.recieves.length; i++)
        {
            if (record.txHistory.recieves[i].unSpent) {
                record.txHistory.unspentAmount += jsonToAmount_(Number(record.txHistory.recieves[i].value));
            } else {
                record.txHistory.spentAmount += jsonToAmount_(Number(record.txHistory.recieves[i].value));
            }

        }
    }
    return record;
}

var writeHistoryFile_ = function(record, callback) {
    var fileName = './coldWallet/' + record.address;
    record.txHistory.latestHeight = latest;
    console.log('write file: ', fileName);
    var str = JSON.stringify(countTotalAmount_(record));
    console.log('record: %j', str);
    fs.writeFile(fileName, str, function(error) {
        if (error) {
            console.log(error);
            callbcak(error, null);
        } else {
            callback();
        }
    });
};

var writeFile_ = function(callback) {
    Async.map(spentRecs, writeHistoryFile_, function(error, results) {
        if (error) {
            console.log(error);
            callback(error);
        } else {
            callback(null, results);
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
                for (var k = 0; k < spentRecs.length; k++) {
                    if (tx.vout[i].scriptPubKey.addresses[j] == spentRecs[k].address) {
                        console.log('txid: ' + tx.txid + ', n: ' + tx.vout[i].n);
                        var recv = {txid: tx.txid, n: tx.vout[i].n,
                            value: tx.vout[i].value,
                            hex: tx.vout[i].scriptPubKey.hex,
                            unSpent: true};
                        spentRecs[k].txHistory.recieves.push(recv);
                    } else {
                    }
                }
            }
        }
    }
};

var ifATxUnspent_ = function(tx) {
    for (var m = 0; m < tx.vin.length; m++) {
        for (var n = 0; n < spentRecs.length; n++) {
            if (spentRecs[n].txHistory.recieves) {
                for (var q = 0; q < spentRecs[n].txHistory.recieves.length; q++)
                {
                    if (tx.vin[m].txid == spentRecs[n].txHistory.recieves[q].txid
                        && tx.vin[m].vout == spentRecs[n].txHistory.recieves[q].n) {
                        spentRecs[n].txHistory.recieves[q].unSpent = false;
                        console.log('already used: txid(' + spentRecs[n].txHistory.recieves[q].txid + ')' 
                            + ', n: ' + spentRecs[n].txHistory.recieves[q].n);
                    }
                }
            }
        }
    }
};

var jsonToAmount_ = function(value) {
    return Math.round(1e8 * value)/1e8;
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

var getTx_ = function(tx, callback) {
     rpc.getRawTransaction(tx, 1, function(errTx, txRet) {
         if (errTx) {
             callback(error);
         } else {
             callback(null, txRet.result);
         }
     });
}


var checkTxs_ = function(block, callback) {
    var txCount = 0;
    if (block.tx.length) {
        Async.map(block.tx, getTx_, function(error, txs) {
            console.log('height: ' + height);
            for (var i = 0; i < txs.length; i++) {
                ifATxBelongToAddr_(txs[i]);
                ifATxUnspent_(txs[i]);
            }
            if (height%50 == 0) {
               writeFile_(function(errWrite, results) {
                   if (errWrite) {
                       console.log('errWrite: %j', errWrite);
                   } else {

                   }
                   callback(true);
               }); 
            } else {
                callback(true);
            }
        });
    }
};

var getData_ = function(callback) {
    rpc.getBlockCount(function(errCount, count) {
        if (errCount) {
            console.log('%j', errCount);
        } else {
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
                    callback();
                    console.log('Total time: ' + (new Date().getTime() - timeBegin));
                }
            );
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
    getData: ['readFile', function(callback) {
        getData_(callback);
    }],
    writeFile: ['getData', function(callback) {
        writeFile_(callback);
    }]
}, function(err, results) {

});
