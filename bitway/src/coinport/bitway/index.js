/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

var RedisProxy                      = require('./redis/redis_proxy').RedisProxy,
    DataTypes                       = require('../../../gen-nodejs/data_types')
    MessageTypes                    = require('../../../gen-nodejs/message_types')
    Bitcore                         = require('bitcore'),
    BitwayRequestType               = DataTypes.BitwayRequestType,
    GenerateAddressesResult         = MessageTypes.GenerateAddressesResult,
    CryptoCurrencyTransaction       = DataTypes.CryptoCurrencyTransaction,
    CryptoCurrencyTransactionPort   = DataTypes.CryptoCurrencyTransactionPort,
    CryptoCurrencyBlock             = DataTypes.CryptoCurrencyBlock,
    TransferStatus                  = DataTypes.TransferStatus,
    CryptoCurrencyTransactionType   = DataTypes.CryptoCurrencyTransactionType,
    CryptoCurrencyAddressType       = DataTypes.CryptoCurrencyAddressType,
    Currency                        = DataTypes.Currency,
    BlockIndex                      = DataTypes.BlockIndex,
    CryptoCurrencyBlocksMessage     = MessageTypes.CryptoCurrencyBlocksMessage,
    ErrorCode                       = DataTypes.ErrorCode,
    BitwayMessage                   = MessageTypes.BitwayMessage,
    Peer                            = Bitcore.Peer,
    Networks                        = Bitcore.networks;

var PeerManager = Bitcore.PeerManager;

var proxy = new RedisProxy("btc", "127.0.0.1", "6379");
var RpcClient = Bitcore.RpcClient;
// TODO(yangli): make CryptoProxy and config following params
var config = {
    protocol: 'http',
    user: 'user',
    pass: 'pass',
    host: '127.0.0.1',
    port: '18332',
};
var rpc = new RpcClient(config);
var MIN_GENERATE_ADDR_NUM = 1;
var MAX_GENERATE_ADDR_NUM = 1000;
var ACCOUNT = "customers";
var HOT_ACCOUNT = "coinport";
var needJson = 1;
var AUTO_REPORT = 0;
var tip = 0.000001;
proxy.start();

proxy.on(RedisProxy.EventType.GENERATE_ADDRESS, function(currency, request) {
    var startTime = new Date().getTime();
    console.log(RedisProxy.EventType.GENERATE_ADDRESS);
    console.log(currency);
    console.log(request);
    if (request.num < MIN_GENERATE_ADDR_NUM || request.num > MAX_GENERATE_ADDR_NUM) {
        proxy.publish(new BitwayMessage({currency: Currency.BTC,
            generateAddressResponse: new GenerateAddressesResult({error: ErrorCode.INVALID_REQUEST_ADDRESS_NUM})}));
    } else {
        var addresses = [];
        for (var i = 0; i < request.num; i++) {
            rpc.getNewAddress(ACCOUNT, function(err, retAddress) {
                if (err) {
                    console.error('An error occured generate address');
                    console.error(err);
                    proxy.publish(new BitwayMessage({currency: Currency.BTC,
                        generateAddressResponse: new GenerateAddressesResult({error: ErrorCode.RPC_ERROR})}));
                    return;
                }
                var address = "";
                // TODO(yangli): don't log private key
                console.log(retAddress.result);
                address = retAddress.result;
                var addr = new Bitcore.Address(address);
                if (addr.isValid()) {
                    // TODO(yangli): not sure this is needed
                    rpc.dumpPrivKey(addr.data, function(err, retPrivKey) {
                        if (err) {
                            console.error('An error occured dumpPrivKey', hash);
                            console.error(err);
                            return;
                        }
                        addresses.push(address);
                        if (addresses.length == request.num) {
                            console.log("addresses: " + addresses);
                            proxy.publish(new BitwayMessage({currency: Currency.BTC,
                                generateAddressResponse: new GenerateAddressesResult({error: ErrorCode.OK,
                                    addresses: addresses, addressType: CryptoCurrencyAddressType.UNUSED})}));
                            console.log("costTime: " + (new Date().getTime() - startTime) + "ms");
                        }
                    });
                }
            });
        }
    }
});

