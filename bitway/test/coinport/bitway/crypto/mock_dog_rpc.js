/**
 *Copyright 2014 Coinport Inc. All Rights Reserved.
 *Author: YangLi--ylautumn84@gmail.com
 *Filename: mock_dog_rpc.js
 *Description: 
 */

'use strict'

var MockDogData = require('./mock_dog_data').MockDogData;


var MockDogRpc = module.exports.MockDogRpc = function(config) {
    this.blockCount = config.blockCount;
};

MockDogRpc.prototype.getBlockCount = function(callback) {
    var self = this;
    callback(null, {result: self.blockCount});
};

MockDogRpc.prototype.getBlockHash = function(index, callback) {
    callback(null, {result: MockDogData.heightHash[index]});
};

MockDogRpc.prototype.getBlock = function(hash, callback) {
    callback(null, {result: MockDogData.hashBlock[hash]});
};

MockDogRpc.prototype.listSinceBlock = function(hash, callback) {
    callback(null, {result: MockDogData.hashSinceTxs[hash]});
};

MockDogRpc.prototype.getRawTransaction = function(txid, needJson, callback) {
    callback(null, {result: MockDogData.txidTx[txid]});
};

MockDogRpc.prototype.getAddressesByAccount = function(account, callback) {
    callback(null, {result: MockDogData.addressesByAccount[account]});
};

MockDogRpc.prototype.listUnspent = function(minConfirm, maxConfirm, addrArray, callback) {
    callback(null, {result: MockDogData.listUnspent[addrArray.toString()]});
};
