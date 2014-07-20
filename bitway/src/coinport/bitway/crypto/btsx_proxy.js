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
    SyncHotAddressesResult        = MessageTypes.SyncHotAddressesResult;

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
        opt_config.cryptoRpc != undefined && (this.rpc = opt_config.cryptoRpc);
        opt_config.redis != undefined && (this.redis = opt_config.redis);
        opt_config.minConfirm != undefined && (this.minConfirm = opt_config.minConfirm);
        opt_config.checkInterval != undefined && (this.checkInterval = opt_config.checkInterval);
        opt_config.minerfee != undefined && (self.minerFee = opt_config.minerFee);
        opt_config.walletPassPhrase != undefined && (this.walletPassPhrase = opt_config.walletPassPhrase);
    }

    this.currency || (this.currency = currency);
    this.minConfirm || (this.minConfirm = 1);
    this.rpc || (this.rpc = new Bitcore.RpcClient({
        protocol: 'http',
        user: 'user',
        pass: 'pass',
        host: '127.0.0.1',
        port: '18332',
    }));
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
};
Util.inherits(CryptoProxy, Events.EventEmitter);

CryptoProxy.ACCOUNT = 'customers';
CryptoProxy.HOT_ACCOUNT = "hot";
CryptoProxy.MIN_GENERATE_ADDR_NUM = 1;
CryptoProxy.MAX_GENERATE_ADDR_NUM = 1000;
CryptoProxy.MAX_CONFIRM_NUM = 9999999;
CryptoProxy.HOT_ADDRESS_NUM = 10;

CryptoProxy.EventType = {
    TX_ARRIVED : 'tx_arrived',
    BLOCK_ARRIVED : 'block_arrived',
    HOT_ADDRESS_GENERATE : 'hot_address_generate'
};

var http    = require('http');
var auth = Buffer('test' + ':' + 'test').toString('base64');
var options = {
  host: '127.0.0.1',
  path: '/rpc',
  method: 'POST',
  port: 9989,
  agent: this.disableAgent ? false : undefined,                   
};

CryptoProxy.prototype.logFunction = function log(type) {
    var self = this;
    return function() {
        self.log.info(type, 'crypto_proxy');
    };
};

CryptoProxy.prototype.httpRequest_ = function(request, callback) {
    var self = this;
    var err = null;
    var req = http.request(options, function(res) {
        var buf = '';

        res.on('data', function(data) {
            buf += data; 
        });

        res.on('end', function() {
            if(res.statusCode == 401) {
                console.log(new Error('bitcoin JSON-RPC connection rejected: 401 unauthorized'));
                return;
            }

            if(res.statusCode == 403) {
                console.log(new Error('bitcoin JSON-RPC connection rejected: 403 forbidden'));
                return;
            }

            if(err) {
                console.log('error: ', err);
                return;
            }

            try {
                console.log(buf);
                var pos = buf.indexOf('{');
                var x = buf.substring(pos, buf.length);
                console.log("x: ", x.length);
                var parsedBuf = JSON.parse(x.data || x);
                console.log("Json Result: ", parsedBuf);
                callback(null, parsedBuf);
            } catch(e) {
                console.log("e.stack", e.stack);
                console.log('HTTP Status code:' + res.statusCode);
                return;
            }
        });
    });

    req.on('error', function(e) {
        var err = new Error('Could not connect to bitcoin via RPC: '+e.message);
        console.log(err);
    });

    console.log("request: ", request); 
    req.setHeader('Accept', 'application/json, text/plain, */*');
    req.setHeader('Connection', 'keep-alive');
    req.setHeader('Content-Length', request.length);
    req.setHeader('Content-Type', 'application/json;charaset=UTF-8');
    req.setHeader('Authorization', 'Basic ' + auth);
    req.setHeader('Accept-Encoding', 'gzip,deflate,sdch');
    req.write(request);
    req.end();
}

CryptoProxy.prototype.generateNewAccountName_ = function(unused, callback) {
    var self = this;
    var requestBody = {jsonrpc: '2.0', id: 2, method: "wallet_list_accounts", params: []};
    var request = JSON.stringify(requestBody);

    self.httpRequest_(request, function(error, result) {
        console.log("request: ", request);
        if(!error) {
            console.log("result: ", result);
            var number = result.result.length + 1;
            console.log("result.result.length: ", result.result.length);
            var accountName = CryptoProxy.ACCOUNT + number;
            console.log("result: ", accountName);
            callback(null, accountName);
        } else {
            console.log("error: ", error);
            callback(error, null);
        }
    });
};

