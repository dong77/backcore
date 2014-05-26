/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

var CryptoAgent = require('./crypto_agent').CryptoAgent,
    CryptoProxy = require('./crypto_proxy').CryptoProxy;

var CryptoAgentManager = module.exports.CryptoAgentManager = function(configs) {
    this.agents = [];
    for (var i = 0; i < configs.length; ++i) {
        var config = configs[i];
        var redisProxy = config.redisProxy;
        var cryptoProxy = new CryptoProxy(config.currency, config.cryptoConfig);
        this.agents.push(new CryptoAgent(cryptoProxy, redisProxy));
    }
};

CryptoAgentManager.prototype.start = function() {
    for (var i = 0; i < this.agents.length; ++i)
        this.agents[i].start();
};
