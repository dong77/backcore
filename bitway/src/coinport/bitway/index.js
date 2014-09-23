/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

var CryptoAgentManager = require('./crypto/crypto_agent_manager').CryptoAgentManager,
    DataTypes          = require('../../../gen-nodejs/data_types'),
    Currency           = DataTypes.Currency;
var fs = require('fs');
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
        checkInterval : 5000,
        walletPassPhrase: ""
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
        checkInterval : 5000,
        walletPassPhrase: ""
    },
    redisProxyConfig: {
        currency: Currency.LTC,
        ip: 'bitway',
        port: '6379',
    }
};

var dog = {
    currency: Currency.DOGE,
    cryptoConfig: {
        cryptoRpcConfig: {
            protocol: 'http',
            user: 'user',
            pass: 'pass',
            host: 'bitway',
            port: '44555',
        },
        minConfirm: 1,
        checkInterval : 5000,
        walletPassPhrase: ""
    },
    redisProxyConfig: {
        currency: Currency.DOGE,
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
        checkInterval : 5000,
        walletPassPhrase: ""
    },
    redisProxyConfig: {
        currency: Currency.DRK,
        ip: 'bitway',
        port: '6379',
    }
};

var bc = {
    currency: Currency.BC,
    cryptoConfig: {
        cryptoRpcConfig: {
            protocol: 'http',
            user: 'user',
            pass: 'pass',
            host: 'bitway',
            port: '15715',
        },
        minConfirm: 1,
        checkInterval : 5000,
        walletPassPhrase: ""
    },
    redisProxyConfig: {
        currency: Currency.BC,
        ip: 'bitway',
        port: '6379',
    }
};

var vrc = {
    currency: Currency.VRC,
    cryptoConfig: {
        cryptoRpcConfig: {
            protocol: 'http',
            user: 'user',
            pass: 'pass',
            host: 'bitway',
            port: '58683',
        },
        minConfirm: 1,
        checkInterval : 5000,
        walletPassPhrase: ""
    },
    redisProxyConfig: {
        currency: Currency.VRC,
        ip: 'bitway',
        port: '6379',
    }
};

var zet = {
    currency: Currency.ZET,
    cryptoConfig: {
        cryptoRpcConfig: {
            protocol: 'http',
            user: 'user',
            pass: 'pass',
            host: 'bitway',
            port: '6332',
        },
        minConfirm: 1,
        checkInterval : 5000,
        walletPassPhrase: ""
    },
    redisProxyConfig: {
        currency: Currency.ZET,
        ip: 'bitway',
        port: '6379',
    }
};


var btsx = {
    currency: Currency.BTSX,
    cryptoConfig: {
        cryptoRpcConfig: {
            protocol: 'http',
            user: 'test',
            pass: 'test',
            host: '192.168.0.104',
            port: 9989,
        },
        hotAccountName: "",
        minConfirm: 20,
        checkInterval : 5000,
        walletName: "default",
        walletPassPhrase: ""
    },
    redisProxyConfig: {
        currency: Currency.BTSX,
        ip: 'bitway',
        port: '6379',
    }
};


var xrp = {
    currency: Currency.XRP,
    cryptoConfig: {
        hotAccount: "rpX6Sujw8hkK8bBiFqH9oVQdNaogtT81z2",
        checkInterval : 10000,
        secret: "shGbSXqEM3gvtNDKHae21scBBVbRQ",
        walletPassPhrase: ""
    },
    redisProxyConfig: {
        currency: Currency.XRP,
        ip: 'bitway',
        port: '6379',
    }
};

// var configs = [ btc, ltc, dog, drk, bc, vrc, zet ];
// var configs = [ btc ];
var configs = [ xrp ];


fs.readFile('./pw', function(error, data){
    if (!error) {
        if (data.length != 0) {
            var pw = JSON.parse(data);
            var password = pw.testPw;
            if (password && password.length > 7) {
                console.log('use password');
                for (var i = 0; i < configs.length; i++) {
                    configs[i].cryptoConfig.walletPassPhrase = password;
                }
            } else {
                console.log("Password isn't correct!");
                console.log("node index.js [password]");
                process.exit(0);
            }
        } else {
            console.log("data.length == 0");
            console.log("Password isn't correct!");
            console.log("node index.js [password]");
            process.exit(0);
        }
    } else {
        console.log('error %j', error);
        console.log("Password isn't correct!");
        console.log("node index.js [password]");
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
});
