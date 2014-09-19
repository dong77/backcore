/**
 *Author: yangli - yangli@coinport.com
 *Last modified: 2014-07-19 12:58
 *Filename: btsx_proxy.js
 *Copyright 2014 Coinport Inc. All Rights Reserved.
 */

'use strict'

var Async                         = require('async'),
    Bitcore                       = require('bitcore'),
    Events                        = require('events'),
    Util                          = require("util"),
    Crypto                        = require('crypto'),
    Redis                         = require('redis'),
    DataTypes                     = require('../../../../gen-nodejs/data_types'),
    MessageTypes                  = require('../../../../gen-nodejs/message_types'),
    BitwayMessage                 = MessageTypes.BitwayMessage,
    CryptoCurrencyBlockMessage    = MessageTypes.CryptoCurrencyBlockMessage,
    BitwayResponseType            = DataTypes.BitwayResponseType,
    ErrorCode                     = DataTypes.ErrorCode,
    GenerateAddressesResult       = MessageTypes.GenerateAddressesResult,
    TransferStatus                = DataTypes.TransferStatus,
    CryptoCurrencyAddressType     = DataTypes.CryptoCurrencyAddressType,
    Logger                        = require('../logger'),
    TransferType                  = DataTypes.TransferType,
    BlockIndex                    = DataTypes.BlockIndex,
    CryptoAddress                 = DataTypes.CryptoAddress,
    SyncHotAddressesResult        = MessageTypes.SyncHotAddressesResult,
    http                          = require('http');
var request = require('request');

/**
 * Handle the crypto currency network event
 * @param {Currency} currency The handled currency type
 * @param {Map{...}} opt_config The config for CryptoProxy
 *     {
 *       cryptoRpc: xxx,
 *       checkInterval: 5000,
 *       redis: xxx,
 *       minConfirm: 1
 *     }
 * @constructor
 * @extends {Events.EventEmitter}
 */
var CryptoProxy = module.exports.CryptoProxy = function(currency, opt_config) {
    Events.EventEmitter.call(this);

    if (opt_config) {
        opt_config.redis != undefined && (this.redis = opt_config.redis);
        opt_config.checkInterval != undefined && (this.checkInterval = opt_config.checkInterval);
        opt_config.minerfee != undefined && (self.minerFee = opt_config.minerFee);
        opt_config.hotAccount != undefined && (this.hotAccount = opt_config.hotAccount);
        opt_config.secret != undefined && (this.secret = opt_config.secret);
    }

    this.currency || (this.currency = currency);
    this.redis || (this.redis = Redis.createClient('6379', '127.0.0.1'));
    this.redis.on('connect'     , this.logFunction('connect'));
    this.redis.on('ready'       , this.logFunction('ready'));
    this.redis.on('reconnecting', this.logFunction('reconnecting'));
    this.redis.on('error'       , this.logFunction('error'));
    this.redis.on('end'         , this.logFunction('end'));

    this.checkInterval || (this.checkInterval = 5000);
    this.processedSigids = this.currency + '_processed_sigids';
    this.lastIndex = this.currency + '_last_index';
    this.log = Logger.logger(this.currency.toString());
    this.hotAccountName = opt_config.hotAccountName;
};

Util.inherits(CryptoProxy, Events.EventEmitter);

CryptoProxy.EventType = {
    TX_ARRIVED : 'tx_arrived',
    BLOCK_ARRIVED : 'block_arrived',
    HOT_ADDRESS_GENERATE : 'hot_address_generate'
};

CryptoProxy.prototype.logFunction = function log(type) {
    var self = this;
    return function() {
        self.log.info(type, 'ripple crypto_proxy');
    };
};

CryptoProxy.URL = {
    transfer: 'http://localhost:5990/v1/payments',
};

CryptoProxy.prototype.generateUserAddress = function(request, callback) {
    var self = this;
    self.log.info('** Generate User Address Request Received **');
    self.log.info("generateUserAddress req: " + JSON.stringify(request));
    self.log.info("generateUserAddress do nothing");
};

CryptoProxy.prototype.synchronousHotAddr =  function(request, callback) {
    var self = this;
    self.log.info('** Synchronous Hot Addr Request Received **');
    self.log.info("Synchronous Hot Addr Request: " + JSON.stringify(request));
    var shr = new SyncHotAddressesResult({error: ErrorCode.OK, addresses: []});
    var cryptoAddress = new CryptoAddress({address: self.hotAccount, privateKey: self.secret});
    shr.addresses.push(cryptoAddress);
    callback(self.makeNormalResponse_(BitwayResponseType.SYNC_HOT_ADDRESSES, self.currency, shr));
};

