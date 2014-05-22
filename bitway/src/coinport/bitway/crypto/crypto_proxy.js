/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

'use strict'

var Async                     = require('async'),
    Bitcore                   = require('bitcore'),
    Events                    = require('events'),
    Util                      = require("util"),
    Crypto                    = require('crypto'),
    Redis                     = require('redis'),
    DataTypes                 = require('../../../../gen-nodejs/data_types'),
    MessageTypes              = require('../../../../gen-nodejs/message_types'),
    BitwayMessage             = MessageTypes.BitwayMessage,
    BitwayResponseType        = DataTypes.BitwayResponseType,
    ErrorCode                 = DataTypes.ErrorCode,
    GenerateAddressesResult   = MessageTypes.GenerateAddressesResult,
    TransferStatus            = DataTypes.TransferStatus,
    CryptoCurrencyAddressType = DataTypes.CryptoCurrencyAddressType;

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
};
Util.inherits(CryptoProxy, Events.EventEmitter);

CryptoProxy.ACCOUNT = 'customers';
CryptoProxy.HOT_ACCOUNT = "hot";
CryptoProxy.TIP = 0.0001;
CryptoProxy.MIN_GENERATE_ADDR_NUM = 1;
CryptoProxy.MAX_GENERATE_ADDR_NUM = 1000;
CryptoProxy.MAX_CONFIRM_NUM = 9999999;

CryptoProxy.EventType = {
    TX_ARRIVED : 'tx_arrived',
    BLOCK_ARRIVED : 'block_arrived'
};

CryptoProxy.prototype.generateUserAddress = function(request, callback) {
    var self = this;
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
    cryptoProxy.log.info('** TransferRequest Received **');
    Async.compose(self.getCCTXFromTxid_.bind(self),
        self.sendTransaction_.bind(self),
        self.signTransaction_.bind(self),
        self.createRawTransaction_.bind(self),
        self.constructRawTransaction_.bind())(transferreq, function(error, cctx) {
        if (!error) {
            callback(self.makeNormalResponse_(BitwayResponseType.TRANSACTION, self.currency, cctx));
        } else {
            var response = new CryptoCurrencyTransaction({status: TransferStatus.REORGING});
            self.log.error("Transfer failed! sigId: " + response.sigId);
            callback(self.makeNormalResponse_(BitwayResponseType.TRANSACTION, self.currency, response));
        }
    });
};

