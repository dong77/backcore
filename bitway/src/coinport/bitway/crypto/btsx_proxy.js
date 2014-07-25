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
        opt_config.walletName != undefined && (this.walletName = opt_config.walletName);
        opt_config.walletPassPhrase != undefined && (this.walletPassPhrase = opt_config.walletPassPhrase);
    }

    this.rpcUser = opt_config.cryptoRpcConfig.user;
    this.rpcPass = opt_config.cryptoRpcConfig.pass;
    this.httpOptions = {
      host: opt_config.cryptoRpcConfig.host,
      path: '/rpc',
      method: 'POST',
      port: opt_config.cryptoRpcConfig.port,
      agent: this.disableAgent ? false : undefined,                   
    };

    this.currency || (this.currency = currency);
    this.redis || (this.redis = Redis.createClient('6379', '127.0.0.1'));
    this.redis.on('connect'     , this.logFunction('connect'));
    this.redis.on('ready'       , this.logFunction('ready'));
    this.redis.on('reconnecting', this.logFunction('reconnecting'));
    this.redis.on('error'       , this.logFunction('error'));
    this.redis.on('end'         , this.logFunction('end'));

    this.checkInterval || (this.checkInterval = 5000);
    this.minerFee || (this.minerFee = 0.0001);
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
        self.log.info(type, 'btsx crypto_proxy');
    };
};

CryptoProxy.prototype.httpRequest_ = function(request, callback) {
    var self = this;
    var err = null;
    var auth = Buffer(self.rpcUser + ':' + self.rpcPass).toString('base64');
    var req = http.request(self.httpOptions, function(res) {
        var buf = '';

        res.on('data', function(data) {
            buf += data; 
        });

        res.on('end', function() {
            if(res.statusCode == 401) {
                self.log.info(new Error('bitcoin JSON-RPC connection rejected: 401 unauthorized'));
                return;
            }

            if(res.statusCode == 403) {
                self.log.info(new Error('bitcoin JSON-RPC connection rejected: 403 forbidden'));
                return;
            }

            if(err) {
                self.log.error('httpRequest error: ', err);
                return;
            }

            try {
                var pos = buf.indexOf('{');
                var body = buf.substring(pos, buf.length);
                var parsedBuf = JSON.parse(body.data || body);
                callback(null, parsedBuf);
            } catch(e) {
                self.log.error("e.stack", e.stack);
                self.log.error('HTTP Status code:' + res.statusCode);
                return;
            }
        });
    });

    req.on('error', function(e) {
        var err = new Error('Could not connect to bitcoin via RPC: '+e.message);
        self.log.error(err);
    });

    req.setHeader('Accept', 'application/json, text/plain, */*');
    req.setHeader('Connection', 'keep-alive');
    req.setHeader('Content-Length', request.length);
    req.setHeader('Content-Type', 'application/json;charaset=UTF-8');
    req.setHeader('Authorization', 'Basic ' + auth);
    req.setHeader('Accept-Encoding', 'gzip,deflate,sdch');
    req.write(request);
    req.end();
}

CryptoProxy.prototype.getPrivateKeyByAccount_ = function(accountName, callback) {
    var self = this;
    var params = [];
    params.push(accountName);
    var requestBody = {jsonrpc: '2.0', id: 2, method: "wallet_account_export_private_key", params: params};
    var request = JSON.stringify(requestBody);
    self.log.info("getPrivateKeyByAccount_ request: ", request);
    self.httpRequest_(request, function(error, result) {
        if (!error) {
            var privateKey = result.result;
            callback(null, privateKey);
        } else {
            self.log.info("getPrivateKeyByAccount_ error: ", error);
            callback(error, null);
        }
    });
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
    self.getHotAccount_.bind(self)(function(error, result) {
       if (!error) {
            var addresses = [];
            addresses.push(result);
            shr.addresses = addresses;
            callback(self.makeNormalResponse_(BitwayResponseType.SYNC_HOT_ADDRESSES, self.currency, shr));
       } else {
            shr.error = ErrorCode.RPC_ERROR;
            callback(self.makeNormalResponse_(BitwayResponseType.SYNC_HOT_ADDRESSES, self.currency, shr));
       }
    });
};