CryptoProxy.prototype.syncPrivateKeys =  function(request, callback) { 
    var self = this;
    self.log.info('** Synchronous Addr Request Received **');
    self.log.info("syncPKs req: " + JSON.stringify(request));
    self.log.info("syncPrivateKeys do nothing");
};

CryptoProxy.prototype.transfer = function(request, callback) {
    var self = this;
    self.log.info('** TransferRequest Received **');
    self.log.info("transfer req: " + JSON.stringify(request));
    var ids = [];
    for (var i = 0; i < request.transferInfos.length; i++) {
        self.makeTransfer_.bind(self)(request.type, request.transferInfos[i]);
    }
};

CryptoProxy.prototype.sign_ = function(transferInfo, callback) {
    var self = this;
    var requestBody = {                                                                      
       "method": "sign",                                                              
       "params": [                                                                    
              {                                                                       
                  "offline": false,                                                   
                  "secret": self.secret,                          
                  "tx_json": {                                                        
                     "Account": self.hotAccount,                 
                     "Amount": (transferInfo.amount) * 1000000,                                                   
                     "Destination": transferInfo.to,             
                     "TransactionType": "Payment"                                     
                   }                                                                  
               }                                                                      
           ]                                                                          
    };      
    request({
        method: 'POST',
        url: 'http://s1.ripple.com:51234/',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(requestBody)
    }, function (error, response, body) {
        if (!error) {
            console.log('Response:', body);
            var responseBody = JSON.parse(body);
            if (response.statusCode ==200 && responseBody.result.status == "success") {
                callback(null, responseBody.result.tx_blob);
            } else {
                self.log.error("sign_error");
                callback("sign_ error", null);
            }
        } else {
            self.log.error("sign_error", error);
            callback("sign_ error", null);
        }
    });
};

CryptoProxy.prototype.submit_ = function(tx_blob, callback) {
    var self = this;
    var requestBody = {                                                                      
       "method": "submit",                                                              
       "params": [                                                                    
            {                                                                       
                "tx_blob": tx_blob,                                                   
            }                                                                      
        ]                                                                          
    };      
    request({
        method: 'POST',
        url: 'http://s1.ripple.com:51234/',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(requestBody)
    }, function (error, response, body) {
        if (!error) {
            console.log('Response:', body);
            var responseBody = JSON.parse(body);
            if (response.statusCode == 200 && responseBody.result.status == "success") {
                var tx = responseBody.result.tx_json;
                var cctx = self.constructCctxByTxJson_(tx); 
                callback(null, cctx);
            } else {
                self.log.error("submit_error");
                callback("submit_ error", null);
            }
        } else {
            self.log.error("submit_error", error);
            callback("submit_ error", null);
        }
    });
};

CryptoProxy.prototype.makeTransfer_ = function(type, transferInfo) {
    var self = this;
    switch (type) {
        case TransferType.WITHDRAWAL:
        case TransferType.HOT_TO_COLD:
            Async.compose(self.submit_.bind(self),
                self.sign_.bind(self))(transferInfo, function(error, cctx) {
                    var ids = [];
                    ids.push(transferInfo.id);
                    if (!error) {
                        cctx.ids = ids;
                        cctx.txType = type;
                        self.emit(CryptoProxy.EventType.TX_ARRIVED, 
                            self.makeNormalResponse_(BitwayResponseType.TRANSACTION, self.currency, cctx));
                    } else {
                        var response = new CryptoCurrencyTransaction({ids: ids, txType: type, 
                            status: TransferStatus.FAILED});
                        self.emit(CryptoProxy.EventType.TX_ARRIVED,
                            self.makeNormalResponse_(BitwayResponseType.TRANSACTION, self.currency, response));
                    }
                });
            break;
        default:
            this.log.error("Invalid type: " + type);
    }
};

CryptoProxy.prototype.multi_transfer = function(request, callback) {
    var self = this;
    self.log.info('**Multi Transfer Request Received **');
    self.log.info("multi transfer req: " + JSON.stringify(request));
    var requestAarry = [];
    for (var key in request.transferInfos) {
        var singleRequest = new TransferCryptoCurrency({currency: self.currency, 
            transferInfos: request.transferInfos[key], type: Number(key)});
        requestAarry.push(singleRequest);
    }
    Async.map(requestAarry, self.transfer.bind(self), function(results) {
    });
}

