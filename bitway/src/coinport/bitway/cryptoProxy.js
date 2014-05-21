/**
 *Copyright 2014 Coinport Inc. All Rights Reserved.
 *Author: YangLi (yangli@coinport.com)
 *Filename: cryptoProxy.js
 *Description: 
 */

'use strict'

var RedisProxy                      = require('./redis/redis_proxy').RedisProxy,
    Redis                           = require('redis'),
    DataTypes                       = require('../../../gen-nodejs/data_types'),
    MessageTypes                    = require('../../../gen-nodejs/message_types'),
    Bitcore                         = require('bitcore'),
    Crypto                          = require('crypto'),
    BitwayRequestType               = DataTypes.BitwayRequestType,
    BitwayResponseType              = DataTypes.BitwayResponseType,
    GenerateAddressesResult         = MessageTypes.GenerateAddressesResult,
    CryptoCurrencyTransaction       = DataTypes.CryptoCurrencyTransaction,
    CryptoCurrencyTransactionPort   = DataTypes.CryptoCurrencyTransactionPort,
    CryptoCurrencyBlock             = DataTypes.CryptoCurrencyBlock,
    TransferStatus                  = DataTypes.TransferStatus,
    TransferType                    = DataTypes.TransferType,
    CryptoCurrencyAddressType       = DataTypes.CryptoCurrencyAddressType,
    Currency                        = DataTypes.Currency,
    BlockIndex                      = DataTypes.BlockIndex,
    CryptoCurrencyBlocksMessage     = MessageTypes.CryptoCurrencyBlocksMessage,
    ErrorCode                       = DataTypes.ErrorCode,
    BitwayMessage                   = MessageTypes.BitwayMessage,
    Peer                            = Bitcore.Peer,
    Networks                        = Bitcore.networks,
    Logger                          = require('./logger');


var CryptoProxy = module.exports.CryptoProxy = function(currency, rpcConfig, minConfirmNum, redisProxy) {
    this.currency = currency;
    this.rpc = new Bitcore.RpcClient(rpcConfig);
    this.ACCOUNT = "customers";
    this.HOT_ACCOUNT = "coinportTest";
    this.tip = 0.0001;
    this.MIN_GENERATE_ADDR_NUM = 1;
    this.MAX_GENERATE_ADDR_NUM = 1000;
    this.needJson = 1;
    this.lastReportBlockIndex = currency + "lastReportBlockIndex";
    this.MIN_CONFIRM_NUM = minConfirmNum;
    this.MAX_CONFIRM_NUM = 9999999;
    this.innerRedis = Redis.createClient('6379', '127.0.0.1', { return_buffers: true });
    this.redisProxy = redisProxy;
    this.log = Logger.logger(currency.toString());
};

CryptoProxy.prototype.checkTx = function(cryptoProxy){
    cryptoProxy.log.info('** CHECK TX **' + cryptoProxy.currency + " begin time: " + (new Date().toLocaleString()));
    var rpc = cryptoProxy.rpc;
    rpc.getBlockCount(function(errCount,retCount){
        if(errCount){
            cryptoProxy.log.info("errCount code: " + errCount.code);
            cryptoProxy.log.info("errCount message: " + errCount.message);
        }else{
            cryptoProxy.log.info("check tx in block: " + retCount.result);
            getTxsSinceBlock(cryptoProxy, retCount.result);
        }
    });
};

CryptoProxy.prototype.checkBlock = function(cryptoProxy){
    cryptoProxy.log.info('** CHECK_BLOCK **' + "begin Time: " + (new Date().toLocaleString()));
    var rpc = cryptoProxy.rpc;
    cryptoProxy.innerRedis.get(cryptoProxy.lastReportBlockIndex, function(errLastIndex, retLastIndex){
        rpc.getBlockCount(function(errCount,retCount){
            if(errCount){
                cryptoProxy.log.info("errCount code: " + errCount.code);
                cryptoProxy.log.info("errCount message: " + errCount.message);
            }else{
                cryptoProxy.log.info("current block index: " + retCount.result);
                cryptoProxy.log.info("last report block index: " + retLastIndex);
                if(retLastIndex == null){
                    getBlockByIndex(cryptoProxy, retCount.result);
                }else{
                    if(!isNaN(retLastIndex) && retLastIndex < retCount.result){
                        cryptoProxy.log.info("Behind the newest: " + (retCount.result - retLastIndex));
                        var checkBlockIndex = Number(retLastIndex) + Number(1);
                        getBlockByIndex(cryptoProxy, checkBlockIndex);
                    }else{
                        cryptoProxy.log.info("The newest block has already been reported!");
                    }
                }
            }
        });
    });
};