proxy.on(RedisProxy.EventType.TRANSFER, function(currency, request) {
    console.log('** TransferRequest Received **');
    console.log(RedisProxy.EventType.TRANSFER);
    console.log(currency);
    /*var transferInfos = [];
    var transfer1 = new CryptoCurrencyTransferInfo({id: 0, to: 'mrhMpyaM4TZn6QFdTCZhbZzM89yNnPPnGP',
        amount: 0.01, from: 'mzhVJHArQ2ropmyGS3HMwyV6LQLs4pkjXM'});
    var transfer2 = new CryptoCurrencyTransferInfo({id: 0, to: 'mqWs5kNcb6W2oiogTWXYXHBip8E4S6NzA5',
        amount: 0.01, from: 'n3vJJnkJBRQfwfoN7miz1FfYknk382DCaw'});
    transferInfos.push(transfer1);
    transferInfos.push(transfer2);
    var fromAddresses = [];
    var toAddress = 'mqWs5kNcb6W2oiogTWXYXHBip8E4S6NzA5';
    var amount = 0.01;
    var minConfirmedNum = 6;
    var maxConfirmedNum = 9999999;
    var addresses = {};
    //var request = new TransferCryptoCurrency({currency: Currency.BTC,
    //    transferInfos: transferInfos, type:CryptoCurrencyTransactionType.WITHDRAWAL});
    var request = new TransferCryptoCurrency({currency: Currency.BTC,
        transferInfos: transferInfos, type:CryptoCurrencyTransactionType.USER_TO_HOT});*/
    switch(request.type){
        case CryptoCurrencyTransactionType.WITHDRAWAL:
            txWithoutDefiniteFrom(request);
            break;
        case CryptoCurrencyTransactionType.USER_TO_HOT:
            txWithDefiniteFrom(request);
            break;
        default:
            console.log("Invalid request type: " + request.type);
    }
});

var txWithDefiniteFrom = function(request){
    var minConfirmedNum = 6;
    var maxConfirmedNum = 9999999;
    var amountTotal = 0;
    var addresses = {};
    var transactions = [];
    var fromAddresses = [];
    for(var i = 0; i < request.transferInfos.length; i++){
        amountTotal += request.transferInfos[i].amount;
        makeTransaction(request.transferInfos[i], request.transferInfos.length, fromAddresses,transactions, addresses);
    }
}

var makeTransaction = function(transferInfo, finishLength, fromAddresses, transactions, addresses){
    var from = transferInfo.from;
    var to = transferInfo.to;
    var amountPay = transferInfo.amount;
    var minConfirmedNum = 6;
    var maxConfirmedNum = 9999999;
    var fromArray = [];
    fromArray.push(from);
    console.log("from: " + from);
    rpc.listUnspent(minConfirmedNum, maxConfirmedNum, fromArray, function(errUnspent, retUnspent){
        if(errUnspent){
            console.log("errUnspent code: " + errUnspent.code);
            console.log("errUnspent message: " + errUnspent.message);
        }else{
            var amountCanUse = 0;
            console.log("retUnspent.length: " + retUnspent.result.length);
            for(var j = 0; j < retUnspent.result.length; j++){
                console.log("txid: " + retUnspent.result[j].txid);
                console.log("vout: " + retUnspent.result[j].vout);
                var transaction = {
                    txid: retUnspent.result[j].txid,
                    vout: retUnspent.result[j].vout,
                    };
                transactions.push(transaction);
                amountCanUse += retUnspent.result[j].amount;
                if(amountCanUse > amountPay || amountCanUse == amountPay){
                    break;
                }
            }
            fromAddresses.push(from);
            addresses[to] = amountPay;
            if(amountCanUse > amountPay){
                addresses[from] = amountCanUse - amountPay;
            }
            if(fromAddresses.length == finishLength){
                createSignSend(transactions, addresses);
            }
        }
    });
};

