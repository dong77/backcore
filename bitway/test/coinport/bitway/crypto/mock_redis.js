/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

'use strict'

var MockRedis = module.exports.MockRedis = function() {
    this.map = {};
};

MockRedis.prototype.smembers = function(key, callback) {
    callback(null, this.map[key] ? this.map[key] : []);
};

MockRedis.prototype.sadd = function(key, values, callback) {
    var prev = this.map[key] ? this.map[key] : [];
    var newSet = prev.concat(values);
    this.map[key] = newSet;
    callback();
};

MockRedis.prototype.get = function(key, callback) {
    callback(null, this.map[key]);
};

MockRedis.prototype.set = function(key, val, callback) {
    this.map[key] = val;
    callback();
};

MockRedis.prototype.del = function(key, callback) {
    this.map[key] = undefined;
    callback();
};

MockRedis.prototype.on = function(e, callback) {
};