CryptoProxy.prototype.generateUserAddress = function(cryptoProxy, request, redisProxy) {
    var startTime = new Date().getTime();
    var rpc = cryptoProxy.rpc;
    cryptoProxy.log.info(RedisProxy.EventType.GENERATE_ADDRESS);
    cryptoProxy.log.info(cryptoProxy.currency);
    cryptoProxy.log.info(request);
    if (request.num < cryptoProxy.MIN_GENERATE_ADDR_NUM || request.num > cryptoProxy.MAX_GENERATE_ADDR_NUM) {
        var generateAddressResponse = new GenerateAddressesResult({error: ErrorCode.INVALID_REQUEST_ADDRESS_NUM});
        makeNormalResponse(BitwayResponseType.GENERATE_ADDRESS, cryptoProxy, generateAddressResponse);
    } else {
        var addresses = [];
        for (var i = 0; i < request.num; i++) {
            rpc.getNewAddress(cryptoProxy.ACCOUNT, function(errAddress, retAddress) {
                if (errAddress) {
                    console.error('An error occured generate address');
                    console.error(errAddress);
                    var generateAddressResponse = new GenerateAddressesResult({error: ErrorCode.RPC_ERROR});
                    makeNormalResponse(BitwayResponseType.GENERATE_ADDRESS, cryptoProxy, 
                        generateAddressResponse, redisProxy);
                    return;
                }
                var address = retAddress.result;
                addresses.push(address);
                if (addresses.length == request.num) {
                    cryptoProxy.log.info("addresses: " + addresses);
                    var generateAddressResponse = new GenerateAddressesResult({error: ErrorCode.OK,
                            addresses: addresses, addressType: CryptoCurrencyAddressType.UNUSED});
                    makeNormalResponse(BitwayResponseType.GENERATE_ADDRESS, cryptoProxy, 
                        generateAddressResponse, redisProxy);
                    cryptoProxy.log.info("costTime: " + (new Date().getTime() - startTime) + "ms");
                }
            });
        }
    }
};

CryptoProxy.prototype.getMissedBlocks = function(cryptoProxy, request, redisProxy) {
    cryptoProxy.log.info(RedisProxy.EventType.GET_MISSED_BLOCKS);
    cryptoProxy.log.info("endIndex hash:" + request.endIndex.id);
    var rpc = cryptoProxy.rpc;
    var blocksFinishLength = request.endIndex.height - request.startIndexs[0].height +1;
    var blocksMsg = new CryptoCurrencyBlocksMessage({blocks:[]});
    for(var iHeight = request.startIndexs[0].height; iHeight < request.endIndex.height + 1; iHeight++){
        cryptoProxy.log.info("iHeight: " + iHeight);
        rpc.getBlockHash(iHeight, function(errHash, retHash){
            cryptoProxy.log.info("block hash: " + retHash.result);
            rpc.getBlock(retHash.result, function(errBlock, retBlock){
                if(errBlock){
                    cryptoProxy.log.info("errBlock code: " + errBlock.code);
                    cryptoProxy.log.info("errBlock message: " + errBlock.message);
                }else{
                    var index = new BlockIndex({id: retBlock.result.hash, height:retBlock.result.height});
                    cryptoProxy.log.info(index.id);
                    var prevIndex = new BlockIndex({id:retBlock.result.previousblockhash, height:retBlock.result.height - 1});
                    var txs = [];
                    var block = new CryptoCurrencyBlock({index:index, prevIndex:prevIndex, txs:txs});
                    for(var i = 0; i < retBlock.result.tx.length; i++){
                        rpc.getRawTransaction(retBlock.result.tx[i], cryptoProxy.needJson, function(errTx, retTx){
                            if(errTx){
                                cryptoProxy.log.info("errTx code: " + errBlock.code);
                                cryptoProxy.log.info("errTx message: " + errBlock.message);
                            }else{
                                //cryptoProxy.log.info("txid: " + retTx.result.txid);
                                var cctx = new CryptoCurrencyTransaction({inputs: [], outputs: [],
                                    status: TransferStatus.Confirming});
                                cctx.txid = retTx.result.txid;
                                getOutputAddresses(retTx.result, cctx);
                                for(var j = 0; j < retTx.result.vin.length; j++){
                                    //cryptoProxy.log.info("vout: " + retTx.result.vin[j].vout);
                                    constructBlocks(cryptoProxy, redisProxy, retTx.result.vin[j], retTx.result.vin.length,
                                        retBlock.result.tx.length, blocksFinishLength, request, cctx, block, blocksMsg);
                                }
                            }
                        });
                    }
                }
            });
        });
    }
};

CryptoProxy.prototype.transfer = function(cryptoProxy, request, redisProxy) {
    cryptoProxy.log.info('** TransferRequest Received **');
    cryptoProxy.log.info(RedisProxy.EventType.TRANSFER);
    cryptoProxy.log.info(cryptoProxy.currency);
    switch(request.type){
        case TransferType.WITHDRAWAL:
        case TransferType.HOT_TO_COLD:
            txWithDefiniteTo(cryptoProxy, request, redisProxy);
            break;
        case TransferType.USER_TO_HOT:
            txWithDefiniteFrom(cryptoProxy, request, redisProxy);
            break;
        default:
            cryptoProxy.log.info("Invalid request type: " + request.type);
    }
};

