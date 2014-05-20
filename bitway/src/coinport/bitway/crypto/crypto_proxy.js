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
 * @param {Map{...}} config The config for CryptoProxy
 *     {
 *       cryptoRpc: xxx,
 *       checkInterval: 5000,
 *       redis: xxx,
 *       minConfirm: 1
 *     }
 * @constructor
 * @extends {Events.EventEmitter}
 */
var CryptoProxy = module.exports.CryptoProxy = function(currency, config) {
    Events.EventEmitter.call(this);

    this.currency = currency;
    this.minConfirm = config.minConfirm;
    this.rpc = config.cryptoRpc;
    this.redis = config.redis;
};
Util.inherits(CryptoProxy, Events.EventEmitter);

CryptoProxy.ACCOUNT = 'customers';
CryptoProxy.HOT_ACCOUNT = "hot";
CryptoProxy.TIP = 0.0001;
CryptoProxy.MIN_GENERATE_ADDR_NUM = 1;
CryptoProxy.MAX_GENERATE_ADDR_NUM = 1000;
CryptoProxy.MIN_CONFIRM_NUM = 0;
CryptoProxy.MAX_CONFIRM_NUM = 9999999;

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

CryptoProxy.prototype.generateOneAddress_ = function(unusedIndex, callback) {
    this.rpc.getNewAddress(CryptoProxy.ACCOUNT, function(error, address) {
        CryptoProxy.invokeCallback(error, function() {return address.result}, callback);
    });
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
    Async.compose(self.getBlock_.bind(self), self.getBlockHash_.bind(self))(index, callback);
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

CryptoProxy.prototype.getSigId_ = function(cctx) {
    var sigId = cctx.txid;
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
    var retOutputs = self.getOutputAddresses_(tx);
    Async.map(tx.vin, self.getInputAddress_.bind(self), function(error, rawInputs) {
        if (error) {
            callback(error);
        } else {
            var cctx = new CryptoCurrencyTransaction({txid: tx.txid, inputs: rawInputs, outputs: retOutputs,
                status: TransferStatus.Confirming});
            var sigId = self.getSigId_(cctx);
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

CryptoProxy.prototype.getCCBlockByIndex_ = function(index, callback) {
    var self = this;
    Async.compose(self.getCCBlockFromBlockInfo_.bind(self), self.getBlockByIndex_.bind(self))(index, callback);
};

CryptoProxy.prototype.makeNormalResponse_ = function(type, currency, response) {
    switch (type) {
        case BitwayResponseType.GENERATE_ADDRESS:
            return new BitwayMessage({currency: currency, generateAddressResponse: response});
        /*
        case BitwayResponseType.TRANSFER:
        case BitwayResponseType.TRANSACTION:
            console.log("TRANSACTION REPORT: " + currency);
            displayTxContent(response);
            redisProxy.publish(new BitwayMessage({currency: currency, tx: response}));
            break;
        case BitwayResponseType.GET_MISSED_BLOCKS:
        case BitwayResponseType.AUTO_REPORT_BLOCKS:
            console.log("BLOCK REPORT: " + currency);
            console.log("response.blocks.length:" + response.blocks.length);
            displayBlocksContent(response.blocks);
            redisProxy.publish(new BitwayMessage({currency: currency, blocksMsg: response}));
            break;
        */
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
