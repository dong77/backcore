/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

'use strict'
var MockRedis = require('./mock_redis').MockRedis,
    Assert    = require('assert');

describe('mock redis', function() {
    describe('smembers after sadd', function() {
        it('get members which just added', function(done) {
            var redis = new MockRedis();
            redis.sadd('test', [1, 2, 3, 4], function() {
                redis.smembers('test', function(error, members) {
                    Assert.deepEqual(members, [1, 2, 3, 4]);
                    done();
                });
            });
        });
    });
});