var txWithDefiniteFrom = function(cryptoProxy, request, redisProxy){
    var amountTotal = 0;
    var addresses = {};
    var transactions = [];
    var fromAddresses = [];
    var ids = [];
    var rpc = cryptoProxy.rpc;
    rpc.getAddressesByAccount(cryptoProxy.HOT_ACCOUNT, function(errAddr, retAddr){
        if(errAddr){
        }else{
            if(retAddr.result.length == 0){
                for(var m = 0; m < 10; m++)
                {
                    rpc.getNewAddress(cryptoProxy.HOT_ACCOUNT, function(errAddress, retAddress) {
                        if (errAddress) {
                            console.error('An error occured generate hot address');
                            console.error(errAddress);
                        }else{
                            var addresses = [];
                            addresses.push(retAddress.result);
                            var generateAddressResponse = new GenerateAddressesResult({error: ErrorCode.OK,
                                    addresses: addresses, addressType: CryptoCurrencyAddressType.HOT});
                            makeNormalResponse(BitwayResponseType.GENERATE_ADDRESS, cryptoProxy, 
                                generateAddressResponse, redisProxy);
                            for(var i = 0; i < request.transferInfos.length; i++){
                                request.transferInfos[i].to = retAddress.result;
                                makeTransaction(cryptoProxy, request.transferInfos[i], request.transferInfos.length,
                                    fromAddresses, transactions, addresses, ids, redisProxy);
                            }
                        }
                    });
                }
            }else{
                var toPos = Math.floor(Math.random()*retAddr.result.length);
                var toAddress = retAddr.result[toPos];
                cryptoProxy.log.info("toPos: " + toPos + " toAddr: " + toAddress);
                for(var i = 0; i < request.transferInfos.length; i++){
                    request.transferInfos[i].to = toAddress;
                    makeTransaction(cryptoProxy, request.transferInfos[i], request.transferInfos.length,
                        fromAddresses, transactions, addresses, ids, redisProxy);
                }
            }
        }
    });
}

var makeTransaction = function(cryptoProxy, transferInfo, finishLength, fromAddresses, transactions, 
        addresses, ids, redisProxy){
    var rpc = cryptoProxy.rpc;
    var from = transferInfo.from;
    var to = transferInfo.to;
    var amountPay = transferInfo.amount;
    var minConfirmedNum = cryptoProxy.MIN_CONFIRM_NUM;
    var maxConfirmedNum = cryptoProxy.MAX_CONFIRM_NUM;
    var fromArray = [from];
    cryptoProxy.log.info("from: " + fromArray[0]);
    rpc.listUnspent(minConfirmedNum, maxConfirmedNum, fromArray, function(errUnspent, retUnspent){
        if(errUnspent){
            cryptoProxy.log.info("errUnspent code: " + errUnspent.code);
            cryptoProxy.log.info("errUnspent message: " + errUnspent.message);
        }else{
            var amountUnspent = 0;
            cryptoProxy.log.info("retUnspent.length: " + retUnspent.result.length);
            for(var j = 0; j < retUnspent.result.length; j++){
                cryptoProxy.log.info("txid: " + retUnspent.result[j].txid);
                cryptoProxy.log.info("vout: " + retUnspent.result[j].vout);
                var transaction = {
                    txid: retUnspent.result[j].txid,
                    vout: retUnspent.result[j].vout,
                    };
                transactions.push(transaction);
                amountUnspent += retUnspent.result[j].amount;
                if(amountUnspent > amountPay || amountUnspent == amountPay){
                    break;
                }
            }
            fromAddresses.push(from);
            ids.push(transferInfo.id);
            addresses[to] = amountPay;
            if(amountUnspent > amountPay){
                addresses[from] = amountUnspent - amountPay;
            }
            if(fromAddresses.length == finishLength){
                finishTransfer(cryptoProxy, transactions, addresses, ids, redisProxy);
            }
        }
    });
};

