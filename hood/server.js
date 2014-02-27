/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

var http = require('http');
var url = require('url');

function start(route, handle) {
    function onRequest(request, response) {
        // var parser = url.parse(request.url, true);  \\ will parse query as object
        var parser = url.parse(request.url);
        var pathname = parser.pathname;
        // console.log('Request for ' + pathname + ' received.');

        var args = parser.query;

        route(handle, pathname, args, response);
    }

    http.createServer(onRequest).listen(8888);
    console.log('Server has started.');
}

exports.start = start;