CryptoProxy.prototype.start = function() {
    this.checkBlockAfterDelay_();
};

CryptoProxy.prototype.checkBlockAfterDelay_ = function(opt_interval) {
    var self = this;
    var interval = self.checkInterval;
    opt_interval != undefined && (interval = opt_interval)
    setTimeout(self.checkBlock_.bind(self), interval);
};

CryptoProxy.prototype.getMissedBlocks = function(request, callback) {
    var self = this;
    self.log.info('** Get Missed Request Received **');
    self.log.info("getMissedBlocks req:" + JSON.stringify(request));
    self.checkMissedRange_.bind(self)(request);
};

CryptoProxy.prototype.checkMissedRange_ = function(request) {
    var self = this;
    self.log.warn("Missed start begin position: " + request.startIndexs[0].height);
    self.log.warn("Missed start end position: " + request.startIndexs[request.startIndexs.length - 1].height);
    self.log.warn("Required block position: " + request.endIndex.height);
    self.log.warn("Behind: " + (request.endIndex.height - request.startIndexs[request.startIndexs.length - 1].height));
    self.redis.set(self.lastIndex, request.startIndexs[request.startIndexs.length - 1].height, function(errorRedis, retRedis)     {
        if (!errorRedis) {
            self.log.info("change position to ", request.startIndexs[request.startIndexs.length - 1].height);
        } else {
           self.log.error("checkMissedRange_ error!"); 
        }
    });
};

CryptoProxy.prototype.checkBlock_ = function() {
    var self = this;
    self.getNextCCBlock_(function(error, result){
        if (!error) {
            var response = new CryptoCurrencyBlockMessage({block: result});
            self.emit(CryptoProxy.EventType.BLOCK_ARRIVED,
                self.makeNormalResponse_(BitwayResponseType.AUTO_REPORT_BLOCKS, self.currency, response));
            self.checkBlockAfterDelay_(0);
        } else {
            self.checkBlockAfterDelay_();
        }
   });
};

CryptoProxy.prototype.getNextCCBlock_ = function(callback) {
    var self = this;
    Async.compose(self.getNextCCBlockSinceLastIndex_.bind(self), 
        self.getLastIndex_.bind(self))(callback);
};

CryptoProxy.prototype.getLastIndex_ = function(callback) {
    var self = this;
    self.redis.get(self.lastIndex, function(error, index) {
        var numIndex = Number(index);
        if (!error && numIndex) {
            callback(null, numIndex);
        } else {
            callback(null, -1);
        }
    });
};

CryptoProxy.prototype.getNextCCBlockSinceLastIndex_ = function(index, callback) {
    var self = this;
    self.log.info("getNextCCBlockSinceLastIndex_ index: ", index);
    self.getBlockCount_(function(error, count) {
        self.log.info("getNextCCBlockSinceLastIndex_ count: ", count);
        if (error) {
            self.log.error(error);
            callback(error);
        } else if (index == count) {
            self.log.debug('no new block found');
            callback('no new block found');
        } else {
            var nextIndex = (index == -1) ? count : index + 1;
            self.log.info("getNextCCBlockSinceLastIndex_ nextIndex: ", nextIndex);
            self.redis.del(self.getProcessedSigidsByHeight_(nextIndex - 1), function() {});
            self.getCCBlockByIndex_(nextIndex, callback);
        }
    });
};

CryptoProxy.prototype.getBlockCount_ = function(callback) {
    var self = this;
    var requestBody = {
        "method": "ledger_closed",
        "params": [
            {}
        ]
    };
    request({
        method: 'POST',
        url: 'http://s1.ripple.com:51234/',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(requestBody)
    }, function (error, response, body) {
        if (!error) {
            var responseBody = JSON.parse(body);
            console.log('responseBody:', responseBody);
            if (response.statusCode == 200 && responseBody.result.status == "success") {
                callback(null, responseBody.result.ledger_index);
            } else {
                self.log.error("getBlockCount_error");
                callback("getBlockCount_ error", null);
            }
        } else {
            self.log.error("getBlockCount_error", error);
            callback("getBlockCount_ error", null);
        }
    });
};

CryptoProxy.prototype.convertAmount_ = function(valueStr) {
    var value = parseFloat(valueStr);
    return value/1000000;
};