var txWithDefiniteTo = function(cryptoProxy, request, redisProxy){
    var minConfirmedNum = cryptoProxy.MIN_CONFIRM_NUM;
    var maxConfirmedNum = cryptoProxy.MAX_CONFIRM_NUM;
    var amountTotal = 0;
    var addresses = {};
    var ids = [];
    var rpc = cryptoProxy.rpc;
    var tip = cryptoProxy.tip;
    for(var i = 0; i < request.transferInfos.length; i++){
        amountTotal += request.transferInfos[i].amount;
        ids.push(request.transferInfos[i].id);
    }
    rpc.listUnspent(minConfirmedNum, maxConfirmedNum, function(err, ret){
        if(err){
            console.error('An error occured listUnspent', hash);
            console.error(err);
            return;
        }else{
            var result = ret.result;
            if(result.length == 0){
            //TODO:
                cryptoProxy.log.info("There is nothing to spend!");
            }else{
                var amountCanUse = 0;
                var i = 0;
                var transactions = [];
                rpc.getAddressesByAccount(cryptoProxy.HOT_ACCOUNT, function(err, ret){
                    if(err){
                    }else{
                        var changePos = Math.floor(Math.random()*ret.result.length);
                        cryptoProxy.log.info("changePos: " + changePos);
                        var changeAddress = ret.result[changePos];
                        for(i = 0; i < result.length; i++){
                            amountCanUse += result[i].amount;
                            var transaction = {
                                txid: result[i].txid,
                                vout: result[i].vout,
                                };
                            transactions.push(transaction);
                            if(amountCanUse > (amountTotal + tip)){//TODO:
                                for(var j =0; j < request.transferInfos.length; j++)
                                {
                                    addresses[request.transferInfos[j].to] = request.transferInfos[j].amount;
                                }
                                addresses[changeAddress] = amountCanUse - amountTotal - tip;
                                break;
                            }else if(amountCanUse == (amount + tip)){
                                for(var j =0; j < request.transferInfos.length; j++)
                                {
                                    addresses[request.transferInfos[j].to] = request.transferInfos[j].amount;
                                }
                                break;
                            }
                        }
                        if(i <= result.length){
                            finishTransfer(cryptoProxy, transactions, addresses, ids, redisProxy);
                        }
                    }
                });
            }
        }
    });
};

var getTransactionInfo = function(cryptoProxy, txid, ids, redisProxy){
    var rpc = cryptoProxy.rpc;
    cryptoProxy.log.info("txid: " + txid);
    rpc.getRawTransaction(txid, cryptoProxy.needJson, function(err,ret){
        if(err){
            cryptoProxy.log.info("fail code: " + err.code);
            cryptoProxy.log.info("fail message: " + err.message);
        }else{
            var cctx = new CryptoCurrencyTransaction({inputs: [], outputs: [],
                status: TransferStatus.Confirming});
            cctx.txid = ret.result.txid;
            cryptoProxy.log.info("txid: " + ret.result.txid);
            getOutputAddresses(ret.result, cctx);
            for(var i = 0; i < ret.result.vin.length; i++){
                cryptoProxy.log.info("vout: " + ret.result.vin[i].vout);
                saveTransferIds(cryptoProxy, ret.result.vin[i], cctx, ret.result.vin.length, ids, redisProxy);
            }
        }
    });
}

var saveTransferIds = function(cryptoProxy, input, cctx, finishLength, ids, redisProxy){
    cryptoProxy.log.info("saveTransferIds input-txid: " + input.txid);
    cryptoProxy.log.info("finishLength: " + finishLength);
    cryptoProxy.log.info("input-vout: " + input.vout);
    var rpc = cryptoProxy.rpc;
    var vout = input.vout;
    rpc.getRawTransaction(input.txid, cryptoProxy.needJson, function(errIn,retIn){
        if(errIn){
            cryptoProxy.log.info("errIn code: " + errIn.code);
            cryptoProxy.log.info("errIn message: " + errIn.message);
        }else{
            for(var j = 0; j < retIn.result.vout.length; j++){
                if(vout == retIn.result.vout[j].n){
                    cryptoProxy.log.info("success match: " + retIn.result.vout[j].n);
                    var input = new CryptoCurrencyTransactionPort();
                    input.address = retIn.result.vout[j].scriptPubKey.addresses.toString();
                    input.amount = retIn.result.vout[j].value;
                    cryptoProxy.log.info("input.address: " + input.address);
                    cctx.inputs.push(input);
                }
            }
            cryptoProxy.log.info("cctx.outputs.length: " + cctx.outputs.length);
            if(cctx.inputs.length == finishLength){
                var sigId = getSigId(cctx); 
                cryptoProxy.innerRedis.set(sigId, ids, function(errRedis, reply){
                    if(errRedis){
                        cryptoProxy.log.info("errRedis: " + errRedis);
                    }else{
                        cryptoProxy.log.info("cctx.sigId: " + cctx.sigId);
                        cctx.ids = ids;
                        makeNormalResponse(BitwayResponseType.TRANSACTION, cryptoProxy, cctx, redisProxy);
                    }
                });
            }
        }
    });
};


var finishTransfer = function(cryptoProxy, transactions, addresses, ids, redisProxy){
    var rpc = cryptoProxy.rpc;
    for(var i = 0; i < transactions.length; i++)
    {
        cryptoProxy.log.info("tx.txid: " + transactions[i].txid);
        cryptoProxy.log.info("tx.vout: " + transactions[i].vout);
    }
    cryptoProxy.log.info(addresses);
    rpc.createRawTransaction(transactions, addresses, function(errCreate, retCreate){
        if(errCreate){
            cryptoProxy.log.info("errCreate code: " + errCreate.code);
            cryptoProxy.log.info("errCreate message: " + errCreate.message);
        }else{
            cryptoProxy.log.info("transaction: " + retCreate.result);
            rpc.signRawTransaction(retCreate.result, function(errSign, retSign){
                if(errSign){
                    cryptoProxy.log.info("errSign code: " + errSign.code);
                    cryptoProxy.log.info("errSign message: " + errSign.message);
                }else{
                    rpc.sendRawTransaction(retSign.result.hex, function(errSend, retSend){
                        cryptoProxy.log.info("send hex: " + retSign.result.hex);
                        if(errSend){
                            cryptoProxy.log.info("errSend code: " + errSend.code);
                            cryptoProxy.log.info("errSend message: " + errSend.message);
                        }else{
                            getTransactionInfo(cryptoProxy, retSend.result, ids, redisProxy);
                        }
                    });
                }
            });
         }
    });
}

