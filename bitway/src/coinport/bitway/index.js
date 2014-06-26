/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

var CryptoAgentManager = require('./crypto/crypto_agent_manager').CryptoAgentManager,
    DataTypes          = require('../../../gen-nodejs/data_types'),
    Currency           = DataTypes.Currency;
var program = require('commander');
var btc = {
    currency: Currency.BTC,
    cryptoConfig: {
        cryptoRpcConfig: {
            protocol: 'http',
            user: 'user',
            pass: 'pass',
            host: 'bitway',
            port: '18332',
        },
        minConfirm: 1,
        checkInterval : 5000
    },
    redisProxyConfig: {
        currency: Currency.BTC,
        ip: 'bitway',
        port: '6379',
    }
};

var ltc = {
    currency: Currency.LTC,
    cryptoConfig: {
        cryptoRpcConfig: {
            protocol: 'http',
            user: 'user',
            pass: 'pass',
            host: 'bitway',
            port: '19332',
        },
        minConfirm: 1,
        checkInterval : 5000
    },
    redisProxyConfig: {
        currency: Currency.LTC,
        ip: 'bitway',
        port: '6379',
    }
};

var dog = {
    currency: Currency.DOG,
    cryptoConfig: {
        cryptoRpcConfig: {
            protocol: 'http',
            user: 'user',
            pass: 'pass',
            host: 'bitway',
            port: '44555',
        },
        minConfirm: 1,
        checkInterval : 5000
    },
    redisProxyConfig: {
        currency: Currency.DOG,
        ip: 'bitway',
        port: '6379',
    }
};

var drk = {
    currency: Currency.DRK,
    cryptoConfig: {
        cryptoRpcConfig: {
            protocol: 'http',
            user: 'user',
            pass: 'pass',
            host: 'bitway',
            port: '7332',
        },
        minConfirm: 1,
        checkInterval : 5000
    },
    redisProxyConfig: {
        currency: Currency.DRK,
        ip: 'bitway',
        port: '6379',
    }
};
var configs = [ btc, ltc, dog, drk ];
// var configs = [ btc ];
// var configs = [ dog ];
program.parse(process.argv);
if (program.args.lenght != configs.length) {
    console.log("The num of the passwords isn't correct!");
} else {
    for (var i = 0; i < program.args.lenght; i++) {
        configs[i].cryptoConfig.walletPassPhrase = program.args[0];
    }
}
var manager = new CryptoAgentManager(configs);
manager.start();

var logo = "\n" +
" _    _ _                     \n" +
"| |__(_) |___ __ ____ _ _  _  \n" +
"| '_ \\ |  _\\ V  V / _` | || | \n" +
"|_.__/_|\\__|\\_/\\_/\\__,_|\\_, | \n" +
"                        |__/  \n";
console.log(logo);
