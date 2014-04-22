/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

var assert = require("assert");

var Serializer = require('../../../../src/coinport/bitway/serializer/thrift_binary_serializer').ThriftBinarySerializer;
var TestThrift = require('../../../../gen-nodejs/test_types').TestThrift

describe('thrift serializer', function(){
    describe('binary serializer', function(){
        var tt = new TestThrift({test: 1425});
        it('should return bytes array when serializing the TestThrift object', function(done) {
            var serializer = new Serializer();
            serializer.toBinary(tt, function(bytes) {
                assert.equal(8, bytes.length);
                done();
            });
        });

        it('should equals previous object after deserialization', function(done) {
            var serializer = new Serializer();
            serializer.toBinary(tt, function(bytes) {
                var newTT = new TestThrift();
                serializer.fromBinary(newTT, bytes);
                assert.equal(tt.test, newTT.test);
                done();
            });
        });
    })
});
