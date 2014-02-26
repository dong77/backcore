/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

var exec = require('child_process').exec,
    bp = require('./gen-nodejs/bp_types'),
    redis = require('redis'),
    publisher = redis.createClient();

var BPCommand = bp.BPCommand,
    UserInfo = bp.UserInfo,
    DWInfo = bp.DWInfo,
    OrderInfo = bp.OrderInfo,
    BPCommandType = bp.BPCommandType,
    BOS = bp.BOS,
    CoinType = bp.CoinType,
    DOW = bp.DOW,
    TradePair = bp.TradePair;

function start(args, response) {
    console.log('Request handler "start" was called.');

    exec('ls -lah', function (error, stdout, stderr) {
        render(response, stdout);
        console.log('request handler finished');
    });
}

function upload(args, response) {
    console.log('Request handler "upload" was called.');
    render(response);
}

function render(response, message, isFail) {
    if (typeof(message) == 'undefined')
        if (isFail)
            message = 'fail';
        else
            message = 'ok';
    if (isFail)
        response.writeHead(500, {'Content-Type': 'text/plain'});
    else
        response.writeHead(200, {'Content-Type': 'text/plain'});

    response.write(message);
    response.end();
}

function pub(obj, response) {
    publisher.publish("command", JSON.stringify(obj));
    render(response);
}

function registerUser(args, response) {
    var userinfo = new UserInfo({
        nickname : args.name,
        password : args.pw
    });

    var command = new BPCommand({
        type : BPCommandType.REGISTER_USER,
        userInfo : userinfo
    });

    pub(command, response);
}

function dw(args, response) {
    var dwinfo = new DWInfo({
        uid : args.uid,
        dwtype : args.dw,
        coinType : args.coin,
        amount : args.amount
    });

    var command = new BPCommand({
        type : BPCommandType.DW,
        dwInfo : dwinfo
    });

    pub(command, response);
}

function placeOrder(args, response) {
    var orderinfo = new OrderInfo({
        uid : args.uid,
        tradePair : new TradePair({
            from : args.f,
            to : args.t
        }),
        quantity : args.q,
        bos : args.bos,
        price : args.p
    });

    var command = new BPCommand({
        type : BPCommandType.PLACE_ORDER,
        orderInfo : orderinfo
    });

    pub(command, response);
}

function cancelOrder(args, response) {
    var orderinfo = new OrderInfo({
        id : args.id,
        uid : args.uid,
        tradePair : new TradePair({
            from : args.f,
            to : args.t
        })
    });

    var command = new BPCommand({
        type : BPCommandType.CANCEL_ORDER,
        orderInfo: orderinfo
    });

    pub(command, response);
}

exports.start = start;
exports.upload = upload;
exports.registerUser = registerUser;
exports.dw = dw;
exports.placeOrder = placeOrder;
exports.cancelOrder = cancelOrder;