CryptoProxy.prototype.getHotAccount_ =  function(callback) { 
    var self = this;
    var params = [];
    params.push(self.hotAccountName);
    var requestBody = {jsonrpc: '2.0', id: 2, method: "wallet_get_account", params: params};
    var request = JSON.stringify(requestBody);
    self.log.info("getHotAccount_ request: ", request);
    self.httpRequest_(request, function(error, result) {
        if (!error) {
            var accountInfo = new CryptoAddress({accountName: self.hotAccountName,
                address: result.result.owner_key,
                privateKey: null});
            self.getPrivateKeyByAccount_(self.hotAccountName, function(errPriv, retPriv) {
                if (!errPriv) {
                    accountInfo.privateKey = retPriv;
                    callback(null, accountInfo);
                } else {
                    self.log.info("errPriv: ", errPriv);
                    callback(errPriv, null);
                }
            });
        } else {
            callback(error, null);
        }
    });
};

CryptoProxy.prototype.syncPrivateKeys =  function(request, callback) { 
    var self = this;
    self.log.info('** Synchronous Addr Request Received **');
    self.log.info("syncPKs req: " + JSON.stringify(request));
    self.log.info("syncPrivateKeys do nothing");
};

CryptoProxy.prototype.encryptPrivKey_ = function(priv, key) {
    var cipher = Crypto.createCipher('aes-256-cbc', key);
    var crypted = cipher.update(priv,'utf8','hex');
    crypted += cipher.final('hex');
    var message = crypted;
    return message;
}

CryptoProxy.prototype.decrypt_ = function(message, key) {
    var decipher = Crypto.createDecipher('aes-256-cbc', key);
    var dec = decipher.update(message,'hex','utf8');
    dec += decipher.final('utf8');
};

CryptoProxy.prototype.transfer = function(request, callback) {
    var self = this;
    self.log.info('** TransferRequest Received **');
    self.log.info("transfer req: " + JSON.stringify(request));
    var ids = [];
    for (var i = 0; i < request.transferInfos.length; i++) {
        self.makeTransfer_(request.type, request.transferInfos[i]);
    }
};

CryptoProxy.prototype.walletTransfer_ = function(type, amount, from, to, id) {
    var self = this;
    var params = [];
    params.push(amount);
    params.push("BTSX");
    params.push(from.accountName);
    params.push(to.accountName);
    var requestBody = {jsonrpc: '2.0', id: 2, method: "wallet_transfer", params: params};
    var request = JSON.stringify(requestBody);
    self.log.info("walletTransfer_ request: ", request);
    self.httpRequest_(request, function(error, result) {
        if (!error) {
            self.log.info("walletTransfer_ result: ", result);
            var cctx = new CryptoCurrencyTransaction({status: TransferStatus.CONFIRMING});
            cctx.ids = id;
            cctx.txType = type;
            cctx.status = TransferStatus.CONFIRMING;
            cctx.sigId = self.getSigId_(result.result.signatures[0]);
            self.log.info("ids: " + id + " sigId: " + cctx.sigId);
            self.redis.set(cctx.sigId, cctx.ids, function(redisError, redisReply){
                if (redisError) {
                    self.log.error("redis sadd error! ids: ", cctx.ids);
                }
            });
            self.emit(CryptoProxy.EventType.TX_ARRIVED,
                self.makeNormalResponse_(BitwayResponseType.TRANSACTION, self.currency, cctx));
        } else {
            self.log.info("error: ", error);
            var response = new CryptoCurrencyTransaction({ids: ids, txType: type, 
                status: TransferStatus.FAILED});
            self.emit(CryptoProxy.EventType.TX_ARRIVED,
                self.makeNormalResponse_(BitwayResponseType.TRANSACTION, self.currency, response));
        }
        if (type == TransferType.WITHDRAWAL || type == TransferType.HOT_TO_COLD) {
            self.removeContactAccount_.bind(self)(to);
        }
    });
};

CryptoProxy.prototype.removeContactAccount_ = function(contactAccount) {
    var self = this;
    var params = [];
    params.push(contactAccount.accountName);
    var requestBody = {jsonrpc: '2.0', id: 2, method: "wallet_remove_contact_account", params: params};
    var request = JSON.stringify(requestBody);
    self.log.info("wallet_remove_contact_account request: ", request);
    self.httpRequest_(request, function(error, result) {
        if (!error) {
            self.log.info("remove contactAccount success accountName: ", contactAccount.accountName);
        } else {
            self.log.error("removeContactAccount_ error: ", error);
        }
    });
};

