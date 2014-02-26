/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

var server = require("./server");
var router = require("./router");
var requestHandlers = require("./requestHandlers");

var handle = {}

handle["/"] = requestHandlers.start;
handle["/start"] = requestHandlers.start;
handle["/upload"] = requestHandlers.upload;
handle["/registerUser"] = requestHandlers.registerUser;
handle["/dw"] = requestHandlers.dw;
handle["/placeOrder"] = requestHandlers.placeOrder;
handle["/cancelOrder"] = requestHandlers.cancelOrder;

server.start(router.route, handle);
