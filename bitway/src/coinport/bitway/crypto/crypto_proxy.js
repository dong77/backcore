/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
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
    BlockIndex                    = DataTypes.BlockIndex;

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
        opt_config.cryptoRpc && (this.rpc = opt_config.cryptoRpc);
        opt_config.redis && (this.redis = opt_config.redis);
        opt_config.minConfirm && (this.minConfirm = opt_config.minConfirm);
        opt_config.checkInterval && (this.checkInterval = opt_config.checkInterval);
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
    this.checkInterval || (this.checkInterval = 5000);

    this.processedSigids = this.currency + '_processed_sigids';
    this.lastIndex = this.currency + '_last_index';
    this.log = Logger.logger(this.currency.toString());
};
Util.inherits(CryptoProxy, Events.EventEmitter);

CryptoProxy.ACCOUNT = 'customers';
CryptoProxy.HOT_ACCOUNT = "hot";
CryptoProxy.TIP = 0.0001;
CryptoProxy.MIN_GENERATE_ADDR_NUM = 1;
CryptoProxy.MAX_GENERATE_ADDR_NUM = 1000;
CryptoProxy.MAX_CONFIRM_NUM = 9999999;
CryptoProxy.HOT_ADDRESS_NUM = 10;

CryptoProxy.EventType = {
    TX_ARRIVED : 'tx_arrived',
    BLOCK_ARRIVED : 'block_arrived',
    HOT_ADDRESS_GENERATE : 'hot_address_generate'
};

CryptoProxy.prototype.generateUserAddress = function(request, callback) {
    var self = this;
    self.log.info('** Generate User Address Request Received **');
    self.log.info("generateUserAddress req: " + JSON.stringify(request));
    if (request.num < CryptoProxy.MIN_GENERATE_ADDR_NUM || request.num > CryptoProxy.MAX_GENERATE_ADDR_NUM) {
        callback(self.makeNormalResponse_(BitwayResponseType.GENERATE_ADDRESS, self.currency,
            new GenerateAddressesResult({error: ErrorCode.INVALID_REQUEST_ADDRESS_NUM})));
    } else {
        Async.times(request.num, self.generateOneAddress_.bind(self), function(error, results) {
            var gar = new GenerateAddressesResult({error: ErrorCode.OK, addressType: CryptoCurrencyAddressType.UNUSED})
            if (error || results.length != request.num) {
                gar.error = ErrorCode.RPC_ERROR;
            } else {
                gar.addresses = results;
            }
            callback(self.makeNormalResponse_(BitwayResponseType.GENERATE_ADDRESS, self.currency, gar));
        });
    }
};

CryptoProxy.prototype.transfer = function(request, callback) {
    var self = this;
    self.log.info('** TransferRequest Received **');
    self.log.info("transfer req: " + JSON.stringify(request));
    var ids = [];
    for (var i = 0; i < request.transferInfos.length; i++) {
        ids.push(request.transferInfos[i].id);
    }
    Async.compose(self.getCCTXFromTxid_.bind(self),
        self.sendTransaction_.bind(self),
        self.signTransaction_.bind(self),
        self.createRawTransaction_.bind(self),
        self.constructRawTransaction_.bind(self))(request, function(error, cctx) {
        if (error) {
            self.log.error(error);
            self.log.error("Transfer failed! ids: " + ids);
            var response = new CryptoCurrencyTransaction({ids: ids, status: TransferStatus.FAILED});
            callback(self.makeNormalResponse_(BitwayResponseType.TRANSACTION, self.currency, response));
        } else {
            cctx.status = TransferStatus.CONFIRMING;
            cctx.ids = ids;
            self.log.info("ids: " + ids + " sigId: " + cctx.sigId);
            self.redis.sadd(cctx.sigId, cctx.ids, function(redisError, redisReply){
                if (redisError) {
                    self.log.error("redis sadd error! ids: ", cctx.ids);
                }
            });
            callback(self.makeNormalResponse_(BitwayResponseType.TRANSACTION, self.currency, cctx));
        }
    });
};

