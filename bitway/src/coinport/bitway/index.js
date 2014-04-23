/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

var RedisProxy = require('./redis/redis_proxy').RedisProxy;

var proxy = new RedisProxy("127.0.0.1", "6379");

proxy.on(RedisProxy.EventType.GENERATE_WALLET, function(requestId, currency, request) {
    console.log(RedisProxy.EventType.GENERATE_WALLET);
    console.log(requestId);
    console.log(currency);
    console.log(request);
});

proxy.on(RedisProxy.EventType.TRANSFER, function(requestId, currency, request) {
    console.log(RedisProxy.EventType.TRANSFER);
    console.log(requestId);
    console.log(requestId === 123);
    console.log(currency);
    console.log(request);
});

proxy.on(RedisProxy.EventType.QUERY_WALLET, function(requestId, currency, request) {
    console.log(RedisProxy.EventType.QUERY_WALLET);
    console.log(requestId);
    console.log(currency);
    console.log(request);
});

proxy.start();

var logo = "" +
" _    _ _                     \n" +
"| |__(_) |___ __ ____ _ _  _  \n" +
"| '_ \\ |  _\\ V  V / _` | || | \n" +
"|_.__/_|\\__|\\_/\\_/\\__,_|\\_, | \n" +
"                        |__/  \n";
console.log(logo);