CryptoProxy.prototype.getSigId_ = function(signatures) {
    var sha256 = Crypto.createHash('sha256');
    sha256.update(signatures);
    return sha256.digest('hex');
};

CryptoProxy.prototype.makeTransfer_ = function(type, transferInfo) {
    switch (type) {
        case TransferType.WITHDRAWAL:
        case TransferType.HOT_TO_COLD:
            Async.parallel ([
                function(cb) {self.getAccountByAccountName_.bind(self)(self.hotAccountName, cb)},
                function(cb) {self.addWithdrawalAccount_.bind(self)(transferInfo.from.accountName, cb)}
                ], function(error, results){
                if (!error) {
                     self.walletTransfer_(transferInfo.amount, results[0], results[1], transferInfo.id);
                } else {
                    var response = new CryptoCurrencyTransaction({ids: transferInfo.id, txType: type, 
                        status: TransferStatus.FAILED});
                    self.emit(CryptoProxy.EventType.TX_ARRIVED,
                        self.makeNormalResponse_(BitwayResponseType.TRANSACTION, self.currency, response));
                }
            });
            break;
        case TransferType.USER_TO_HOT:
            self.log.info("makeTransfer_ USER_TO_HOT do nothing!");
            //Async.parallel ([
            //    function(cb) {self.getAccountByAccountName_.bind(self)(CryptoProxy.DEPOSIT_ACCOUNT, cb)},
            //    function(cb) {self.getAccountByAccountName_.bind(self)(self.hotAccountName, cb)}
            //    ], function(error, results){
            //        if (!error) {
            //             self.walletTransfer_(transferInfo.amount, results[0], results[1], transferInfo.id);
            //        } else {
            //            var response = new CryptoCurrencyTransaction({ids: transferInfo.id, txType: type, 
            //                status: TransferStatus.FAILED});
            //            self.emit(CryptoProxy.EventType.TX_ARRIVED,
            //                self.makeNormalResponse_(BitwayResponseType.TRANSACTION, self.currency, response));
            //        }
            //});
            break;
        default:
            this.log.error("Invalid type: " + type);
    }
};

