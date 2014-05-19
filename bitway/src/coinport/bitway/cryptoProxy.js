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
    Networks                        = Bitcore.networks;


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
};

CryptoProxy.prototype.checkTx = function(cryptoProxy){
    console.log('** CHECK TX **' + cryptoProxy.currency + " begin time: " + (new Date().getTime()));
    var rpc = cryptoProxy.rpc;
    rpc.getBlockCount(function(errCount,retCount){
        if(errCount){
            console.log("errCount code: " + errCount.code);
            console.log("errCount message: " + errCount.message);
        }else{
            console.log("current block index: " + retCount.result);
            getTxsSinceBlock(cryptoProxy, retCount.result);
        }
    });
};

CryptoProxy.prototype.checkBlock = function(cryptoProxy){
    console.log('** CHECK_BLOCK **' + "begin Time: " + (new Date().toLocaleString()));
    var rpc = cryptoProxy.rpc;
    cryptoProxy.innerRedis.get(cryptoProxy.lastReportBlockIndex, function(errLastIndex, retLastIndex){
        rpc.getBlockCount(function(errCount,retCount){
            if(errCount){
                console.log("errCount code: " + errCount.code);
                console.log("errCount message: " + errCount.message);
            }else{
                console.log("current block index: " + retCount.result);
                console.log("last report block index: " + retLastIndex);
                if(!isNaN(retLastIndex) && retLastIndex < retCount.result){
                    console.log("Behind the newest: " + (retCount.result - retLastIndex));
                    var checkBlockIndex = Number(retLastIndex) + Number(1);
                    getBlockByIndex(cryptoProxy, checkBlockIndex);
                }else if(!isNaN(retLastIndex) && retLastIndex == retCount.result){
                    console.log("The newest block has already been reported!");
                }else{
                    getBlockByIndex(cryptoProxy, retCount.result);
                }
            }
        });
    });
};

CryptoProxy.prototype.generateUserAddress = function(cryptoProxy, request, redisProxy) {
    var startTime = new Date().getTime();
    var rpc = cryptoProxy.rpc;
    console.log(RedisProxy.EventType.GENERATE_ADDRESS);
    console.log(cryptoProxy.currency);
    console.log(request);
    if (request.num < cryptoProxy.MIN_GENERATE_ADDR_NUM || request.num > cryptoProxy.MAX_GENERATE_ADDR_NUM) {
        var generateAddressResponse = new GenerateAddressesResult({error: ErrorCode.INVALID_REQUEST_ADDRESS_NUM});
        makeNormalResponse(BitwayResponseType.GENERATE_ADDRESS, cryptoProxy.currency, generateAddressResponse);
    } else {
        var addresses = [];
        for (var i = 0; i < request.num; i++) {
            rpc.getNewAddress(cryptoProxy.ACCOUNT, function(errAddress, retAddress) {
                if (errAddress) {
                    console.error('An error occured generate address');
                    console.error(errAddress);
                    var generateAddressResponse = new GenerateAddressesResult({error: ErrorCode.RPC_ERROR});
                    makeNormalResponse(BitwayResponseType.GENERATE_ADDRESS, cryptoProxy.currency, 
                        generateAddressResponse, redisProxy);
                    return;
                }
                var address = retAddress.result;
                addresses.push(address);
                if (addresses.length == request.num) {
                    console.log("addresses: " + addresses);
                    var generateAddressResponse = new GenerateAddressesResult({error: ErrorCode.OK,
                            addresses: addresses, addressType: CryptoCurrencyAddressType.UNUSED});
                    makeNormalResponse(BitwayResponseType.GENERATE_ADDRESS, cryptoProxy.currency, 
                        generateAddressResponse, redisProxy);
                    console.log("costTime: " + (new Date().getTime() - startTime) + "ms");
                }
            });
        }
    }
};