CryptoProxy.prototype.getPrivateKeyByAccount_ = function(accountName, callback) {
    var self = this;
    var params = [];
    params.push(accountName);
    var requestBody = {jsonrpc: '2.0', id: 2, method: "wallet_account_export_private_key", params: params};
    var request = JSON.stringify(requestBody);
    console.log("request: ", request);
    self.httpRequest_(request, function(error, result) {
        if (!error) {
            console.log("result: ", result);
            callback(null, result.result);
        } else {
            console.log("error: ", error);
            callback(error, null);
        }
    });
};

CryptoProxy.prototype.generateNewAccount_ = function(accountName, callback) {
    var self = this;
    var params = [];
    params.push(accountName);
    var requestBody = {jsonrpc: '2.0', id: 2, method: "wallet_account_create", params: params};
    var request = JSON.stringify(requestBody);
    console.log("request: ", request);
    self.httpRequest_(request, function(error, result) {
        if (!error) {
            console.log("result: ", result);
            self.getPrivateKeyByAccount_(accountName, function(errPriv, retPriv) {
                if (!errPriv) {
                    console.log(retPriv);
                    var accountInfo = new Object({accountName: accountName, address: result.result,
                        privateKey: retPriv.result});
                    console.log("accountInfo: ", accountInfo);
                    callback(null, cryptoAddress);
                } else {
                    console.log("errPriv: ", errPriv);
                    callback(errPriv, null);
                }
            });
        } else {
            callback(error, null);
        }
    });
};

CryptoProxy.prototype.generateCustomerAccount_ = function(accountInfo, callback) {
    var self = this;
    var params = [];
    params.push(accountInfo.accountName);
    params.push(CryptoProxy.HOT_ACCOUNT);
    var requestBody = {jsonrpc: '2.0', id: 2, method: "wallet_account_register", params: params};
    var request = JSON.stringify(requestBody);
    console.log("request: ", request);
    self.httpRequest_(request, function(error, result) {
        if (!error) {
            console.log("result: ", result);
            var cryptoAddress = new CryptoAddress({address: accountInfo.address, privateKey: accountInfo.privateKey});
            console.log("cryptoAddress: ", cryptoAddress);
            callback(null, cryptoAddress);
        } else {
            callback(error, null);
        }
    });
};

CryptoProxy.prototype.generateCustomerAccount_ = function(unusedIndex, callback) { 
    var self = this;
    Async.compose(self.registeAccount_bind(self),
        self.generateNewAccount_.bind(self),
        self.generateNewAccountName_.bind(self))("", function(error, cryptoAddress) {
            if (!error) {
                callback(null, cryptoAddress);
            } else {
                callback(error, null);
            }
        });
};