CryptoProxy.prototype.constructRawTransaction_ = function(transferReq, callback) {
    var self = this;
    switch(request.type){
        case TransferType.WITHDRAWAL:
        case TransferType.HOT_TO_COLD:
            Async.parallel ([
                self.getUnspent.bind(self)(),
                self.getChangeAddress.bind(self)
                ], function(err, result){
                if (!err) {
                    var unspentTxs = result[0];
                    var changeAddress = result[1];
                    var amountUnspent = 0;
                    var transactions = [];
                    var amountTotalPay = calTotalPay(transferReq);
                    for (var i = 0; i < unspentTxs.length; i++) {
                        amountUnspent += unspentTxs[i].amount;
                        var transaction = {txid: unspentTxs[i].txid, vout: unspentTxs[i].vout};
                        transactions.push(transaction);
                        if (amountUnspent > (amountTotaloPay + self.TIP)) {
                            addresses[changeAddress] = amountUnspent - amountTotalPay - self.TIP;
                            break;
                        } else if((amountUnspent < (amountTotalPay + self.TIP) && amountUnspent > amountTotalPay)
                                || amountUnspent == (amountTotalPay + self.TIP)) {
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
    }
};

CryptoProxy.prototype.createRawTransaction_ = function(rawData, callback) {
    var self = this;
    var transactions = rawData.transactions;
    var addresses = rawData.addresses;
    this.rpc.createRawTransaction(transactions, addresses, function(error, createReply) {
        if (error) {
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
            callback(error);
        } else {
            callback(null, sendReply.result);
        }
    });
};

CryptoProxy.prototype.calTotalPay = function(transferReq) {
    var amountTotalPay = 0;
    for (var i = 0; i < transferReq.transferInfos.length; i++) {
        amountTotal += transferReq.transferInfos[i].amount;
    }
    return amountTotal;
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

CryptoProxy.prototype.checkBlock_ = function() {
    var self = this;
    self.getNextCCBlock_(function(error, ccblock) {
        if (error || !ccblock) {
            self.checkBlockAfterDelay_();
        } else {
            self.emit(CryptoProxy.EventType.BLOCK_ARRIVED,
                self.makeNormalResponse_(BitwayResponseType.AUTO_REPORT_BLOCKS, self.currency, ccblock));
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
            self.redis.del(self.processedSigids, function() {});
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
        CryptoProxy.invokeCallback(error, function() {return count.result}, callback);
    });
};

CryptoProxy.prototype.generateOneAddress_ = function(unusedIndex, callback) {
    this.rpc.getNewAddress(CryptoProxy.ACCOUNT, function(error, address) {
        CryptoProxy.invokeCallback(error, function() {return address.result}, callback);
    });
};

CryptoProxy.prototype.getNewCCTXsFromTxids_ = function(txids, callback) {
    var self = this;
    Async.map(txids, self.getCCTXFromTxid_.bind(self), function(error, cctxs) {
        if (error) {
            callback(error);
        } else {
            self.redis.smembers(self.processedSigids, function(error, sigIds) {
                if (error) {
                    callback(error);
                } else {
                    var sigStrIds = sigIds.map(function(element) {return String(element)});
                    var newCCTXs = cctxs.filter(function(element) {return sigStrIds.indexOf(element.sigId) == -1;});
                    self.redis.sadd(self.processedSigids, newCCTXs.map(function(element) {return element.sigId}),
                        function(error, replay) {
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
    this.rpc.listSinceBlock(hash, function(error, txs) {
        if (error) {
            callback(error);
        } else {
            var txids = txs.result.transactions.map(function(element) {return element.txid});
            callback(null, txids);
        }
    });
};

CryptoProxy.prototype.getCCBlockByIndex_ = function(index, callback) {
    var self = this;
    Async.compose(self.getCCBlockFromBlockInfo_.bind(self), self.getBlockByIndex_.bind(self))(index, callback);
};

CryptoProxy.prototype.getBlockHash_ = function(index, callback) {
    this.rpc.getBlockHash(index, function(error, hash) {
        CryptoProxy.invokeCallback(error, function() {return hash.result}, callback);
    });
};

CryptoProxy.prototype.getBlock_ = function(hash, callback) {
    this.rpc.getBlock(hash, function(error, block) {
        CryptoProxy.invokeCallback(error, function() {return block.result}, callback);
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
            callback(error);
        } else {
            var cctx = new CryptoCurrencyTransaction({txid: tx.txid, inputs: rawInputs, outputs: retOutputs,
                status: TransferStatus.CONFIRMING});
            var sigId = self.getSigId_(cctx, vinTxids);
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
        CryptoProxy.invokeCallback(error,
            function() {return new CryptoCurrencyBlock({index:index, prevIndex:prevIndex, txs:cctxs})}, callback);
    });
};

CryptoProxy.prototype.makeNormalResponse_ = function(type, currency, response) {
    switch (type) {
        case BitwayResponseType.GENERATE_ADDRESS:
            return new BitwayMessage({currency: currency, generateAddressResponse: response});
        case BitwayResponseType.TRANSFER:
        case BitwayResponseType.TRANSACTION:
            return new BitwayMessage({currency: currency, tx: response});
        case BitwayResponseType.GET_MISSED_BLOCKS:
        case BitwayResponseType.AUTO_REPORT_BLOCKS:
            return new BitwayMessage({currency: currency, blocksMsg: response});
        default:
            console.log("Inavalid Type!");
            return null
    }
};

CryptoProxy.invokeCallback = function(error, resultFun, callback) {
    if (error) {
        callback(error);
    } else {
        callback(null, resultFun());
    }
};