CryptoProxy.prototype.constructCctxByTxJson_ = function(tx) {
    var self = this;
    self.log.info("~~~~~~~~~~~~");
    var input = new CryptoCurrencyTransactionPort({address: tx.Account, amount: (self.convertAmount_(tx.Amount) + self.convertAmount_(tx.Fee))});
    self.log.info("**************");
    var inputs = [];
    inputs.push(input);
    if (tx.DestinationTag) {
        var output = new CryptoCurrencyTransactionPort({address: tx.Destination, amount: self.convertAmount_(tx.Amount), 
            memo: (tx.DestinationTag).toString()});
    } else {
        var output = new CryptoCurrencyTransactionPort({address: tx.Destination, amount: self.convertAmount_(tx.Amount)});
    }
    var outputs = [];
    outputs.push(output);
    var cctx = new CryptoCurrencyTransaction({txid: tx.hash, inputs: inputs, outputs: outputs,            
        status: TransferStatus.CONFIRMING});                                                                    
    cctx.sigId = tx.hash;        
    return cctx;
};

CryptoProxy.prototype.getCCBlockByIndex_ = function(index, callback) {
    var self = this;
    self.log.info("Enter into getCCBlockByIndex_ index: ", index);
    var requestBody = {
        "method": "account_tx",
        "params": [
            {
                "account": self.hotAccount,
                "binary": false,
                "ledger_index_max": index,
                "ledger_index_min": index,
                "limit": 10,
            }
        ]
    };
    request({
        method: 'POST',
        url: 'http://s1.ripple.com:51234/',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(requestBody)
    }, function (error, response, body) {
        if (!error) {
            var responseBody = JSON.parse(body);
            console.log('responseBody:', responseBody);
            if (response.statusCode == 200 && !error) {
                self.redis.set(self.lastIndex, index, function(errorRedis, retRedis) {
                    if (!errorRedis) {
                        var prevIndex = new BlockIndex({id: (index - 1).toString(), height: index - 1});
                        var currentIndex = new BlockIndex({id: (index).toString(), height: index});
                        var cctxs = [];
                        for (var i = 0; i < responseBody.result.transactions.length; i++) {
                            var tx = responseBody.result.transactions[i].tx;
                            self.log.info(tx);
                            if (tx.TransactionType == "Payment") {
                                self.log.info("yangli 1");
                                cctxs.push(self.constructCctxByTxJson_(tx));
                            }
                        }    
                        self.log.info("cctxs", cctxs);
                        var ccBlock = new CryptoCurrencyBlock({index: currentIndex, prevIndex: prevIndex, txs: cctxs});
                        self.log.info("ccBlock", ccBlock);
                        callback(null, ccBlock);
                    } else {
                        self.log.error("getCCBlockByIndex_errorRedis: ", errorRedis);
                        callback(errorRedis);
                    }
                });
            } else {
                self.log.error("getBlockCount_error");
                callback("getCCBlockByIndex_ error", null);
            }
        } else {
            self.log.error("getBlockCount_error", error);
            callback("getCCBlockByIndex_ error", null);
        }
    });
};

CryptoProxy.prototype.makeNormalResponse_ = function(type, currency, response) {
    switch (type) {
        case BitwayResponseType.SYNC_HOT_ADDRESSES:
            this.log.info("sync hot addr response", response);
            return new BitwayMessage({currency: currency, syncHotAddressesResult: response});
        case BitwayResponseType.SYNC_PRIVATE_KEYS:
            this.log.info("sync addr response");
            return new BitwayMessage({currency: currency, syncPrivateKeysResult: response});
        case BitwayResponseType.GENERATE_ADDRESS:
            this.log.info("generate addr response");
            return new BitwayMessage({currency: currency, generateAddressResponse: response});
        case BitwayResponseType.TRANSFER:
        case BitwayResponseType.TRANSACTION:
            this.log.info("transfer response: " + JSON.stringify(response));
            return new BitwayMessage({currency: currency, tx: response});
        case BitwayResponseType.GET_MISSED_BLOCKS:
        case BitwayResponseType.AUTO_REPORT_BLOCKS:
            this.log.info("blocks response: " + JSON.stringify(response));
            return new BitwayMessage({currency: currency, blockMsg: response});
        default:
            this.log.error("Inavalid Type!");
            return null
    }
};

CryptoProxy.prototype.getProcessedSigidsByHeight_ = function(height) {
    return this.processedSigids + '_' + height;
};

CryptoProxy.invokeCallback_ = function(error, resultFun, callback) {
    if (error) {
        callback(error);
    } else {
        callback(null, resultFun());
    }
};
