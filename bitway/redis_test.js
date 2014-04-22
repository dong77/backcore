/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

var redis   = require('redis'),
    client  = redis.createClient('6379', '127.0.0.1', { return_buffers: true }),
    message = require('./gen-nodejs/message_types'),
    Currency = require('./gen-nodejs/data_types').Currency;

var thrift = require('thrift');
var Serializer = require('./src/coinport/bitway/serializer/thrift_binary_serializer').ThriftBinarySerializer;

var GenerateWallet = message.GenerateWallet

var gw = new GenerateWallet({test: 1425});

var serializer = new Serializer();
/*
serializer.toBinary(gw, function(bytes) {
    console.log(bytes);
    client.rpush("cr", bytes);
});
*/

/*
client.on("error", function(error) {
    console.log(error);
});

client.rpush("hoss", "okko");

var notification = new GenerateWallet({test: 1425})
console.log(notification)
var buffer = new Buffer(notification);
var transport = new thrift.TFramedTransport(buffer, function(buf) {
    console.log("TRANSPORT FLUSHED "); console.log(buf);
});
var binaryProt = new thrift.TBinaryProtocol(transport);
notification.write(binaryProt);
var byteArray = transport.outBuffers
console.log(byteArray)

*/

client.blpop("cr" , 0, function(error, result) {
    if (!error && result) {
        var gw = new GenerateWallet();
        var buf = new Buffer(result[1], 'binary');
        console.log(result);
        console.log(buf);
        serializer.fromBinary(gw, buf);
        console.log(gw);
    } else if (!result) {
        console.log("timeout")
    } else {
        console.log(error)
    }
});