CryptoProxy.prototype.constructRawTransaction_ = function(transferReq, callback) {
    var self = this;
    switch(transferReq.type){
        case TransferType.WITHDRAWAL:
        case TransferType.HOT_TO_COLD:
            Async.parallel ([
                function(cb) {self.getHotUnspent_.bind(self)(cb)},
                function(cb) {self.getAHotAddressByRandom_.bind(self)(cb)}
                ], function(err, result){
                if (!err) {
                    var unspentTxs = result[0];
                    var changeAddress = result[1];
                    var amountUnspent = 0;
                    var transactions = [];
                    var addresses = {};
                    var amountTotalPay = self.calTotalPay_(transferReq);
                    for (var i = 0; i < unspentTxs.length; i++) {
                        amountUnspent += unspentTxs[i].amount;
                        var transaction = {txid: unspentTxs[i].txid, vout: unspentTxs[i].vout};
                        transactions.push(transaction);
                        if (amountUnspent > (amountTotalPay + CryptoProxy.TIP)) {
                            addresses[changeAddress] = amountUnspent - amountTotalPay - CryptoProxy.TIP;
                            break;
                        } else if((amountUnspent < (amountTotalPay + CryptoProxy.TIP) && amountUnspent > amountTotalPay)
                                || amountUnspent == (amountTotalPay + CryptoProxy.TIP)) {
                            break;
                        }
                    }
                    for(var j =0; j < transferReq.transferInfos.length; j++)
                    {
                        addresses[transferReq.transferInfos[j].to] = transferReq.transferInfos[j].amount;
                    }
                    var rawData = {transactions: transactions, addresses: addresses};
                    callback(null, rawData);
                } else {
                    callback(err);
                }
            });
            break;
        case TransferType.USER_TO_HOT:
            var fromAddresses = [];
            var amountTotalPay = 0;
            for (var i = 0; i < transferReq.transferInfos.length; i++) {
                fromAddresses.push(transferReq.transferInfos[i].from);
                amountTotalPay += transferReq.transferInfos[i].amount;
            }
            Async.parallel ([
                function (cb) {self.getUnspentOfAddresses_.bind(self)(fromAddresses, cb)},
                function (cb) {self.getAHotAddressByRandom_.bind(self)(cb)}
                ], function(err, result){
                    if (err) {
                        self.log.error(err);
                        callback(err);
                    } else {
                        var unspentTxs = result[0];
                        var toAddress = result[1];
                        var amountTotalUnspent = 0;
                        var transactions = [];
                        var addresses = {};
                        for (var j = 0; j < unspentTxs.length; j++) {
                            amountTotalUnspent += unspentTxs[j].amount;
                            var transaction = {txid: unspentTxs[j].txid, vout: unspentTxs[j].vout};
                            transactions.push(transaction);
                        }
                        if (amountTotalUnspent > amountTotalPay && amountTotalUnspent < amountTotalPay + CryptoProxy.TIP) {
                            addresses[toAddress] = amountTotalPay;
                            var rawData = {transactions: transactions, addresses: addresses};
                            callback(null, rawData);
                        } else if (amountTotalUnspent == amountTotalPay){
                            if (amountTotalPay > CryptoProxy.TIP) {
                                addresses[toAddress] = amountTotalPay;
                            } else {
                                addresses[toAddress] = amountTotalPay - amountTotalPay*0.1;
                            }
                            var rawData = {transactions: transactions, addresses: addresses};
                            callback(null, rawData);
                        } else if (amountTotalUnspent > amountTotalPay + CryptoProxy.TIP
                                || amountTotalUnspent == amountTotalPay + CryptoProxy.TIP){
                            self.log.warn("User accounts exceed the amount transferred!");
                            addresses[toAddress] = amountTotalUnspent - CryptoProxy.TIP;
                            var rawData = {transactions: transactions, addresses: addresses};
                            callback(null, rawData);
                        } else {
                            self.log.error("Lack of balance!");
                            var err = {code: "Lack of balance!", message: "Lack of balance!"};
                            callback(err);
                        }
                    }
                });
            break;
        default:
            this.log.error("Invalid type: " + type);
    }
};