CryptoProxy.prototype.getMissedBlocks = function(cryptoProxy, request, redisProxy) {
    console.log(RedisProxy.EventType.GET_MISSED_BLOCKS);
    console.log("endIndex hash:" + request.endIndex.id);
    var rpc = cryptoProxy.rpc;
    var blocksFinishLength = request.endIndex.height - request.startIndexs[0].height +1;
    var blocksMsg = new CryptoCurrencyBlocksMessage({blocks:[]});
    for(var iHeight = request.startIndexs[0].height; iHeight < request.endIndex.height + 1; iHeight++){
        console.log("iHeight: " + iHeight);
        rpc.getBlockHash(iHeight, function(errHash, retHash){
            console.log("block hash: " + retHash.result);
            rpc.getBlock(retHash.result, function(errBlock, retBlock){
                if(errBlock){
                    console.log("errBlock code: " + errBlock.code);
                    console.log("errBlock message: " + errBlock.message);
                }else{
                    var index = new BlockIndex({id: retBlock.result.hash, height:retBlock.result.height});
                    console.log(index.id);
                    var prevIndex = new BlockIndex({id:retBlock.result.previousblockhash, height:retBlock.result.height - 1});
                    var txs = [];
                    var block = new CryptoCurrencyBlock({index:index, prevIndex:prevIndex, txs:txs});
                    for(var i = 0; i < retBlock.result.tx.length; i++){
                        rpc.getRawTransaction(retBlock.result.tx[i], cryptoProxy.needJson, function(errTx, retTx){
                            if(errTx){
                                console.log("errTx code: " + errBlock.code);
                                console.log("errTx message: " + errBlock.message);
                            }else{
                                //console.log("txid: " + retTx.result.txid);
                                var cctx = new CryptoCurrencyTransaction({inputs: [], outputs: [],
                                    status: TransferStatus.Confirming});
                                cctx.txid = retTx.result.txid;
                                getOutputAddresses(retTx.result, cctx);
                                for(var j = 0; j < retTx.result.vin.length; j++){
                                    //console.log("vout: " + retTx.result.vin[j].vout);
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
    console.log('** TransferRequest Received **');
    console.log(RedisProxy.EventType.TRANSFER);
    console.log(cryptoProxy.currency);
    switch(request.type){
        case TransferType.WITHDRAWAL:
        case TransferType.HOT_TO_COLD:
            txWithDefiniteTo(cryptoProxy, request, redisProxy);
            break;
        case TransferType.USER_TO_HOT:
            txWithDefiniteFrom(cryptoProxy, request, redisProxy);
            break;
        default:
            console.log("Invalid request type: " + request.type);
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
                rpc.getNewAddress(HOT_ACCOUNT, function(errAddress, retAddress) {
                    if (errAddress) {
                        console.error('An error occured generate address');
                        console.error(errAddress);
                    }else{
                        for(var i = 0; i < request.transferInfos.length; i++){
                            request.transferInfos[i].to = retAddress.result;
                            makeTransaction(cryptoProxy, request.transferInfos[i], request.transferInfos.length,
                                fromAddresses, transactions, addresses, ids, redisProxy);
                        }
                    }
                });
            }else{
                var toPos = Math.floor(Math.random()*retAddr.result.length);
                var toAddress = retAddr.result[toPos];
                console.log("toPos: " + toPos + " toAddr: " + toAddress);
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
    console.log("from: " + fromArray[0]);
    rpc.listUnspent(minConfirmedNum, maxConfirmedNum, fromArray, function(errUnspent, retUnspent){
        if(errUnspent){
            console.log("errUnspent code: " + errUnspent.code);
            console.log("errUnspent message: " + errUnspent.message);
        }else{
            var amountUnspent = 0;
            console.log("retUnspent.length: " + retUnspent.result.length);
            for(var j = 0; j < retUnspent.result.length; j++){
                console.log("txid: " + retUnspent.result[j].txid);
                console.log("vout: " + retUnspent.result[j].vout);
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
                console.log("There is nothing to spend!");
            }else{
                var amountCanUse = 0;
                var i = 0;
                var transactions = [];
                rpc.getAddressesByAccount(cryptoProxy.HOT_ACCOUNT, function(err, ret){
                    if(err){
                    }else{
                        var changePos = Math.floor(Math.random()*ret.result.length);
                        console.log("changePos: " + changePos);
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

var getTransactionInfo = function(cryptoPrxoy, txid, ids, redisProxy){
    var rpc = cryptoPrxoy.rpc;
    console.log("txid: " + txid);
    rpc.getRawTransaction(txid, cryptoPrxoy.needJson, function(err,ret){
        if(err){
            console.log("fail code: " + err.code);
            console.log("fail message: " + err.message);
        }else{
            var cctx = new CryptoCurrencyTransaction({inputs: [], outputs: [],
                status: TransferStatus.Confirming});
            cctx.txid = ret.result.txid;
            console.log("txid: " + ret.result.txid);
            getOutputAddresses(ret.result, cctx);
            for(var i = 0; i < ret.result.vin.length; i++){
                console.log("vout: " + ret.result.vin[i].vout);
                saveTransferIds(cryptoPrxoy, ret.result.vin[i], cctx, ret.result.vin.length, ids, redisProxy);
            }
        }
    });
}

var saveTransferIds = function(cryptoProxy, input, cctx, finishLength, ids, redisProxy){
    console.log("saveTransferIds input-txid: " + input.txid);
    console.log("finishLength: " + finishLength);
    console.log("input-vout: " + input.vout);
    var rpc = cryptoProxy.rpc;
    var vout = input.vout;
    rpc.getRawTransaction(input.txid, cryptoProxy.needJson, function(errIn,retIn){
        if(errIn){
            console.log("errIn code: " + errIn.code);
            console.log("errIn message: " + errIn.message);
        }else{
            for(var j = 0; j < retIn.result.vout.length; j++){
                if(vout == retIn.result.vout[j].n){
                    console.log("success match: " + retIn.result.vout[j].n);
                    var input = new CryptoCurrencyTransactionPort();
                    input.address = retIn.result.vout[j].scriptPubKey.addresses.toString();
                    input.amount = retIn.result.vout[j].value;
                    console.log("input.address: " + input.address);
                    cctx.inputs.push(input);
                }
            }
            console.log("cctx.outputs.length: " + cctx.outputs.length);
            if(cctx.inputs.length == finishLength){
                var sigId = getSigId(cctx); 
                cryptoProxy.innerRedis.set(sigId, ids, function(errRedis, reply){
                    if(errRedis){
                        console.log("errRedis: " + errRedis);
                    }else{
                        console.log("cctx.sigId: " + cctx.sigId);
                        makeNormalResponse(BitwayResponseType.TRANSACTION, cryptoProxy.currency, cctx, redisProxy);
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
        console.log("tx.txid: " + transactions[i].txid);
        console.log("tx.vout: " + transactions[i].vout);
    }
    console.log(addresses);
    rpc.createRawTransaction(transactions, addresses, function(errCreate, retCreate){
        if(errCreate){
            console.log("errCreate code: " + errCreate.code);
            console.log("errCreate message: " + errCreate.message);
        }else{
            console.log("transaction: " + retCreate.result);
            rpc.signRawTransaction(retCreate.result, function(errSign, retSign){
                if(errSign){
                    console.log("errSign code: " + errSign.code);
                    console.log("errSign message: " + errSign.message);
                }else{
                    rpc.sendRawTransaction(retSign.result.hex, function(errSend, retSend){
                        console.log("send hex: " + retSign.result.hex);
                        if(errSend){
                            console.log("errSend code: " + errSend.code);
                            console.log("errSend message: " + errSend.message);
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
    console.log("input-txid: " + input.txid);
    console.log("txFinishLength: " + txFinishLength);
    console.log("blockFinishLength: " + blockFinishLength);
    console.log("blocksFinishLength: " + blocksFinishLength);
    console.log("input-vout: " + input.vout);
    console.log("block.index: " + block.index.id);
    var rpc = cryptoProxy.rpc;
    var vout = input.vout;
    if(input.txid != undefined){
        rpc.getRawTransaction(input.txid, cryptoProxy.needJson, function(errIn,retIn){
            if(errIn){
                console.log("errIn code: " + err.code);
                console.log("errIn message: " + err.message);
            }else{
                for(var j = 0; j < retIn.result.vout.length; j++){
                    if(vout == retIn.result.vout[j].n){
                        //console.log("success match: " + retIn.result.vout[j].n);
                        var input = new CryptoCurrencyTransactionPort();
                        input.address = retIn.result.vout[j].scriptPubKey.addresses.toString();
                        input.amount = retIn.result.vout[j].value;
                        //console.log("success match input.address: " + input.address);
                        cctx.inputs.push(input);
                        break;
                    }
                }
                console.log("cctx.inputs.length: " + cctx.inputs.length);
                if(cctx.inputs.length == txFinishLength){
                    console.log("block.txs.length: " + block.txs.length);
                    console.log("blockFinishLength: " + blockFinishLength);
                    var sigId = getSigId(cctx);
                    cryptoProxy.innerRedis.get(sigId, function(errRedis, reply){
                        if(errRedis){
                            console("errRedis: " + errRedis);
                        }else{
                            cctx.ids = reply;
                            block.txs.push(cctx);
                            if(block.txs.length == blockFinishLength){
                                console.log("*****blocksMsg.blocks.length: " + blocksMsg.blocks.length);
                                blocksMsg.blocks.push(block);
                                if(blocksMsg.blocks.length == blocksFinishLength){
                                    makeFinalBlocksResponse(redisProxy, request, blocksMsg);
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
        console.log("input.address: " + input.address);
        cctx.inputs.push(input);
        if(cctx.inputs.length == txFinishLength){
            console.log("block.txs.length: " + block.txs.length);
            console.log("blockFinishLength: " + blockFinishLength);
            var sigId = getSigId(cctx);
            cryptoProxy.innerRedis.get(sigId, function(errRedis, reply){
                if(errRedis){
                    console("errRedis: " + errRedis);
                }else{
                    cctx.ids = relpy;
                    block.txs.push(cctx);
                    if(block.txs.length == blockFinishLength){
                        console.log("*****blocksMsg.blocks.length: " + blocksMsg.blocks.length);
                        blocksMsg.blocks.push(block);
                        if(blocksMsg.blocks.length == blocksFinishLength){
                            makeFinalBlocksResponse(redisProxy, request, blocksMsg);
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

var makeFinalBlocksResponse = function(redisProxy, request, blocksMsg){
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
        console.log("block chain fork!");
    }else{
        reorgIndex.id = blocksMsg.blocks[diffPos].index.id;
        reorgIndex.height =  blocksMsg.blocks[diffPos].index.height;
    }
    var blocks = [];
    for(var j = diffPos; j < blocksMsg.blocks.length; j++){
        blocks.push(blocksMsg.blocks[j]);
    }
    var blocksFinal = new CryptoCurrencyBlocksMessage({reorgIndex: reorgIndex, blocks:blocks});
    makeNormalResponse(BitwayResponseType.GET_MISSED_BLOCKS, cryptoProxy.currency, blocksFinal, redisProxy);
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
    console.log('sigId: ' + sigId);
    sha256.update(sigId);
    cctx.sigId = sha256.digest('hex');
    console.log('sha256 sigId: ' + cctx.sigId);
    return sigId;
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
    console.log("getInputAddresses input-txid: " + input.txid);
    console.log("finishLength: " + finishLength);
    console.log("input-vout: " + input.vout);
    var vout = input.vout;A
    var rpc = cryptoProxy.rpc;
    rpc.getRawTransaction(input.txid, needJson, function(errIn,retIn){
        if(errIn){
            console.log("errIn code: " + errIn.code);
            console.log("errIn message: " + errIn.message);
        }else{
            for(var j = 0; j < retIn.result.vout.length; j++){
                if(vout == retIn.result.vout[j].n){
                    console.log("success match: " + retIn.result.vout[j].n);
                    var input = new CryptoCurrencyTransactionPort();
                    input.address = retIn.result.vout[j].scriptPubKey.addresses.toString();
                    input.amount = retIn.result.vout[j].value;
                    console.log("input.address: " + input.address);
                    cctx.inputs.push(input);
                }
            }
            console.log("cctx.outputs.length: " + cctx.outputs.length);
            if(cctx.inputs.length == finishLength){
                var sigId = getSigId(cctx);
                proxy.innerClient.get(sigId, function(errRedis, reply){
                    if(errRedis){
                        console("errRedis: " + errRedis);
                    }else{
                        console.log("cctx.ids: " + reply);
                        cctx.ids = reply;
                        makeNormalResponse(BitwayResponseType.TRANSACTION, cryptoProxy.currency,
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
            console.log('** TX Received **');
            rpc.listSinceBlock(retHash.result, function(errSinceBlock, retSinceBlock){
                if(!errSinceBlock && retSinceBlock){
                    console.log("transactions.length: " + retSinceBlock.result.transactions.length);
                    for(var i = 0; i < retSinceBlock.result.transactions.length; i++){
                        var txid = retSinceBlock.result.transactions[i];
                        console.log("txid: " + txid);
                        rpc.getRawTransaction(txid, cryptoProxy.needJson, function(errTx,retTx){
                            if(errTx){
                                console.log("errTx code: " + errTx.code);
                                console.log("errTx message: " + errTx.message);
                            }else{
                                var cctx = new CryptoCurrencyTransaction({inputs: [], outputs: [],
                                    status: TransferStatus.Confirming});
                                cctx.txid = txid;
                                getOutputAddresses(retTx.result, cctx);
                                for(var i = 0; i < retTx.result.vin.length; i++){
                                    console.log("vout: " + retTx.result.vin[i].vout);
                                    getInputAddresses(cryptoProxy, retTx.result.vin[i], cctx, retTx.result.vin.length);
                                }
                            }
                        });
                    }
                }else{
                }
           });
        }else{
            console.log("errHash code: " + errHash.code);
            console.log("errHash message: " + errHash.message);
        }
    });
};

var getBlockByIndex = function(cryptoProxy, index) {
    console.log("block index: " + index);
    var rpc = cryptoProxy.rpc;
    rpc.getBlockHash(index, function(errHash,retHash){
        if(errHash){
            console.log("errHash code: " + errHash.code);
            console.log("errHash message: " + errHash.message);
        }else{
            rpc.getBlock(retHash.result, function(errBlock, retBlock){
                if(errBlock){
                    console.log("errBlock code: " + errBlock.code);
                    console.log("errBlock message: " + errBlock.message);
                }else{
                    console.log("retBlock.result.hash: " + retBlock.result.hash);
                    var index = new BlockIndex({id: retBlock.result.hash, height:retBlock.result.height});
                    console.log(index.id);
                    var prevIndex = new BlockIndex({id:retBlock.result.previousblockhash,
                        height:retBlock.result.height - 1});
                    var txs = [];
                    var block = new CryptoCurrencyBlock({index:index, prevIndex:prevIndex, txs:txs});
                    for(var i = 0; i < retBlock.result.tx.length; i++){
                        rpc.getRawTransaction(retBlock.result.tx[i], cryptoProxy.needJson, function(errTx, retTx){
                            if(errTx){
                                console.log("errTx code: " + errBlock.code);
                                console.log("errTx message: " + errBlock.message);
                            }else{
                                //console.log("txid: " + retTx.result.txid);
                                var cctx = new CryptoCurrencyTransaction({inputs: [], outputs: [],
                                    status: TransferStatus.Confirming});
                                cctx.txid = retTx.result.txid;
                                getOutputAddresses(retTx.result, cctx);
                                for(var j = 0; j < retTx.result.vin.length; j++){
                                    console.log("vout: " + retTx.result.vin[j].vout);
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
    console.log("input-txid: " + input.txid);
    console.log("txFinishLength: " + txFinishLength);
    console.log("blockFinishLength: " + blockFinishLength);
    console.log("input-vout: " + input.vout);
    console.log("block.index: " + block.index.id);
    var rpc = cryptoProxy.rpc;
    var vout = input.vout;
    if(input.txid != undefined){
        rpc.getRawTransaction(input.txid, cryptoProxy.needJson, function(errIn,retIn){
            if(errIn){
                console.log("errIn code: " + errIn.code);
                console.log("errIn message: " + errIn.message);
            }else{
                for(var j = 0; j < retIn.result.vout.length; j++){
                    if(vout == retIn.result.vout[j].n){
                        var input = new CryptoCurrencyTransactionPort();
                        //TODO:scriptPubKey.addresses maybe null?
                        if(input.address = retIn.result.vout[j].scriptPubKey.type == "pubkeyhash"){
                            input.address = retIn.result.vout[j].scriptPubKey.addresses.toString();
                        }
                        input.amount = retIn.result.vout[j].value;
                        //console.log("succsess match input.address: " + input.address);
                        cctx.inputs.push(input);
                    }
                }
                //console.log("cctx.inputs.length: " + cctx.inputs.length);
                if(cctx.inputs.length == txFinishLength){
                    //console.log("block.txs.length: " + block.txs.length);
                    var sigId = getSigId(cctx);
                    cryptoProxy.innerRedis.get(sigId, function(errRedis, reply){
                        if(errRedis){
                            console("errRedis: " + errRedis);
                        }else{
                            console.log("cctx.ids: " + reply);
                            cctx.ids = reply;
                            block.txs.push(cctx);
                            if(block.txs.length == blockFinishLength){
                                var blocksMsg = new CryptoCurrencyBlocksMessage({blocks:[]});
                                blocksMsg.blocks.push(block);
                                console.log("cryptoProxy.lastReportBlockIndex: ", cryptoProxy.lastReportBlockIndex);
                                cryptoProxy.innerRedis.set(cryptoProxy.lastReportBlockIndex,
                                    block.index.height, function(err, reply) {  
                                    if (err) {  
                                        console.log(err);  
                                        return;  
                                    } 
                                });
                                console.log("##########################################################");
                                makeNormalResponse(BitwayResponseType.AUTO_REPORT_BLOCKS, cryptoProxy.currency, 
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
        console.log("input.address: " + input.address);
        cctx.inputs.push(input);
        if(cctx.inputs.length == txFinishLength){
            var sigId = getSigId(cctx);
            cryptoProxy.innerRedis.get(sigId, function(errRedis, reply){
                if(errRedis){
                    console("errRedis: " + errRedis);
                }else{
                    console.log("cctx.ids: " + reply);
                    cctx.ids = reply;
                    block.txs.push(cctx);
                    if(block.txs.length == blockFinishLength){
                        var blocksMsg = new CryptoCurrencyBlocksMessage({blocks:[]});
                        blocksMsg.blocks.push(block);
                        cryptoProxy.innerRedis.set(cryptoProxy.lastReportBlockIndex, 
                            block.index.height, function(err, reply){
                            if (err) {  
                                console.log(err);  
                                return;  
                            } 
                        });
                        makeNormalResponse(BitwayResponseType.AUTO_REPORT_BLOCKS, cryptoProxy.currency, 
                            blocksMsg, cryptoProxy.redisProxy);
                    }
                }
            });
        }
    }
}

var displayTxContent = function(cctx){
    console.log("Tx txid: " + cctx.txid);
    console.log("Tx sigId: " + cctx.sigId);
    console.log("Tx ids: " + cctx.ids);
    for(var m = 0; m < cctx.inputs.length; m++){
        console.log("input address "+ m + ": " + cctx.inputs[m].address);
        console.log("input amount "+ m + ": " + cctx.inputs[m].amount);
    }
    for(var n = 0; n < cctx.outputs.length; n++){
        console.log("output address "+ n + ": " + cctx.outputs[n].address);
        console.log("output amount "+ n + ": " + cctx.outputs[n].amount);
    }
};

var displayBlocksContent = function(blockArray){
    for(var i = 0; i < blockArray.length; i++){
        console.log("index id: " + blockArray[i].index.id);
        console.log("index height: " + blockArray[i].index.height);
        console.log("prevIndex id: " + blockArray[i].prevIndex.id);
        console.log("prevIndex height: " + blockArray[i].prevIndex.height);
//        for(var j =0; j < blockArray[i].txs.length; j++){
//            displayTxContent(blockArray[i].txs[j]);
//        }
    }
};

var makeNormalResponse = function(type, currency, response, redisProxy){
    console.log("type: " + type);
    console.log("currency: " + currency);
    switch(type){
        case BitwayResponseType.GENERATE_ADDRESS:
            console.log("GENERATE_ADDRESS");
            redisProxy.publish(new BitwayMessage({currency: currency, generateAddressResponse: response}));
            break;
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
        default:
            console.log("Inavalid Type!");
    }
};

var logo = "" +
" _    _ _                     \n" +
"| |__(_) |___ __ ____ _ _  _  \n" +
"| '_ \\ |  _\\ V  V / _` | || | \n" +
"|_.__/_|\\__|\\_/\\_/\\__,_|\\_, | \n" +
"                        |__/  \n";
console.log(logo);

