/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

var exec = require('child_process').exec,
    redis = require('redis'),
    publisher = redis.createClient();

function start(args, response) {
    console.log('Request handler "start" was called.');

    exec('ls -lah', function (error, stdout, stderr) {
        ok(response, stdout);
        console.log('request handler finished');
    });
}

function upload(args, response) {
    console.log('Request handler "upload" was called.');
    ok(response);
}

function ok(response, message) {
    response.writeHead(200, {'Content-Type': 'text/plain'});
    if (typeof(message) == 'undefined')
        message = 'ok';
    response.write(message);
    response.end();
}

function pub(args, ct, response) {
    args['ct'] = ct;
    console.log(args);
    publisher.publish("command", JSON.stringify(args));
    ok(response);
}

function registerUser(args, response) {
    pub(args, 'ru', response);
}

function dw(args, response) {
    pub(args, 'dw', response);
}

function placeOrder(args, response) {
    pub(args, 'po', response);
}

function cancelOrder(args, response) {
    pub(args, 'co', response);
}

exports.start = start;
exports.upload = upload;
exports.registerUser = registerUser;
exports.dw = dw;
exports.placeOrder = placeOrder;
exports.cancelOrder = cancelOrder;