CryptoProxy.prototype.getHotUnspent_ = function(callback) {
    var self = this;
    Async.compose(self.getUnspentOfAddresses_.bind(self),
        self.getAllHotAddresses_.bind(self))(callback);
};

CryptoProxy.prototype.getAllHotAddresses_ = function(callback) {
    var self = this;
    this.rpc.getAddressesByAccount(CryptoProxy.HOT_ACCOUNT, function(addrError, addrReply) {
        if (addrError) {
            self.log.error(addrError);
            callback(addrError);
        } else {
            callback(null, addrReply.result);
        }
    });
};

CryptoProxy.prototype.getUnspentOfAddresses_ = function(addresses, callback) {
    var self = this;
    self.rpc.listUnspent(self.minConfirm, CryptoProxy.MAX_CONFIRM_NUM,
        addresses, function(unspentError, unspentReply) {
        if (unspentError) {
            self.log(unspentError);
            callback(unspentError);
        } else {
            var transactions = [];
            for (var i = 0; i < unspentReply.result.length; i++) {
                var transaction = {txid: unspentReply.result[i].txid,
                vout: unspentReply.result[i].vout, amount: unspentReply.result[i].amount};
                transactions.push(transaction);
            }
            callback(null, transactions);
        }
    });
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
    self.rpc.getNewAddress(CryptoProxy.HOT_ACCOUNT, function(error, address) {
        if (error) {
            self.log.err(error);
            callback(error);
        } else {
            var addresses = [];
            addresses.push(address.result);
            var gar = new GenerateAddressesResult({error: ErrorCode.OK, addresses: addresses,
                addressType: CryptoCurrencyAddressType.HOT});
            self.emit(CryptoProxy.EventType.HOT_ADDRESS_GENERATE,
                self.makeNormalResponse_(BitwayResponseType.GENERATE_ADDRESS, self.currency, gar));
            callback(null, address.result);
        }
    });
};

CryptoProxy.prototype.createRawTransaction_ = function(rawData, callback) {
    var self = this;
    var transactions = rawData.transactions;
    var addresses = rawData.addresses;
    self.log.debug(transactions);
    self.log.debug(addresses);
    this.rpc.createRawTransaction(transactions, addresses, function(error, createReply) {
        if (error) {
            self.log.error("create error: " + error);
            callback(error);
        } else {
            callback(null, createReply.result);
        }
    });
}

CryptoProxy.prototype.signTransaction_ = function(data, callback) {
    var self = this;
    this.rpc.signRawTransaction(data, function(error, signReply) {
        if (error) {
            self.log.error("sign error: " + error);
            callback(error);
        } else {
            callback(null, signReply.result.hex);
        }
    });
}

CryptoProxy.prototype.sendTransaction_ = function(hex, callback) {
    var self = this;
    this.rpc.sendRawTransaction(hex, function(error, sendReply) {
        if (error) {
            self.log.error("send error: " + error);
            callback(error);
        } else {
            self.log.info("made a transfer, txid: " + sendReply.result);
            callback(null, sendReply.result);
        }
    });
};

CryptoProxy.prototype.calTotalPay_ = function(transferReq) {
    var amountTotalPay = 0;
    for (var i = 0; i < transferReq.transferInfos.length; i++) {
        amountTotalPay += transferReq.transferInfos[i].amount;
    }
    return amountTotalPay;
};

CryptoProxy.prototype.start = function() {
    this.checkTxAfterDelay_();
    this.checkBlockAfterDelay_();
};