CryptoProxy.prototype.generateUserAddress = function(request, callback) {
    var self = this;
    self.log.info('** Generate User Address Request Received **');
    self.log.info("generateUserAddress req: " + JSON.stringify(request));
    if (request.num < CryptoProxy.MIN_GENERATE_ADDR_NUM || request.num > CryptoProxy.MAX_GENERATE_ADDR_NUM) {
        callback(self.makeNormalResponse_(BitwayResponseType.GENERATE_ADDRESS, self.currency,
            new GenerateAddressesResult({error: ErrorCode.INVALID_REQUEST_ADDRESS_NUM})));
    } else {
        Async.times(request.num, self.generateCustomerAccount_.bind(self), function(error, results) {
            var gar = new GenerateAddressesResult({error: ErrorCode.OK, addressType: CryptoCurrencyAddressType.UNUSED})
            if (error || results.length != request.num) {
                gar.error = ErrorCode.RPC_ERROR;
            } else {
                gar.addresses = results;
            }
            console.log("gar: ", gar);
            callback(self.makeNormalResponse_(BitwayResponseType.GENERATE_ADDRESS, self.currency, gar));
        });
    }
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
    params.push(CryptoProxy.HOT_ACCOUNT);
    var requestBody = {jsonrpc: '2.0', id: 2, method: "wallet_get_account", params: params};
    var request = JSON.stringify(requestBody);
    console.log("request: ", request);
    self.httpRequest_(request, function(error, result) {
        if (!error) {
            console.log("result: ", result);
            if (result.result.length == 0) {
                self.generateNewAccount_ (CryptoProxy.HOT_ACCOUNT, function(errGNA, retGNA) {
                    if (!error) {
                        var accountInfo = new Object({ address: result.result,
                            privateKey: retGNA.privateKey});
                        console.log("accountInfo: ", accountInfo);
                        callback(null, cryptoAddress);
                    } else {
                        callback(errGNA, null);
                    }
                });
            } else {
                self.getPrivateKeyByAccount_(accountName, function(errPriv, retPriv) {
                    if (!errPriv) {
                        console.log(retPriv);
                        var accountInfo = new Object({address: result.result,
                            privateKey: retPriv.result});
                        console.log("accountInfo: ", accountInfo);
                        callback(null, cryptoAddress);
                    } else {
                        console.log("errPriv: ", errPriv);
                        callback(errPriv, null);
                    }
                });
                    var cryptoAddress = new CryptoAddress({address: accountInfo.address, privateKey: accountInfo.privateKey});
                }
                var cryptoAddress = new CryptoAddress({address: accountInfo.address, privateKey: accountInfo.privateKey});
                console.log("cryptoAddress: ", cryptoAddress);
                callback(null, cryptoAddress);
        } else {
            callback(error, null);
        }
    });
};