CryptoProxy.prototype.addWithdrawalAccount_ = function(transferInfo, callback) {
    var self = this;
    if (transferInfo.id) {
        var params = [];
        params.push("out" + transferInfo.id);
        params.push(transferInfo.to);
        var requestBody = {jsonrpc: '2.0', id: 2, method: "wallet_add_contact_account", params: params};
        var request = JSON.stringify(requestBody);
        self.log.info("addWithdrawalAccount_ request: ", request);
        self.httpRequest_(request, function(error, result) {
            if (!error) {
                var account = new CryptoCurrencyTransactionPort({accountName: transferInfo.id, 
                    address: transferInfo.to});
                callback(null, account);
            } else {
                callback(error, null);
            }
        });
    } else {
        var errMessage = "transferInfo.id is null";
        callback(errMessage, null);
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

CryptoProxy.prototype.convertAmount_ = function(value) {
    return value/100000;
};

CryptoProxy.prototype.walletOpen_ = function(callback) {
    var self = this;
    var params = [];
    params.push(self.walletName);
    var requestBody = {jsonrpc: '2.0', id: 2, method: "wallet_open", params: params};
    var request = JSON.stringify(requestBody);
    self.log.info("walletOpen_ request: ", request);
    self.httpRequest_(request, function(error, result) {
        if (!error) {
            callback(null, result.result);
        } else {
            self.log.info("wallet_open error: ", error);
            callback(error, null);
        }
    });
};

CryptoProxy.prototype.walletUnlock_ = function(callback) {
    var self = this;
    self.log.info("Enter into walletUnlock_!");
    var params = [];
    params.push(3600);
    params.push(self.walletPassPhrase);
    var requestBody = {jsonrpc: '2.0', id: 2, method: "wallet_unlock", params: params};
    var request = JSON.stringify(requestBody);
    self.httpRequest_(request, function(error, result) {
        if (!error) {
            self.log.info("walletUnlock_: ", result);
            callback(null, result.result);
        } else {
            self.log.info("wallet_unlock error: ", error);
            callback(error, null);
        }
    });
};


CryptoProxy.prototype.initWallet_ = function(callback) {
    var self = this;
    if (self.walletName && self.walletPassPhrase) {
        Async.series([
            function(cb) {
                self.walletOpen_.bind(self)(cb)},
            function(cb) {
                self.walletUnlock_.bind(self)(cb)}
        ], function(error, result) {
            if (error) {
                self.log.error("initWallet", error);
                callback(error, null);
            } else {
                self.log.info("initWallet success!");
                callback(null, result);
            }
        });
    } else {
        self.log.warn("no password!");
        callback("wallet info lose!", null);
    }
};

CryptoProxy.prototype.start = function() {
    var self = this;
    self.initWallet_(function(error, reuslt) {
        if (!error) {
            self.checkBlockAfterDelay_();
            self.unlockWalletAfterDelay_();
        } else {
            self.log.error("init wallet failed");
        }
    });
};

CryptoProxy.prototype.checkBlockAfterDelay_ = function(opt_interval) {
    var self = this;
    var interval = self.checkInterval;
    opt_interval != undefined && (interval = opt_interval)
    setTimeout(self.checkBlock_.bind(self), interval);
};

CryptoProxy.prototype.unlockWalletAfterDelay_ = function(opt_interval) {
    var self = this;
    var interval = 900000;
    self.walletUnlock_.bind(self)(function(error, result){                                                   
         if (!error) {
             setTimeout(self.unlockWalletAfterDelay_.bind(self), interval);
         } else {
             self.log.error("unlockWalletAfterDelay_ error: ", error);
             setTimeout(self.unlockWalletAfterDelay_.bind(self), 1000);
         }
    });
};

CryptoProxy.prototype.getMissedBlocks = function(request, callback) {
    var self = this;
    self.log.info('** Get Missed Request Received **');
    self.log.info("getMissedBlocks req:" + JSON.stringify(request));
    self.checkMissedRange_.bind(self)(request);
    Async.compose(self.getReorgBlock_.bind(self),
        self.getReorgPosition_.bind(self))(request, function(err, reorgBlock) {
            if (err) {
                self.log.error(err);
                callback(err);
            } else {
                if (reorgBlock.block) {
                    self.redis.set(self.lastIndex, reorgBlock.block.index.height,function(error, replay) {
                        if (!error) {
                        } else {
                            self.log.error(error);
                            callback(error);
                        }
                    });
                }
                callback(null, self.makeNormalResponse_(BitwayResponseType.GET_MISSED_BLOCKS, self.currency, reorgBlock));
            }
        });
};

CryptoProxy.prototype.checkMissedRange_ = function(request) {
    var self = this;
    self.log.warn("Missed start begin position: " + request.startIndexs[0].height);
    self.log.warn("Missed start end position: " + request.startIndexs[request.startIndexs.length - 1].height);
    self.log.warn("Required block position: " + request.endIndex.height);
    self.log.warn("Behind: " + (request.endIndex.height - request.startIndexs[request.startIndexs.length - 1].height));
};

CryptoProxy.prototype.getReorgPosition_ = function(request, callback) {
    var self = this;
    var indexes = request.startIndexs.map(function(element) {return Number(element.height)});
    Async.map(indexes, self.getBlockHash_.bind(self), function(error, hashArray) {
        if (error) {
            self.log.error(error);
            callback(error);
        } else {
            var position = 0;
            var flag = false;
            for (var i = 0; i < request.startIndexs.length; i++) {
               position = i;
               if (request.startIndexs[i].id != hashArray[i]) {
                   flag = true;
                   break;
               }
            }
            if (flag) {
                if (position == 0) {
                    self.log.error("###### fatal forked ###### height: ", indexes[position]);
                    callback(null, -1);
                } else {
                    self.log.error("###### forked ###### height: ", indexes[position] - 1);
                    callback(null, request.startIndexs[position -1]);
                }
            } else {
                callback(null, request.startIndexs[position]);
            }
        }
    });
};

CryptoProxy.prototype.getReorgBlock_ = function(index, callback) {
    var self = this;
    if (index == -1) {
        var gmb = new CryptoCurrencyBlockMessage({reorgIndex: new BlockIndex(null, null)});
        callback(null, gmb);
    } else {
        self.getCCBlockByIndex_.bind(self)(index.height + 1, function(error, block){
            if (error) {
                self.log.error(error);
                callback(error);
            } else {
                var gmb = new CryptoCurrencyBlockMessage({reorgIndex: index, block: block});
                callback(null, gmb);
            }
        });
    }
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
    var requestBody = {jsonrpc: '2.0', id: 2, method: "blockchain_get_blockcount", params: []};
    var request = JSON.stringify(requestBody);
    self.log.info("getBlockCount_ request: ", request);
    self.httpRequest_(request, function(error, result) {
        self.log.info("getBlockCount_ result: ", result);
        CryptoProxy.invokeCallback_(error, function() {return result.result}, callback);
    });
};

CryptoProxy.prototype.getCCBlockByIndex_ = function(index, callback) {
    var self = this;
    self.log.info("Enter into getCCBlockByIndex_ index: ", index);
    Async.parallel ([
        function(cb) {self.getWalletTransactionByIndex_.bind(self)(index, cb)},
        function(cb) {self.getBlockHash_.bind(self)(index - 1, cb)},
        function(cb) {self.getBlockHash_.bind(self)(index, cb)}
        ], function(error, results){
            self.log.info("getCCBlockByIndex_ results: ", results);
            if (!error && results[1] && results[2]) {
                self.redis.set(self.lastIndex, index, function(errorRedis, retRedis) {
                    if (!errorRedis) {
                        var prevIndex = new BlockIndex({id: results[1], height: index - 1});
                        var currentIndex = new BlockIndex({id: results[2], height: index});
                        var ccBlock = new CryptoCurrencyBlock({index: currentIndex, prevIndex: prevIndex, txs: results[0]});
                        callback(null, ccBlock);
                    } else {
                        self.log.error("getCCBlockByIndex_errorRedis: ", errorRedis);
                        callback(errorRedis);
                    }
                });
            } else {
                self.log.error("getCCBlockByIndex_ error: ", error);
                callback(error, null);
            }
    });
};

CryptoProxy.prototype.getWalletTransactionByIndex_ = function(height, callback) {
    var self = this;
    self.log.info("Enter into getWalletTransactionByIndex_");
    var params = [];
    params.push("");
    params.push("BTSX");
    params.push(height);
    params.push(height);
    var requestBody = {jsonrpc: '2.0', id: 2, method: "wallet_account_transaction_history", params: params};
    var request = JSON.stringify(requestBody);
    self.log.info("request: ", request);
    self.httpRequest_(request, function(error, result) {
        if(!error) {
            self.log.info("getWalletTransactionByIndex_ result: ", result);
            var txs = [];
            Async.map(result.result, self.constructCCTXByTxHistory_.bind(self), function(error, results) {
                if (error) {
                    self.log.error(error);
                    callback(error);
                } else {
                    callback(null, results);
                }
            });
        } else {
            self.log.error("error: ", error);
            callback(error, null);
        }
    });
}

CryptoProxy.prototype.constructCCTXByTxHistory_ = function(txHistory, callback) {
    var self = this;
    var inputs = [];
    self.log.info("txHistory %j: ", txHistory);
    var ledger_entries = txHistory.ledger_entries;
    self.log.info("ledger_entries: ", ledger_entries);

    Async.parallel ([
        function(cb) {self.getSigIdByTxId_.bind(self)(txHistory.trx_id, cb)},
        function(cb) {self.constructInputs_.bind(self)(ledger_entries, cb)},
        function(cb) {self.constructOutputs_.bind(self)(ledger_entries, cb)}
        ], function(err, results){
        if (!err) {
            var cctx = new CryptoCurrencyTransaction({sigId: results[0], txid: txHistory.trx_id,
                ids: null, inputs: results[1], outputs: results[2], 
                minerFee: self.convertAmount_(txHistory.fee)});
            self.redis.get(results[0], function(error, ids) {
                if (!error) {
                    cctx.ids = ids;
                    callback(null, cctx);
                } else {
                    self.log.error("constructCCTXByTxHistory_", error);
                    callback(error, null);
                }
            });
        } else {
            self.log.error("constructCCTXByTxHistory_", error);
            callback(error, null);
        }
    });
};

CryptoProxy.prototype.constructInputs_ = function(ledgerEntries, callback) {
    var self = this;
    var inputAccountNames = [];
    for (var i = 0; i < ledgerEntries.length; i++) {
        inputAccountNames.push(ledgerEntries[i].from_account);
    }
    Async.map(inputAccountNames, self.getAccountByAccountName_.bind(self), function(errors, results) {
        if (!errors) {
            var inputs = [];
            for (var i = 0; i < results.length; i++) {
                var input = new CryptoCurrencyTransactionPort({accountName: results[i].accountName,
                    address: results[i].address,
                    amount: self.convertAmount_(ledgerEntries[i].amount.amount)});
                inputs.push(input);
            }
            callback(null, inputs);
       } else {
           callback(errors, null);
       }
   });
};

CryptoProxy.prototype.constructOutputs_ = function(ledgerEntries, callback) {
    var self = this;
    var outputAccountNames = [];
    for (var i = 0; i < ledgerEntries.length; i++) {
        outputAccountNames.push(ledgerEntries[i].to_account);
    }
    Async.map(outputAccountNames, self.getAccountByAccountName_.bind(self), function(errors, results) {
        if (!errors) {
            var outputs = [];
            for (var i = 0; i < results.length; i++) {
                var output = new CryptoCurrencyTransactionPort({accountName: results[i].accountName,
                    address: results[i].address,
                    amount: self.convertAmount_(ledgerEntries[i].amount.amount)});
                if (output.accountName == self.hotAccountName) {
                    output.memo = ledgerEntries[i].memo;
                }
                outputs.push(output);
            }
            callback(null, outputs);
        } else {
            callback(errors, null);
        }
    });
};

CryptoProxy.prototype.getSigIdByTxId_ = function(txid, callback) {
    var self = this;
    self.log.info("Enter into getSigIdByTxId_ txid:", txid);
    var params = [];
    params.push(txid);
    var requestBody = {jsonrpc: '2.0', id: 2, method: "blockchain_get_transaction", params: params};
    var request = JSON.stringify(requestBody);
    self.log.info("getSigIdByTxId_ request: ", request);
    self.httpRequest_(request, function(error, result) {
        if(!error) {
            self.log.info("getSigIdByTxId_ result: ", result);
            var sigId = self.getSigId_(result.result.trx.signatures[0]);
            callback(null, sigId);
        } else {
            callback(error, null);
        }
    });
};

CryptoProxy.prototype.getAccountByAccountName_ = function(accountName, callback) {
    var self = this;
    var params = [];
    params.push(accountName);
    var requestBody = {jsonrpc: '2.0', id: 2, method: "wallet_get_account", params: params};
    var request = JSON.stringify(requestBody);
    self.log.info("getAccountByAccountName_ request: ", request);
    self.httpRequest_(request, function(error, result) {
        if(!error) {
            self.log.info("getAccountByAccountName_ result: ", result);
            if (result.result && result.result.name == accountName) {
                var account = new CryptoAddress({accountName: accountName, address: result.result.owner_key});
                callback(null, account);
            } else {
                var account = new CryptoAddress({accountName: accountName, address: accountName});
                callback(null, account);
            }
        } else {
            self.log.error("getAccountByAccountName_ error: ", error);
            callback(error, null);
        }
    });
};

CryptoProxy.prototype.getBlockHash_ = function(height, callback) {
    var self = this;
    self.log.info("Enter into getBlockHash_ height:", height);
    var params = [];
    params.push(height);
    var requestBody = {jsonrpc: '2.0', id: 2, method: "blockchain_get_blockhash", params: params};
    var request = JSON.stringify(requestBody);
    self.log.info("getBlockHash_ request: ", request);
    self.httpRequest_(request, function(error, result) {
        if(!error) {
            self.log.info("getBlockHash_ result: ", result);
            callback(null, result.result);
        } else {
            self.log.error("getBlockHash_ error: ", error);
            callback(error, null);
        }
    });
};

CryptoProxy.prototype.makeNormalResponse_ = function(type, currency, response) {
    switch (type) {
        case BitwayResponseType.SYNC_HOT_ADDRESSES:
            this.log.info("sync hot addr response");
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