CryptoProxy.prototype.checkTxAfterDelay_ = function(opt_interval) {
    var self = this;
    var interval = self.checkInterval;
    opt_interval && (interval = opt_interval)
    setTimeout(self.checkTx_.bind(self), interval);
};

CryptoProxy.prototype.checkBlockAfterDelay_ = function(opt_interval) {
    var self = this;
    var interval = self.checkInterval;
    opt_interval && (interval = opt_interval)
    setTimeout(self.checkBlock_.bind(self), interval);
};

CryptoProxy.prototype.checkTx_ = function() {
    var self = this;
    self.getNewCCTXsSinceLatest_(function(error, newCCTXs) {
        if (!error && newCCTXs.length != 0) {
            for (var i = 0; i < newCCTXs.length; ++i) {
                self.emit(CryptoProxy.EventType.TX_ARRIVED,
                    self.makeNormalResponse_(BitwayResponseType.TRANSACTION, self.currency, newCCTXs[i]));
            }
        }
        self.checkTxAfterDelay_();
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
    var behind = request.endIndex.height - request.startIndexs[request.startIndexs.length - 1].height;
    self.log.warn("Behind: " + behind);
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
                    self.log.info(block);
                    var gmb = new CryptoCurrencyBlockMessage({reorgIndex: index, block: block});
                    callback(null, gmb);
                }
            });
    }
};

CryptoProxy.prototype.checkBlock_ = function() {
    var self = this;
    self.getNextCCBlock_(function(error, ccblock) {
        if (error || !ccblock) {
            self.checkBlockAfterDelay_();
        } else {
            var response = new CryptoCurrencyBlockMessage({block: ccblock});
            self.emit(CryptoProxy.EventType.BLOCK_ARRIVED,
                self.makeNormalResponse_(BitwayResponseType.AUTO_REPORT_BLOCKS, self.currency, response));
            self.checkBlockAfterDelay_(0);
        }
    });
};

CryptoProxy.prototype.getNextCCBlock_ = function(callback) {
    var self = this;
    Async.compose(self.getNextCCBlockSinceLastIndex_.bind(self), self.getLastIndex_.bind(self))(callback);
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
    self.getBlockCount_(function(error, count) {
        if (error) {
            callback(error);
        } else if (index == count) {
            callback('no new block found');
        } else {
            var nextIndex = (index == -1) ? count : index + 1;
            self.redis.del(self.getProcessedSigidsByHeight_(nextIndex - 1), function() {});
            self.redis.set(self.lastIndex, nextIndex, function(error, replay) {
                if (!error) {
                    self.getCCBlockByIndex_(nextIndex, callback);
                } else {
                    callback(error);
                }
            });
        }
    });
};

CryptoProxy.prototype.getNewCCTXsSinceLatest_ = function(callback) {
    var self = this;
    Async.compose(self.getNewCCTXsFromTxids_.bind(self),
        self.getTxidsSinceBlockHash_.bind(self),
        self.getBlockHash_.bind(self),
        self.getBlockCount_.bind(self))(callback);
};

CryptoProxy.prototype.getBlockCount_ = function(callback) {
    this.rpc.getBlockCount(function(error, count) {
        CryptoProxy.invokeCallback_(error, function() {return count.result}, callback);
    });
};

CryptoProxy.prototype.generateOneAddress_ = function(unusedIndex, callback) {
    this.rpc.getNewAddress(CryptoProxy.ACCOUNT, function(error, address) {
        CryptoProxy.invokeCallback_(error, function() {return address.result}, callback);
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
            var txids = txs.result.transactions.map(function(element) {return element.txid});
            callback(null, hash.index, txids);
        }
    });
};

CryptoProxy.prototype.getCCBlockByIndex_ = function(index, callback) {
    var self = this;
    Async.compose(self.getCCBlockFromBlockInfo_.bind(self), self.getBlockByIndex_.bind(self))(index, callback);
};