CryptoProxy.prototype.syncPrivateKeys =  function(request, callback) { 
    var self = this;
    self.log.info('** Synchronous Addr Request Received **');
    self.log.info("syncPKs req: " + JSON.stringify(request));
    var spkr = new SyncPrivateKeysResult({error: ErrorCode.OK, addresses: []});
    if (request.pubKeys && request.pubKeys.length > 0) {
        Async.map(request.pubKeys, self.getPrivateKey_.bind(self), function(error, cryptoAddrs) {
            if (error) {
                spkr.error = ErrorCode.RPC_ERROR;
            } else {
                spkr.addresses = cryptoAddrs;
            }
            callback(self.makeNormalResponse_(BitwayResponseType.SYNC_PRIVATE_KEYS, self.currency, spkr));
        });
    } else {
        self.log.error("Invalid Request!");
    }
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

CryptoProxy.prototype.dumpPrivateKey_ = function(address, callback) {
    var self = this;
    var privateKey = null;
    self.rpc.dumpPrivKey(address, function(errPriv, replyPriv){
        if (errPriv) {
            callback(errPriv);
        } else {
           if (self.walletPassPhrase) { 
               privateKey = self.encryptPrivKey_(replyPriv.result, self.walletPassPhrase);
           } else {
               privateKey = replyPriv.result;
           }
           var cryptoAddress = new CryptoAddress({address: address, privateKey: privateKey});
           callback(null, cryptoAddress);
        }
    });
};

CryptoProxy.prototype.getPrivateKey_ = function(address, callback) {
    var self = this;
    if (self.walletPassPhrase) {
        Async.series([
            function(cb) {
                self.walletPassPhrase_.bind(self)(cb)},
            function(cb) {
                self.dumpPrivateKey_.bind(self)(address, cb)}
        ], function(err, values) {
            if (err) {
                self.log.error(err);
                callback(err, null);
            } else {
                callback(null, values[1]);
            }
        });
    } else {
        self.log.warn("no password!");
        self.dumpPrivateKey_.bind(self)(address, callback);
    }
};

CryptoProxy.prototype.transfer = function(request, callback) {
    var self = this;
    self.log.info('** TransferRequest Received **');
    self.log.info("transfer req: " + JSON.stringify(request));
    var ids = [];
    for (var i = 0; i < request.transferInfos.length; i++) {
        self.makeTransfer(request.type, request.transferInfos[i]);
    }
};

CryptoProxy.prototype.walletTransfer = function(amount, from, to, id) {
    var self = this;
    var params = [];
    params.push(amount);
    params.push("BTSX");
    params.push(from.name);
    params.push(to.name);
    var requestBody = {jsonrpc: '2.0', id: 2, method: "wallet_transfer", params: params};
    var request = JSON.stringify(requestBody);
    console.log("request: ", request);
    self.httpRequest_(request, function(error, result) {
        if (!error) {
            console.log("result: ", result);
            cctx.ids = ids;
            cctx.txType = request.type;
            cctx.status = TransferStatus.CONFIRMING;
            self.log.info("ids: " + ids + " sigId: " + cctx.sigId);
            self.redis.sadd(cctx.sigId, cctx.ids, function(redisError, redisReply){
                if (redisError) {
                    self.log.error("redis sadd error! ids: ", cctx.ids);
                }
            });
            self.emit(CryptoProxy.EventType.TX_ARRIVED,
                self.makeNormalResponse_(BitwayResponseType.TRANSACTION, self.currency, response));
        } else {
            console.log("error: ", error);
            var response = new CryptoCurrencyTransaction({ids: ids, txType: request.type, 
                status: TransferStatus.FAILED});
            self.emit(CryptoProxy.EventType.TX_ARRIVED,
                self.makeNormalResponse_(BitwayResponseType.TRANSACTION, self.currency, response));
        }
    });
};

CryptoProxy.prototype.makeTransfer = function(type, transferInfo) {
    switch (type) {
        case TransferType.WITHDRAWAL:
        case TransferType.HOT_TO_COLD:
            var withdrawalAccount = addWithdrawalAccount(transferInfo.to);
            if (withdrawalAccount) {
                 self.walletTransfer(transferInfo.amount, CryptoProxy.HOT_ACCOUNT, withdrawalAccount);
            }
            break;
        case TransferType.USER_TO_HOT:
            var userName = self.findAccountByKey(transferInfo.from);
            if (userName) {
                 self.walletTransfer(transferInfo.amount, userName, CryptoProxy.HOT_ACCOUNT);
            }
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
    Async.map(requestAarry, self.transfer.bind(self), function(result) {
        callback(result);    
    });
}

CryptoProxy.prototype.jsonToAmount_ = function(value) {
    return Math.round(1e8 * value)/1e8;
};

CryptoProxy.prototype.getAHotAddressByRandom_ = function(callback) {
    var self = this;
    self.rpc.getAddressesByAccount(CryptoProxy.HOT_ACCOUNT, function(errAddr, retAddr){
        if (errAddr) {
            self.log.error(errAddr);
            callback(errAddr);
        } else {
            if (retAddr.result.length == 0) {
                Async.times(CryptoProxy.HOT_ADDRESS_NUM, self.generateAHotAddress_.bind(self), function(error, results) {
                    if (error || results.length != CryptoProxy.HOT_ADDRESS_NUM) {
                        callback(error);
                    } else {
                        var pos = Math.floor(Math.random()*CryptoProxy.HOT_ADDRESS_NUM);
                        self.log.info("the lucky hot address is:" + results[pos]);
                        callback(null, results[pos]);
                    }
                });
            } else {
                var pos = Math.floor(Math.random()*retAddr.result.length);
                self.log.info("the lucky hot address is:" + retAddr.result[pos]);
                callback(null, retAddr.result[pos]);
            }
        }
    });
};

CryptoProxy.prototype.generateAHotAddress_ = function(unusedIndex, callback) {
    var self = this;
    self.rpc.getNewAddress(CryptoProxy.HOT_ACCOUNT, function(errPub, replyPub) {
        if (errPub) {
            self.log.error(errPub);
            callback(errPub);
        } else {
            self.getPrivateKey_(replyPub.result, function(errPriv, replyPriv) {
                if (errPriv) {
                    callback(errPriv);
                } else {
                    var addresses = [];
                    var cryptoAddress = replyPriv;
                    addresses.push(cryptoAddress);
                    self.log.info("generate a hot addr: " + cryptoAddress.address);
                    var gar = new GenerateAddressesResult({error: ErrorCode.OK, addresses: addresses,
                        addressType: CryptoCurrencyAddressType.HOT});
                    self.emit(CryptoProxy.EventType.HOT_ADDRESS_GENERATE,
                        self.makeNormalResponse_(BitwayResponseType.GENERATE_ADDRESS, self.currency, gar));
                    callback(null, cryptoAddress.address);
                }
            });
        }
    });
};

CryptoProxy.prototype.walletPassPhrase_ = function(callback) {
    var self = this;
    this.rpc.walletPassPhrase(self.walletPassPhrase, 900,  function(error) {
        if (error) {
            self.log.warn("walletPassPhrase error: " + error);
        }
        callback(null);
    });
};

CryptoProxy.prototype.walletLock_ = function(callback) {
    var self = this;
    this.rpc.walletLock(function(error) {
        callback(error);
    });
};

CryptoProxy.prototype.start = function() {
    //this.checkTxAfterDelay_();
    this.checkBlockAfterDelay_();
};

CryptoProxy.prototype.checkTxAfterDelay_ = function(opt_interval) {
    var self = this;
    var interval = self.checkInterval;
    opt_interval != undefined && (interval = opt_interval)
    setTimeout(self.checkTx_.bind(self), interval);
};

CryptoProxy.prototype.checkBlockAfterDelay_ = function(opt_interval) {
    var self = this;
    var interval = self.checkInterval;
    opt_interval != undefined && (interval = opt_interval)
    setTimeout(self.checkBlock_.bind(self), interval);
};

CryptoProxy.prototype.checkTx_ = function() {
    var self = this;
    var requestBody = {jsonrpc: '2.0', id: 2, method: "blockchain_get_pending_transactions", params: []};
    var request = JSON.stringify(requestBody);
    console.log("request: ", request);
    self.httpRequest_(request, function(error, result) {
        if(!error) {
            console.log("result: ", result);
            for (var i = 0; i < result.result.length; ++i) {
                var cctx = self.constructPendingCCTX(result.result[i]); 
                self.emit(CryptoProxy.EventType.TX_ARRIVED,
                    self.makeNormalResponse_(BitwayResponseType.TRANSACTION, self.currency, cctx));
            }
        } else {
            console.log("error: ", error);
        }
        self.checkTxAfterDelay_();
    });
};

//TODO:
CryptoProxy.prototype.constructPendingCCTX_ = function(pendingData) {

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
               if (request.startIndexs[i].id != hashArray[i].hash) {
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
    console.log("getNextCCBlockSinceLastIndex_ index: ", index);
    self.getBlockCount_(function(error, count) {
        console.log("getNextCCBlockSinceLastIndex_ count: ", count);
        if (error) {
            self.log.error(error);
            callback(error);
        } else if (index == count) {
            self.log.debug('no new block found');
            callback('no new block found');
        } else {
            var nextIndex = (index == -1) ? count : index + 1;
            console.log("getNextCCBlockSinceLastIndex_ nextIndex: ", nextIndex);
            self.redis.del(self.getProcessedSigidsByHeight_(nextIndex - 1), function() {});
            self.redis.set(self.lastIndex, nextIndex, function(error, replay) {
                if (!error) {
                    self.getCCBlockByIndex_(nextIndex, callback);
                } else {
                    self.log.error(error);
                    callback(error);
                }
            });
        }
    });
};

CryptoProxy.prototype.getBlockCount_ = function(callback) {
    var self = this;
    var requestBody = {jsonrpc: '2.0', id: 2, method: "blockchain_get_blockcount", params: []};
    var request = JSON.stringify(requestBody);
    console.log("request: ", request);
    self.httpRequest_(request, function(error, result) {
        console.log("result: ", result);
        CryptoProxy.invokeCallback_(error, function() {return result.result}, callback);
    });
};

CryptoProxy.prototype.generateOneAddress_ = function(unusedIndex, callback) {
    var self = this;
    self.rpc.getNewAddress(CryptoProxy.ACCOUNT, function(errPub, replyPub) {
        if (errPub) {
            callback(errPub);
        } else {
            self.getPrivateKey_(replyPub.result, function(errPriv, replyPriv) {
                if (errPriv) {
                    callback(errPriv);
                } else {
                    callback(null, replyPriv);
                }
            });
        }
    });
};

CryptoProxy.prototype.getNewCCTXsFromTxids_ = function(height, txids, callback) {
    var self = this;
    Async.map(txids, self.getCCTXFromTxid_.bind(self), function(error, cctxs) {
        if (error) {
            callback(error);
        } else {
            self.redis.smembers(self.getProcessedSigidsByHeight_(height), function(error, sigIds) {
                if (error) {
                    callback(error);
                } else {
                    var sigStrIds = sigIds.map(function(element) {return String(element)});
                    var newCCTXs = cctxs.filter(function(element) {return sigStrIds.indexOf(element.sigId) == -1;});
                    self.redis.sadd(self.getProcessedSigidsByHeight_(height),
                        newCCTXs.map(function(element) {return element.sigId}), function(error, replay) {
                        if (error) {
                            callback(error);
                        } else {
                            callback(null, newCCTXs);
                        }
                    });
                }
            });
        }
    });
};

CryptoProxy.prototype.getTxidsSinceBlockHash_ = function(hash, callback) {
    var self = this;
    this.rpc.listSinceBlock(hash.hash, function(error, txs) {
        if (error) {
            callback(error);
        } else {
            var txids = [];
            for (var i = 0; i < txs.result.transactions.length; i++) {
                if (txs.result.transactions[i].confirmations > 0 || txs.result.transactions[i].confirmations == 0) {
                    txids.push(txs.result.transactions[i].txid);
                } else {
                    self.log.warn("Invalid txid: ", txs.result.transactions[i].txid);
                    self.log.warn("valid txid: ", txs.result.transactions[i].walletconflicts);
                }
            }
            callback(null, hash.index, txids);
        }
    });
};

CryptoProxy.prototype.getCCBlockByIndex_ = function(index, callback) {
    var self = this;
    console.log("Enter into getCCBlockByIndex_ index: ", index);
    Async.parallel ([
        function(cb) {self.getWalletTransactionByIndex_.bind(self)(index, cb)},
        function(cb) {self.getBlockHash_.bind(self)(index - 1, cb)},
        function(cb) {self.getBlockHash_.bind(self)(index, cb)}
        ], function(err, results){
        if (!err) {
            console.log("results: ", results);
            var prevIndex = new BlockIndex({id: results[1], height: index - 1});
            var currentIndex = new BlockIndex({id: results[2], height: index});
            var ccBlock = new CryptoCurrencyBlock({index: currentIndex, prevIndex: prevIndex, txs: results[0]});
            console.log("getCCBlockByIndex_: ", ccBlock);
            callback(null, ccBlock);
        } else {
            console.log("getCCBlockByIndex_ err: ", err);
            callback(err, null);
        }
    });
};

CryptoProxy.prototype.getWalletTransactionByIndex_ = function(height, callback) {
    var self = this;
    console.log("Enter into getWalletTransactionByIndex_");
    var params = [];
    params.push("");
    params.push(height);
    params.push(height);
    var requestBody = {jsonrpc: '2.0', id: 2, method: "wallet_account_transaction_history", params: params};
    var request = JSON.stringify(requestBody);
    console.log("request: ", request);
    self.httpRequest_(request, function(error, result) {
        if(!error) {
            console.log("result: ", result);
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
            console.log("error: ", error);
            callback(error, null);
        }
    });
}

CryptoProxy.prototype.constructCCTXByTxHistory_ = function(txHistory, callback) {
    var self = this;
    var inputs = [];
    console.log("txHistory %j: ", txHistory);
    var ledger_entries = txHistory.ledger_entries[0];
    console.log("ledger_entries: ", ledger_entries);
    console.log("amount: ", ledger_entries.amount);
    console.log("amount.amount: ", ledger_entries.amount.amount);

    Async.parallel ([
        function(cb) {self.getKeyByAccountName_.bind(self)(ledger_entries.from_account, cb)},
        function(cb) {self.getKeyByAccountName_.bind(self)(ledger_entries.to_account, cb)}
        ], function(err, results){
        if (!err) {
            var input = new CryptoCurrencyTransactionPort({address: results[0],
               amount: ledger_entries.amount.amount + txHistory.fee.amount});
            inputs.push(input);
            var outputs = [];
            var output = new CryptoCurrencyTransactionPort({address: results[1],
               amount: ledger_entries.amount.amount});
            outputs.push(output);
            var cctx = new CryptoCurrencyTransaction({sigId: txHistory.trx_id, txid: txHistory.trx_id,
                ids: null, inputs: inputs, outputs: outputs});
            callback(null, cctx);
        } else {
            callback(error, null);
        }
    });
};

CryptoProxy.prototype.getKeyByAccountName_ = function(accountName, callback) {
    var self = this;
    console.log("Enter into getKeyByAccountName_ accountName:", accountName);
    var params = [];
    params.push(accountName);
    var requestBody = {jsonrpc: '2.0', id: 2, method: "blockchain_get_account", params: params};
    var request = JSON.stringify(requestBody);
    console.log("getKeyByAccountName_ request: ", request);
    self.httpRequest_(request, function(error, result) {
        if(!error) {
            console.log("getKeyByAccountName_ result: ", result);
            if (result.result && result.result.name == accountName) {
                callback(null, result.result.owner_key);
            } else {
                callback(null, accountName);
            }
        } else {
            console.log("getBlockHash_ error: ", error);
            callback(error, null);
        }
    });
};

CryptoProxy.prototype.getBlockHash_ = function(height, callback) {
    var self = this;
    console.log("Enter into getBlockHash_ height:", height);
    var params = [];
    params.push(height);
    var requestBody = {jsonrpc: '2.0', id: 2, method: "blockchain_get_blockhash", params: params};
    var request = JSON.stringify(requestBody);
    console.log("getBlockHash_ request: ", request);
    self.httpRequest_(request, function(error, result) {
        if(!error) {
            console.log("getBlockHash_ result: ", result);
            callback(null, result.result);
        } else {
            console.log("getBlockHash_ error: ", error);
            callback(error, null);
        }
    });
};

CryptoProxy.prototype.getOutputAddresses_ = function(tx) {
    var retAddresses = [];
    for (var k = 0; k < tx.vout.length; k++) {
        var output = new CryptoCurrencyTransactionPort();
        if (tx.vout[k].scriptPubKey.addresses != undefined)
        {
            output.address = tx.vout[k].scriptPubKey.addresses.toString();
            output.amount = tx.vout[k].value;
            retAddresses.push(output);
        }
    }
    return retAddresses;
};

CryptoProxy.prototype.getInputAddress_ = function(vinItem, callback) {
    var self = this;
    if (vinItem.txid) { this.rpc.getRawTransaction(vinItem.txid, 1, function(error, tx) {
        if (error) {
            callback(error);
        } else {
            var outIndexes = tx.result.vout.filter(function(element) {return element.n == vinItem.vout});
            var input = new CryptoCurrencyTransactionPort();
            if (outIndexes.length > 0 && outIndexes[0].scriptPubKey && outIndexes[0].scriptPubKey.addresses) {
                input.address = outIndexes[0].scriptPubKey.addresses.toString();
                input.amount = outIndexes[0].value;
                callback(null, input);
            } else {
                self.log.warn('the previous tx\'s output is ' + outIndexes[0].scriptPubKey.type + ', txid: ', tx.result.txid);
                input.address = outIndexes[0].scriptPubKey.type;
                input.amount = outIndexes[0].value;
                callback(null, input);
            }
        }});
    } else {
        var input = new CryptoCurrencyTransactionPort();
        input.address = 'coinbase';
        input.amount = 0;
        callback(null, input);
    }
};

CryptoProxy.prototype.getCCTXFromTx_ = function(tx, callback) {
    var self = this;
    var vinTxids = "";
    for (var i = 0; i < tx.vin.length; i++){
        vinTxids += (tx.vin[i].txid + tx.vin[i].vout);
    }
    var retOutputs = self.getOutputAddresses_(tx);
    Async.map(tx.vin, self.getInputAddress_.bind(self), function(error, rawInputs) {
        if (error) {
            self.log.error(error);
            callback(error);
        } else {
            var cctx = new CryptoCurrencyTransaction({txid: tx.txid, inputs: rawInputs, outputs: retOutputs,
                status: TransferStatus.CONFIRMING});
            var sigId = self.getSigId_.bind(self)(cctx, vinTxids);
            cctx.sigId = sigId;
            self.redis.smembers(sigId, function(error, ids) {
                if (!error && ids != undefined && ids != null)
                    cctx.ids = ids.map(function(element) {return Number(element);});
                callback(null, cctx);
            });
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