var constructBlocks = function(cryptoProxy, redisProxy, input, txFinishLength, blockFinishLength, blocksFinishLength, 
        request, cctx, block, blocksMsg){
    cryptoProxy.log.info("input-txid: " + input.txid);
    cryptoProxy.log.info("txFinishLength: " + txFinishLength);
    cryptoProxy.log.info("blockFinishLength: " + blockFinishLength);
    cryptoProxy.log.info("blocksFinishLength: " + blocksFinishLength);
    cryptoProxy.log.info("input-vout: " + input.vout);
    cryptoProxy.log.info("block.index: " + block.index.id);
    var rpc = cryptoProxy.rpc;
    var vout = input.vout;
    if(input.txid != undefined){
        rpc.getRawTransaction(input.txid, cryptoProxy.needJson, function(errIn,retIn){
            if(errIn){
                cryptoProxy.log.info("errIn code: " + err.code);
                cryptoProxy.log.info("errIn message: " + err.message);
            }else{
                for(var j = 0; j < retIn.result.vout.length; j++){
                    if(vout == retIn.result.vout[j].n){
                        //cryptoProxy.log.info("success match: " + retIn.result.vout[j].n);
                        var input = new CryptoCurrencyTransactionPort();
                        input.address = retIn.result.vout[j].scriptPubKey.addresses.toString();
                        input.amount = retIn.result.vout[j].value;
                        //cryptoProxy.log.info("success match input.address: " + input.address);
                        cctx.inputs.push(input);
                        break;
                    }
                }
                cryptoProxy.log.info("cctx.inputs.length: " + cctx.inputs.length);
                if(cctx.inputs.length == txFinishLength){
                    cryptoProxy.log.info("block.txs.length: " + block.txs.length);
                    cryptoProxy.log.info("blockFinishLength: " + blockFinishLength);
                    var sigId = getSigId(cctx);
                    cryptoProxy.innerRedis.get(sigId, function(errRedis, reply){
                        if(errRedis){
                            cryptoProxy.log.error("errRedis: " + errRedis);
                        }else{
                            if(reply != null){
                                cctx.ids = reply;
                            }
                            block.txs.push(cctx);
                            if(block.txs.length == blockFinishLength){
                                cryptoProxy.log.info("*****blocksMsg.blocks.length: " + blocksMsg.blocks.length);
                                blocksMsg.blocks.push(block);
                                if(blocksMsg.blocks.length == blocksFinishLength){
                                    makeFinalBlocksResponse(cryptoProxy, redisProxy, request, blocksMsg);
                                }
                            }
                        }
                    });
                }
            }
        });
    }else{
        var input = new CryptoCurrencyTransactionPort();
        input.address = "coinbase";
        input.amount = 0;
        cryptoProxy.log.info("input.address: " + input.address);
        cctx.inputs.push(input);
        if(cctx.inputs.length == txFinishLength){
            cryptoProxy.log.info("block.txs.length: " + block.txs.length);
            cryptoProxy.log.info("blockFinishLength: " + blockFinishLength);
            var sigId = getSigId(cctx);
            cryptoProxy.innerRedis.get(sigId, function(errRedis, reply){
                if(errRedis){
                    cryptoProxy.log.error("errRedis: " + errRedis);
                }else{
                    if(reply != null){
                        cctx.ids = reply;
                    }
                    block.txs.push(cctx);
                    if(block.txs.length == blockFinishLength){
                        cryptoProxy.log.info("*****blocksMsg.blocks.length: " + blocksMsg.blocks.length);
                        blocksMsg.blocks.push(block);
                        if(blocksMsg.blocks.length == blocksFinishLength){
                            makeFinalBlocksResponse(cryptoProxy, redisProxy, request, blocksMsg);
                        }
                    }
                }
            });
        }
    }
};

var compare = function(blockA, blockB){
   if(blockA.index.height > blockB.index.height){
       return 1;
   }else if(blockA.index.height < blockB.index.height){
       return -1;
   }else{
       return 0;
   }
};