CryptoProxy.prototype.getBlockHash_ = function(index, callback) {
    this.rpc.getBlockHash(index, function(error, hash) {
        if (error) {
            callback(error);
        } else {
            callback(null, {'index': index, 'hash': hash.result});
        }
    });
};

CryptoProxy.prototype.getBlock_ = function(hash, callback) {
    this.rpc.getBlock(hash.hash, function(error, block) {
        CryptoProxy.invokeCallback_(error, function() {return block.result}, callback);
    });
};

CryptoProxy.prototype.getBlockByIndex_ = function(index, callback) {
    var self = this;
    Async.compose(self.getBlock_.bind(self),
        self.getBlockHash_.bind(self))(index, callback);
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
    if (vinItem.txid) { this.rpc.getRawTransaction(vinItem.txid, 1, function(error, tx) {
        if (error) {
            callback(error);
        } else {
            var outIndexes = tx.result.vout.filter(function(element) {return element.n == vinItem.vout});
            if (outIndexes.length > 0 && outIndexes[0].scriptPubKey && outIndexes[0].scriptPubKey.addresses) {
                var input = new CryptoCurrencyTransactionPort();
                input.address = outIndexes[0].scriptPubKey.addresses.toString();
                input.amount = outIndexes[0].value;
                callback(null, input);
            } else {
                callback('can\'t find the previous tx\'s output index for input');
            }
        }});
    } else {
        var input = new CryptoCurrencyTransactionPort();
        input.address = 'coinbase';
        input.amount = 0;
        callback(null, input);
    }
};

CryptoProxy.prototype.getSigId_ = function(cctx, vinTxids) {
    var self = this;
    var sigId = vinTxids;
    for(var m = 0; m < cctx.inputs.length; m++){
        sigId += cctx.inputs[m].address;
        sigId += cctx.inputs[m].amount;
    }
    for(var n = 0; n < cctx.outputs.length; n++){
        sigId += cctx.outputs[n].address;
        sigId += cctx.outputs[n].amount;
    }
    var sha256 = Crypto.createHash('sha256');
    sha256.update(sigId);
    return sha256.digest('hex');
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
            self.redis.get(sigId, function(error, ids) {
                if (!error && ids != undefined && ids != null)
                    cctx.ids = ids;
                callback(null, cctx);
            });
        }
    });
};

CryptoProxy.prototype.getCCTXFromTxid_ = function(txid, callback) {
    var self = this;
    self.rpc.getRawTransaction(txid, 1, function(error, tx) {
        if (error) {
            self.log.error(error);
            callback(error);
        } else {
            self.getCCTXFromTx_(tx.result, callback);
        }
    });
};

CryptoProxy.prototype.getCCBlockFromBlockInfo_ = function(block, callback) {
    var self = this;
    var index = new BlockIndex({id: block.hash, height:block.height});
    var prevIndex = new BlockIndex({id: block.previousblockhash, height:block.height - 1});
    Async.map(block.tx, self.getCCTXFromTxid_.bind(self), function(error, cctxs) {
        CryptoProxy.invokeCallback_(error,
            function() {return new CryptoCurrencyBlock({index:index, prevIndex:prevIndex, txs:cctxs})}, callback);
    });
};

CryptoProxy.prototype.makeNormalResponse_ = function(type, currency, response) {
    this.log.info("response: " + JSON.stringify(response));
    switch (type) {
        case BitwayResponseType.GENERATE_ADDRESS:
            return new BitwayMessage({currency: currency, generateAddressResponse: response});
        case BitwayResponseType.TRANSFER:
        case BitwayResponseType.TRANSACTION:
            return new BitwayMessage({currency: currency, tx: response});
        case BitwayResponseType.GET_MISSED_BLOCKS:
        case BitwayResponseType.AUTO_REPORT_BLOCKS:
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