var txWithoutDefiniteFrom = function(request){
    var minConfirmedNum = 6;
    var maxConfirmedNum = 9999999;
    var amountTotal = 0;
    var addresses = {};
    for(var i = 0; i < request.transferInfos.length; i++){
        amountTotal += request.transferInfos[i].amount;
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
            }else{
                var amountCanUse = 0;
                var i = 0;
                var transactions = [];
                rpc.getAddressesByAccount(HOT_ACCOUNT, function(err, ret){
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
                            createSignSend(transactions, addresses);
                        }
                    }
                });
            }
        }
    });
};

var getTransactionInfo = function(txid){
    console.log("txid: " + txid);
    rpc.getRawTransaction(txid, needJson, function(err,ret){
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
                getInputAddresses(ret.result.vin[i], cctx, ret.result.vin.length);
            }
        }
    });
}

var createSignSend = function(transactions, addresses){
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
                            getTransactionInfo(retSend.result);
                        }
                    });
                }
            });
         }
    });
}

proxy.on(RedisProxy.EventType.GET_MISSED_BLOCKS, function(currency, request) {
    console.log(RedisProxy.EventType.GET_MISSED_BLOCKS);
    console.log(currency);
    console.log(request);
    console.log("endIndex hash:" + request.endIndex.id);
    for(var i = request.startIndexs[0].height; i < request.endIndex.height; i++){
        rpc.getBlockHash(i, function(errHash, retHash){
            rpc.getBlock(retHash.result, function(errBlock, retBlock){
                if(errBlock){
                    console.log("errBlock code: " + errBlock.code);
                    console.log("errBlock message: " + errBlock.message);
                }else{
                    var index = new BlockIndex({id: retBlock.result.hash, height:retBlock.result.heigth});
                    console.log(index.id);
                    var prevIndex = new BlockIndex({id:retBlock.result.previousblockhash, height:retBlock.height - 1});
                    var txs = [];
                    var block = new CryptoCurrencyBlock({index:index, preIndex:prevIndex, txs:txs});
                    for(var i = 0; i < retBlock.result.tx.length; i++){
                        rpc.getRawTransaction(retBlock.result.tx[i], needJson, function(errTx, retTx){
                            if(errTx){
                                console.log("errTx code: " + errBlock.code);
                                console.log("errTx message: " + errBlock.message);
                            }else{
                                console.log("txid: " + retTx.result.txid);
                                var cctx = new CryptoCurrencyTransaction({inputs: [], outputs: [],
                                    status: TransferStatus.Confirming});
                                cctx.txid = retTx.result.txid;
                                getOutputAddresses(retTx.result, cctx);
                                for(var j = 0; j < retTx.result.vin.length; j++){
                                    console.log("vout: " + retTx.result.vin[j].vout);
                                    getAllTxsInBlock(retTx.result.vin[j], retTx.result.vin.length,
                                        retBlock.result.tx.length, cctx, block);
                                }
                            }
                        });
                    }
                }
            });
        });
    }
});