var makeFinalBlocksResponse = function(cryptoProxy, redisProxy, request, blocksMsg){
    blocksMsg.blocks.sort(compare);
    var diffPos = 0;
    var reorgIndex = new BlockIndex({id: null, height: null});
    for(var i = 0; i < request.startIndexs.length; i++){
        diffPos = i;
        if(request.startIndexs[i].id == blocksMsg.blocks[i].index.id &&
           request.startIndexs[i].height == blocksMsg.blocks[i].index.height){
        }else{
            break;
        }
    }
    if(diffPos == 0){
        cryptoProxy.log.info("block chain fork!");
    }else{
        reorgIndex.id = blocksMsg.blocks[diffPos].index.id;
        reorgIndex.height =  blocksMsg.blocks[diffPos].index.height;
    }
    var blocks = [];
    for(var j = diffPos; j < blocksMsg.blocks.length; j++){
        blocks.push(blocksMsg.blocks[j]);
    }
    var blocksFinal = new CryptoCurrencyBlocksMessage({reorgIndex: reorgIndex, blocks:blocks});
    makeNormalResponse(BitwayResponseType.GET_MISSED_BLOCKS, cryptoProxy, blocksFinal, redisProxy);
};

var getSigId = function(cctx){
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
    cctx.sigId = sha256.digest('hex');
    return cctx.sigId;
};

var getOutputAddresses = function(tx, cctx){
    for(var k = 0; k < tx.vout.length; k++){
        var output = new CryptoCurrencyTransactionPort();
        if(tx.vout[k].scriptPubKey.addresses != undefined)
        {
            output.address = tx.vout[k].scriptPubKey.addresses.toString();
            output.amount = tx.vout[k].value;
            cctx.outputs.push(output);
        }
    }
};

var getInputAddresses = function(cryptoProxy, input, cctx, finishLength) {
    cryptoProxy.log.info("getInputAddresses input-txid: " + input.txid);
    cryptoProxy.log.info("finishLength: " + finishLength);
    cryptoProxy.log.info("input-vout: " + input.vout);
    var vout = input.vout;
    var rpc = cryptoProxy.rpc;
    rpc.getRawTransaction(input.txid, cryptoProxy.needJson, function(errIn,retIn){
        if(errIn){
            cryptoProxy.log.info("errIn code: " + errIn.code);
            cryptoProxy.log.info("errIn message: " + errIn.message);
        }else{
            for(var j = 0; j < retIn.result.vout.length; j++){
                if(vout == retIn.result.vout[j].n){
                    cryptoProxy.log.info("success match: " + retIn.result.vout[j].n);
                    var input = new CryptoCurrencyTransactionPort();
                    if(retIn.result.vout[j].scriptPubKey.addresses != null){
                        input.address = retIn.result.vout[j].scriptPubKey.addresses.toString();
                    }else{
                        input.address = "";
                    }
                    input.amount = retIn.result.vout[j].value;
                    cryptoProxy.log.info("input.address: " + input.address);
                    cctx.inputs.push(input);
                }
            }
            cryptoProxy.log.info("cctx.outputs.length: " + cctx.outputs.length);
            if(cctx.inputs.length == finishLength){
                var sigId = getSigId(cctx);
                cryptoProxy.innerRedis.get(sigId, function(errRedis, reply){
                    if(errRedis){
                        cryptoProxy.log.error("errRedis: " + errRedis);
                    }else{
                        cryptoProxy.log.info("cctx.ids: " + reply);
                        if(reply != null){
                            cctx.ids = reply;
                        }
                        makeNormalResponse(BitwayResponseType.TRANSACTION, cryptoProxy,
                            cctx, cryptoProxy.redisProxy);
                    }
                });
            }
        }
    });
};

var getTxsSinceBlock = function(cryptoProxy, index) {
    var rpc = cryptoProxy.rpc;
    rpc.getBlockHash(index, function(errHash, retHash){
        if(!errHash && retHash){
            cryptoProxy.log.info('check tx in block: ' + index);
            rpc.listSinceBlock(retHash.result, function(errSinceBlock, retSinceBlock){
                if(!errSinceBlock && retSinceBlock){
                    cryptoProxy.log.info("transactions.length: " + retSinceBlock.result.transactions.length);
                    for(var i = 0; i < retSinceBlock.result.transactions.length; i++){
                        rpc.getRawTransaction(retSinceBlock.result.transactions[i].txid,
                            cryptoProxy.needJson, function(errTx,retTx){
                            if(errTx){
                                cryptoProxy.log.info("errTx code: " + errTx.code);
                                cryptoProxy.log.info("errTx message: " + errTx.message);
                            }else{
                                var cctx = new CryptoCurrencyTransaction({inputs: [], outputs: [],
                                    status: TransferStatus.Confirming});
                                cctx.txid = retTx.result.txid;
                                cryptoProxy.log.info("txid: " + cctx.txid + " comfirmation: " + retTx.result.comfirmations);
                                getOutputAddresses(retTx.result, cctx);
                                for(var i = 0; i < retTx.result.vin.length; i++){
                                    cryptoProxy.log.info("vout: " + retTx.result.vin[i].vout);
                                    getInputAddresses(cryptoProxy, retTx.result.vin[i], cctx, retTx.result.vin.length);
                                }
                            }
                        });
                    }
                }else{
                }
           });
        }else{
            cryptoProxy.log.info("errHash code: " + errHash.code);
            cryptoProxy.log.info("errHash message: " + errHash.message);
        }
    });
};

