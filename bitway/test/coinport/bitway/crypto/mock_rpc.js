/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

'use strict'

var MockRpc = module.exports.MockRpc = function(config) {
    this.fail = config.fail;
    this.struggle = false;
};

MockRpc.prototype.getNewAddress = function(account, callback) {
    if (this.fail == 'all') {
        callback('fail');
    } else if (this.fail == 'partial') {

        if (this.struggle == true) {
            callback('fail');
            this.struggle = false;
        } else {
            callback(null, {result: 'addr'});
            this.struggle = true;
        }

    } else {
        callback(null, {result: 'addr'});
    }
};
