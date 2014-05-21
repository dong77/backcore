/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

'use strict'

var MockData = require('./mock_data').MockData;


var MockRpc = module.exports.MockRpc = function(config) {
    this.fail = config.fail;
    this.struggle = false;
    this.blockCount = config.blockCount;
};

MockRpc.prototype.getNewAddress = function(account, callback) {
    if (this.fail == 'all') {
        callback('fail');
    } else if (this.fail == 'partial') {

        if (this.struggle == true) {
            callback('fail');
            this.struggle = false;
        } else {
            callback(null, {result: 'addr'});
            this.struggle = true;
        }

    } else {
        callback(null, {result: 'addr'});
    }
};

MockRpc.prototype.getBlockCount = function(callback) {
    var self = this;
    callback(null, {result: self.blockCount});
};

MockRpc.prototype.getBlockHash = function(index, callback) {
    callback(null, {result: MockData.heightHash[index]});
};

MockRpc.prototype.getBlock = function(hash, callback) {
    callback(null, {result: MockData.hashBlock[hash]});
};

MockRpc.prototype.listSinceBlock = function(hash, callback) {
    callback(null, {result: MockData.hashSinceTxs[hash]});
};

MockRpc.prototype.getRawTransaction = function(txid, needJson, callback) {
    callback(null, {result: MockData.txidTx[txid]});
};