var getBlockByIndex = function(cryptoProxy, index) {
    cryptoProxy.log.info("block index: " + index);
    var rpc = cryptoProxy.rpc;
    rpc.getBlockHash(index, function(errHash,retHash){
        if(errHash){
            cryptoProxy.log.info("errHash code: " + errHash.code);
            cryptoProxy.log.info("errHash message: " + errHash.message);
        }else{
            rpc.getBlock(retHash.result, function(errBlock, retBlock){
                if(errBlock){
                    cryptoProxy.log.info("errBlock code: " + errBlock.code);
                    cryptoProxy.log.info("errBlock message: " + errBlock.message);
                }else{
                    var index = new BlockIndex({id: retBlock.result.hash, height:retBlock.result.height});
                    cryptoProxy.log.info("height: " + index.height + "hash: " + index.id);
                    var prevIndex = new BlockIndex({id:retBlock.result.previousblockhash,
                        height:retBlock.result.height - 1});
                    var txs = [];
                    var block = new CryptoCurrencyBlock({index:index, prevIndex:prevIndex, txs:txs});
                    for(var i = 0; i < retBlock.result.tx.length; i++){
                        rpc.getRawTransaction(retBlock.result.tx[i], cryptoProxy.needJson, function(errTx, retTx){
                            if(errTx){
                                cryptoProxy.log.info("errTx code: " + errBlock.code);
                                cryptoProxy.log.info("errTx message: " + errBlock.message);
                            }else{
                                //cryptoProxy.log.info("txid: " + retTx.result.txid);
                                var cctx = new CryptoCurrencyTransaction({inputs: [], outputs: [],
                                    status: TransferStatus.Confirming});
                                cctx.txid = retTx.result.txid;
                                getOutputAddresses(retTx.result, cctx);
                                for(var j = 0; j < retTx.result.vin.length; j++){
                                    cryptoProxy.log.info("vout: " + retTx.result.vin[j].vout);
                                    getAllTxsInBlock(cryptoProxy, retTx.result.vin[j], retTx.result.vin.length,
                                        retBlock.result.tx.length, cctx, block);
                                }
                            }
                        });
                    }
                }
            });
        }
    });
};

var getAllTxsInBlock = function(cryptoProxy, input, txFinishLength, blockFinishLength, cctx, block){
    cryptoProxy.log.info("input-txid: " + input.txid);
    cryptoProxy.log.info("txFinishLength: " + txFinishLength);
    cryptoProxy.log.info("blockFinishLength: " + blockFinishLength);
    cryptoProxy.log.info("input-vout: " + input.vout);
    cryptoProxy.log.info("block.index: " + block.index.id);
    var rpc = cryptoProxy.rpc;
    var vout = input.vout;
    if(input.txid != undefined){
        rpc.getRawTransaction(input.txid, cryptoProxy.needJson, function(errIn,retIn){
            if(errIn){
                cryptoProxy.log.info("errIn code: " + errIn.code);
                cryptoProxy.log.info("errIn message: " + errIn.message);
            }else{
                for(var j = 0; j < retIn.result.vout.length; j++){
                    if(vout == retIn.result.vout[j].n){
                        var input = new CryptoCurrencyTransactionPort();
                        //TODO:scriptPubKey.addresses maybe null?
                        if(input.address = retIn.result.vout[j].scriptPubKey.addresses != null){
                            input.address = retIn.result.vout[j].scriptPubKey.addresses.toString();
                        }else{
                            cryptoProxy.log.info("addresses == null");
                            input.address = "";
                        }
                        input.amount = retIn.result.vout[j].value;
                        //cryptoProxy.log.info("succsess match input.address: " + input.address);
                        cctx.inputs.push(input);
                    }
                }
                //cryptoProxy.log.info("cctx.inputs.length: " + cctx.inputs.length);
                if(cctx.inputs.length == txFinishLength){
                    //cryptoProxy.log.info("block.txs.length: " + block.txs.length);
                    var sigId = getSigId(cctx);
                    cryptoProxy.innerRedis.get(sigId, function(errRedis, reply){
                        if(errRedis){
                            cryptoProxy.log.error("errRedis: " + errRedis);
                        }else{
                            cryptoProxy.log.info("cctx.ids: " + reply);
                            if(reply != null){
                                cctx.ids = reply;
                            }
                            block.txs.push(cctx);
                            if(block.txs.length == blockFinishLength){
                                var blocksMsg = new CryptoCurrencyBlocksMessage({blocks:[]});
                                blocksMsg.blocks.push(block);
                                cryptoProxy.log.info("cryptoProxy.lastReportBlockIndex: ", cryptoProxy.lastReportBlockIndex);
                                cryptoProxy.innerRedis.set(cryptoProxy.lastReportBlockIndex,
                                    block.index.height, function(err, reply) {  
                                    if (err) {  
                                        cryptoProxy.log.info(err);  
                                        return;  
                                    } 
                                });
                                makeNormalResponse(BitwayResponseType.AUTO_REPORT_BLOCKS, cryptoProxy, 
                                    blocksMsg, cryptoProxy.redisProxy);
                            }
                        }
                    });
                }
            }
        });
    }else{
        var input = new CryptoCurrencyTransactionPort();
        input.address = "coinbase";
        input.amount = 0;
        cryptoProxy.log.info("input.address: " + input.address);
        cctx.inputs.push(input);
        if(cctx.inputs.length == txFinishLength){
            var sigId = getSigId(cctx);
            cryptoProxy.innerRedis.get(sigId, function(errRedis, reply){
                if(errRedis){
                    cryptoProxy.log.error("errRedis: " + errRedis);
                }else{
                    cryptoProxy.log.info("cctx.ids: " + reply);
                    if(reply != null){
                        cctx.ids = reply;
                    }
                    block.txs.push(cctx);
                    if(block.txs.length == blockFinishLength){
                        var blocksMsg = new CryptoCurrencyBlocksMessage({blocks:[]});
                        blocksMsg.blocks.push(block);
                        cryptoProxy.innerRedis.set(cryptoProxy.lastReportBlockIndex, 
                            block.index.height, function(err, reply){
                            if (err) {  
                                cryptoProxy.log.info(err);  
                                return;  
                            } 
                        });
                        makeNormalResponse(BitwayResponseType.AUTO_REPORT_BLOCKS, cryptoProxy, 
                            blocksMsg, cryptoProxy.redisProxy);
                    }
                }
            });
        }
    }
}