var constructBlocks = function(input, txFinishLength, blockFinishLength, cctx, block){
    console.log("input-txid vin: " + input.txid);
    console.log("txFinishLength: " + txFinishLength);
    console.log("blockFinishLength: " + blockFinishLength);
    console.log("input-vout vin: " + input.vout);
    console.log("block.index: " + block.index.hash);
    var vout = input.vout;
    if(input.txid != undefined){
        rpc.getRawTransaction(input.txid, needJson, function(errIn,retIn){
            if(errIn){
                console.log("fail code: " + err.code);
                console.log("fail message: " + err.message);
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
                console.log("cctx.inputs.length: " + cctx.inputs.length);
                if(cctx.inputs.length == txFinishLength){
                    console.log("block.txs.length: " + block.txs.length);
                    console.log("blockFinishLength: " + blockFinishLength);
                    block.txs.push(cctx);
                    if(block.txs.length == blockFinishLength){
                        var blocksMsg = new ({blocks:[]});
                        blocksMsg.blocks.push(block);
                        if(blocksMsg.blocks.length == blocksFinishLength){
                            makeFinalBlocksResponse(request, blocksMsg);
                        }
                    }
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
            block.txs.push(cctx);
            if(block.txs.length == blockFinishLength){
                var blocksMsg = new ({blocks:[]});
                blocksMsg.blocks.push(block);
                if(blocksMsg.blocks.length == blocksFinishLength){
                    makeFinalBlocksResponse(request, blocksMsg);
                }
            }
        }
    }
};

var makeFinalBlocksResponse = function(request, blocksMsg){
    for(var i = 0; i < request.startIndexs.length; i++){
        if(request.startIndexs[i].id == blocksMsg[i].index.id &&
           request.startIndexs[i].height == blocksMsg[i].index.height){
           //TODO:顺序不一定对应
        }else{
            break;
        }
    }
    var startIndex = new BlockIndex({id: blocksMsg[i].id, height: blocksMsg[i].height});
    var blocks = [];
    for(var j = i; j < blocksMsg.length; j++){
        blocks.push(blocksMsg[j]);
    }
    var blocksFinal = new CryptoCurrencyBlocksMessage({startIndex: startIndex, blocks:blocks});
    makeNormalResponse(BitwayRequestType.GET_MISSED_BLOCKS, AUTO_REPORT, Currency.BTC, blocksFinal);
};

var handleTx = function(info) {
    console.log('** TX Received **');
    var tx = info.message.tx.getStandardizedObject();
    console.log(tx);
    console.log("txid: " + tx.hash);
    rpc.getRawTransaction(tx.hash, needJson, function(err,ret){
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
                getInputAddresses(ret.result.vin[i], cctx, ret.result.vin.length);
            }
        }
    });
};

var makeNormalResponse = function(type, requestId, currency, response){
    console.log("type: " + type);
    console.log("finish: " + response);
    console.log("currency: " + currency);
    console.log("requestId: " + requestId);
    switch(type){
        case BitwayRequestType.TRANSFER:
            for(var m = 0; m < response.inputs.length; m++){
                console.log("input address "+ m + ": " + response.inputs[m].address);
                console.log("input amount "+ m + ": " + response.inputs[m].amount);
            }
            for(var n = 0; n < response.outputs.length; n++){
                console.log("output address "+ n + ": " + response.outputs[n].address);
                console.log("output amount "+ n + ": " + response.outputs[n].amount);
            }
            proxy.publish(new BitwayMessage({currency: currency, tx: response}));
            break;
        case BitwayRequestType.GET_MISSED_BLOCKS:
            proxy.publish(new BitwayMessage({currency: currency, blocks: response}));
            break;
        default:
            console.log("Inavalid Type!");
    }
};

var getInputAddresses = function(input, cctx, finishLength) {
    console.log("input-txid vin: " + input.txid);
    console.log("finishLength: " + finishLength);
    console.log("input-vout vin: " + input.vout);
    var vout = input.vout;
    rpc.getRawTransaction(input.txid, needJson, function(errIn,retIn){
        if(errIn){
            console.log("fail code: " + err.code);
            console.log("fail message: " + err.message);
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
                makeNormalResponse(BitwayRequestType.TRANSFER, AUTO_REPORT, Currency.BTC, cctx);
            }
        }
    });
};

var handleInv = function(info) {
    console.log('** Inv **');
    console.log(info.message);
    var inv = info.message;
    console.log(inv.invs[0].type);
    console.log("hash: " + Bitcore.buffertools.toHex(inv.invs[0].hash));
    var invs = info.message.invs;
    info.conn.sendGetData(invs);
};

