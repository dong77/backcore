/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

var Thrift = require('thrift');

var ThriftBinarySerializer = module.exports.ThriftBinarySerializer = function() {};

ThriftBinarySerializer.prototype.toBinary = function(obj, callback) {
    var transport = new Thrift.TFramedTransport(null, function(bytes) {
        bytes = bytes.slice(4);
        callback(bytes)
    });
    var binaryProt = new Thrift.TBinaryProtocol(transport);
    obj.write(binaryProt);
    transport.flush();
};

ThriftBinarySerializer.prototype.fromBinary = function(obj, bytes) {
    var transport = new Thrift.TFramedTransport(bytes);
    var protocol = new Thrift.TBinaryProtocol(transport);
    obj.read(protocol);
};

ThriftBinarySerializer.prototype.fromBuffers = function(obj, buffers) {
    this.fromBinary(obj, Buffer.concat(buffers));
};