var displayTxContent = function(cryptoProxy, cctx){
    cryptoProxy.log.info("Tx txid: " + cctx.txid);
    cryptoProxy.log.info("Tx sigId: " + cctx.sigId);
    cryptoProxy.log.info("Tx ids: " + cctx.ids);
    for(var m = 0; m < cctx.inputs.length; m++){
        cryptoProxy.log.info("input address "+ m + ": " + cctx.inputs[m].address);
        cryptoProxy.log.info("input amount "+ m + ": " + cctx.inputs[m].amount);
    }
    for(var n = 0; n < cctx.outputs.length; n++){
        cryptoProxy.log.info("output address "+ n + ": " + cctx.outputs[n].address);
        cryptoProxy.log.info("output amount "+ n + ": " + cctx.outputs[n].amount);
    }
};

var displayBlocksContent = function(cryptoProxy, blockArray){
    for(var i = 0; i < blockArray.length; i++){
        cryptoProxy.log.info("index id: " + blockArray[i].index.id);
        cryptoProxy.log.info("index height: " + blockArray[i].index.height);
        cryptoProxy.log.info("prevIndex id: " + blockArray[i].prevIndex.id);
        cryptoProxy.log.info("prevIndex height: " + blockArray[i].prevIndex.height);
        for(var j =0; j < blockArray[i].txs.length; j++){
            displayTxContent(cryptoProxy, blockArray[i].txs[j]);
        }
    }
};

var makeNormalResponse = function(type, cryptoProxy, response, redisProxy){
    cryptoProxy.log.info("type: " + type);
    var currency = cryptoProxy.currency;
    cryptoProxy.log.info("currency: " + currency);
    switch(type){
        case BitwayResponseType.GENERATE_ADDRESS:
            cryptoProxy.log.info("GENERATE_ADDRESS");
            redisProxy.publish(new BitwayMessage({currency: currency, generateAddressResponse: response}));
            break;
        case BitwayResponseType.TRANSFER:
        case BitwayResponseType.TRANSACTION:
            cryptoProxy.log.info("TRANSACTION REPORT: " + currency);
            displayTxContent(cryptoProxy, response);
            redisProxy.publish(new BitwayMessage({currency: currency, tx: response}));
            break;
        case BitwayResponseType.GET_MISSED_BLOCKS:
        case BitwayResponseType.AUTO_REPORT_BLOCKS:
            cryptoProxy.log.info("BLOCK REPORT: " + currency);
            cryptoProxy.log.info("response.blocks.length:" + response.blocks.length);
            displayBlocksContent(cryptoProxy, response.blocks);
            redisProxy.publish(new BitwayMessage({currency: currency, blocksMsg: response}));
            break;
        default:
            cryptoProxy.log.info("Inavalid Type!");
    }
};

var logo = "\n" +
" _    _ _                     \n" +
"| |__(_) |___ __ ____ _ _  _  \n" +
"| '_ \\ |  _\\ V  V / _` | || | \n" +
"|_.__/_|\\__|\\_/\\_/\\__,_|\\_, | \n" +
"                        |__/  \n";
console.log(logo);