var handleBlock = function(info) {
    console.log('** Block Received **');
    console.log(info.message);
    //console.log("prev_hash: " + Bitcore.buffertools.toHex(info.message.block.prev_hash));
    //console.log("txs_id: " + Bitcore.buffertools.toHex(info.message.txs[0].hash));
    rpc.getBlockCount(function(err,ret){
        if(err){
            console.log("fail code: " + err.code);
            console.log("fail message: " + err.message);
        }else{
            console.log("current block index: " + ret.result);
            getBlockByIndex(ret.result);
        }
    });
};

var getBlockByIndex = function(index){
    console.log("current block index: " + index);
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
                    var index = new BlockIndex({id: retBlock.result.hash, height:retBlock.result.heigth});
                    console.log(index.id);
                    var prevIndex = new BlockIndex({id:retBlock.result.previousblockhash, height:retBlock.height - 1});
                    var txs = [];
                    var block = new CryptoCurrencyBlock({index:index, preIndex:prevIndex, txs:txs});
                    for(var i = 0; i < retBlock.result.tx.length; i++){
                        rpc.getRawTransaction(retBlock.result.tx[i], needJson, function(errTx, retTx){
                            if(errTx){
                                console.log("errTx code: " + errBlock.code);
                                console.log("errTx message: " + errBlock.message);
                            }else{
                                console.log("txid: " + retTx.result.txid);
                                var cctx = new CryptoCurrencyTransaction({inputs: [], outputs: [],
                                    status: TransferStatus.Confirming});
                                cctx.txid = retTx.result.txid;
                                getOutputAddresses(retTx.result, cctx);
                                for(var j = 0; j < retTx.result.vin.length; j++){
                                    console.log("vout: " + retTx.result.vin[j].vout);
                                    getAllTxsInBlock(retTx.result.vin[j], retTx.result.vin.length,
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

var getAllTxsInBlock = function(input, txFinishLength, blockFinishLength, cctx, block){
    console.log("input-txid vin: " + input.txid);
    console.log("txFinishLength: " + txFinishLength);
    console.log("blockFinishLength: " + blockFinishLength);
    console.log("input-vout vin: " + input.vout);
    console.log("block.index: " + block.index.hash);
    var vout = input.vout;
    if(input.txid != undefined){
        rpc.getRawTransaction(input.txid, needJson, function(errIn,retIn){
            if(errIn){
                console.log("fail code: " + err.code);
                console.log("fail message: " + err.message);
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
                console.log("cctx.inputs.length: " + cctx.inputs.length);
                if(cctx.inputs.length == txFinishLength){
                    console.log("block.txs.length: " + block.txs.length);
                    console.log("blockFinishLength: " + blockFinishLength);
                    block.txs.push(cctx);
                    if(block.txs.length == blockFinishLength){
                        var blocksMsg = new CryptoCurrencyBlocksMessage({blocks:[]});
                        blocksMsg.blocks.push(block);
                        makeNormalResponse(BitwayRequestType.GET_MISSED_BLOCKS, AUTO_REPORT, Currency.BTC, blocksMsg);
                    }
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
            block.txs.push(cctx);
            if(block.txs.length == blockFinishLength){
                var blocksMsg = new CryptoCurrencyBlocksMessage({blocks:[]});
                blocksMsg.blocks.push(block);
                makeNormalResponse(BitwayRequestType.GET_MISSED_BLOCKS, AUTO_REPORT, Currency.BTC, blocksMsg);
            }
        }
    }
}


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
}

var peerman = new PeerManager({
    network: 'testnet'       
});

peerman.addPeer(new Peer('127.0.0.1', 18333));

peerman.on('connection', function(conn) {
    conn.on('inv', handleInv);
    conn.on('block', handleBlock);
    conn.on('tx', handleTx);
});

peerman.start();

var logo = "" +
" _    _ _                     \n" +
"| |__(_) |___ __ ____ _ _  _  \n" +
"| '_ \\ |  _\\ V  V / _` | || | \n" +
"|_.__/_|\\__|\\_/\\_/\\__,_|\\_, | \n" +
"                        |__/  \n";
console.log(logo);
