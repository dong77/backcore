/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

var RedisProxy                      = require('./redis/redis_proxy').RedisProxy,
    DataTypes                       = require('../../../gen-nodejs/data_types')
    MessageTypes                    = require('../../../gen-nodejs/message_types')
    Bitcore                         = require('bitcore'),
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

var PeerManager = Bitcore.PeerManager;

var proxy = new RedisProxy("BTC", "127.0.0.1", "6379");
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

var makeNormalResponse = function(type, currency, response){
    console.log("type: " + type);
    console.log("currency: " + currency);
    switch(type){
        case BitwayResponseType.GENERATE_ADDRESS:
            console.log("GENERATE_ADDRESS");
            proxy.publish(new BitwayMessage({currency: currency, generateAddressResponse: response}));
            break;
        case BitwayResponseType.TRANSFER:
        case BitwayResponseType.TRANSACTION:
            console.log("TRANSACTION REPORT: " + currency);
            displayTxContent(response);
            proxy.publish(new BitwayMessage({currency: currency, tx: response}));
//            rpc.getBlockCount(function(errCount,retCount){
//                if(errCount){
//                console.log("errCount code: " + errCount.code);
//                console.log("errCount message: " + errCount.message);
//                }else{
//                    console.log("current block index: " + retCount.result);
//                    getBlockByIndex(retCount.result);
//                }
//            });
            break;
        case BitwayResponseType.GET_MISSED_BLOCKS:
        case BitwayResponseType.AUTO_REPORT_BLOCKS:
            console.log("BLOCK REPORT: " + currency);
            console.log("response.blocks.length:" + response.blocks.length);
            displayBlocksContent(response.blocks);
            proxy.publish(new BitwayMessage({currency: currency, blocksMsg: response}));
            break;
        default:
            console.log("Inavalid Type!");
    }
};

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
    }
};

proxy.on(RedisProxy.EventType.GENERATE_ADDRESS, function(currency, request) {
    var startTime = new Date().getTime();
    console.log(RedisProxy.EventType.GENERATE_ADDRESS);
    console.log(currency);
    console.log(request);
    if (request.num < MIN_GENERATE_ADDR_NUM || request.num > MAX_GENERATE_ADDR_NUM) {
        var generateAddressResponse = new GenerateAddressesResult({error: ErrorCode.INVALID_REQUEST_ADDRESS_NUM});
        makeNormalResponse(BitwayResponseType.GENERATE_ADDRESS, Currency.BTC, generateAddressResponse);

    } else {
        var addresses = [];
        for (var i = 0; i < request.num; i++) {
            rpc.getNewAddress(ACCOUNT, function(errAddress, retAddress) {
                if (errAddress) {
                    console.error('An error occured generate address');
                    console.error(errAddress);
                    var generateAddressResponse = new GenerateAddressesResult({error: ErrorCode.RPC_ERROR});
                    makeNormalResponse(BitwayResponseType.GENERATE_ADDRESS, Currency.BTC, generateAddressResponse);
                    return;
                }
                var  address = retAddress.result;
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
                            var generateAddressResponse = new GenerateAddressesResult({error: ErrorCode.OK,
                                    addresses: addresses, addressType: CryptoCurrencyAddressType.UNUSED});
                            makeNormalResponse(BitwayResponseType.GENERATE_ADDRESS, Currency.BTC, generateAddressResponse);
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
    //    transferInfos: transferInfos, type:TransferType.WITHDRAWAL});
    var request = new TransferCryptoCurrency({currency: Currency.BTC,
        transferInfos: transferInfos, type:TransferType.USER_TO_HOT});*/
    switch(request.type){
        case TransferType.WITHDRAWAL:
        case TransferType.HOT_TO_COLD:
            txWithDefiniteTo(request);
            break;
        case TransferType.USER_TO_HOT:
            txWithDefiniteFrom(request);
            break;
        default:
            console.log("Invalid request type: " + request.type);
    }
});

var txWithDefiniteFrom = function(request){
    var amountTotal = 0;
    var addresses = {};
    var transactions = [];
    var fromAddresses = [];
    var ids = [];
    rpc.getAddressesByAccount(HOT_ACCOUNT, function(errAddr, retAddr){
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
                            makeTransaction(request.transferInfos[i], request.transferInfos.length,
                                fromAddresses, transactions, addresses, ids);
                        }
                    }
                });
            }else{
                var toPos = Math.floor(Math.random()*retAddr.result.length);
                var toAddress = retAddr.result[toPos];
                console.log("toPos: " + toPos + " toAddr: " + toAddress);
                for(var i = 0; i < request.transferInfos.length; i++){
                    request.transferInfos[i].to = toAddress;
                    makeTransaction(request.transferInfos[i], request.transferInfos.length,
                        fromAddresses, transactions, addresses, ids);
                }
            }
        }
    });
}

var makeTransaction = function(transferInfo, finishLength, fromAddresses, transactions, addresses, ids){
    var from = transferInfo.from;
    var to = transferInfo.to;
    var amountPay = transferInfo.amount;
    var minConfirmedNum = 6;
    var maxConfirmedNum = 9999999;
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
                finishTransfer(transactions, addresses, ids);
            }
        }
    });
};

var txWithDefiniteTo = function(request){
    var minConfirmedNum = 6;
    var maxConfirmedNum = 9999999;
    var amountTotal = 0;
    var addresses = {};
    var ids = [];
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
                            finishTransfer(transactions, addresses, ids);
                        }
                    }
                });
            }
        }
    });
};

var getTransactionInfo = function(txid, ids){
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
                saveTransferIds(ret.result.vin[i], cctx, ret.result.vin.length, ids);
            }
        }
    });
}

var saveTransferIds = function(input, cctx, finishLength, ids){
    console.log("saveTransferIds input-txid: " + input.txid);
    console.log("finishLength: " + finishLength);
    console.log("input-vout: " + input.vout);
    var vout = input.vout;
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
                proxy.innerClient.set(sigId, ids, function(errRedis, reply){
                    if(errRedis){
                        console.log("errRedis: " + errRedis);
                    }else{
                        cctx.sigId = sigId;
                        console.log("cctx.sigId: " + cctx.sigId);
                        makeNormalResponse(BitwayResponseType.TRANSACTION, Currency.BTC, cctx);
                    }
                });
            }
        }
    });
};


var finishTransfer = function(transactions, addresses, ids){
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
                            getTransactionInfo(retSend.result, ids);
                        }
                    });
                }
            });
         }
    });
}

proxy.on(RedisProxy.EventType.GET_MISSED_BLOCKS, function(currency, request) {
    console.log(RedisProxy.EventType.GET_MISSED_BLOCKS);
//    var startIndexs = [];
//    var startA = new BlockIndex({id: '00000000000475132295d80ad03f64dcb5ca63d72da1c8fe4cf00b92fb2a4d4c', height:242673});
//    var startB = new BlockIndex({id: '00000000b71ef84d66c0c96df20673001c78842fdca2ae0775b4c8f1e0bea902', height:242674});
//    var endIndex = new BlockIndex({id: '0000000000003b7df09b2bda05ab287bf9c6d0b4f7b4c4c298dcf20c9914bee7', height:242677});
//    startIndexs.push(startA);
//    startIndexs.push(startB);
//    var request = new GetMissedCryptoCurrencyBlocks({startIndexs: startIndexs, endIndex: endIndex});
//    console.log(request);
    console.log("endIndex hash:" + request.endIndex.id);
    var blocksFinishLength = request.endIndex.height - request.startIndexs[0].height +1;
    var blocksMsg = new CryptoCurrencyBlocksMessage({blocks:[]});
    for(var iHeight = request.startIndexs[0].height; iHeight < request.endIndex.height + 1; iHeight++){
        console.log("iHeight: " + iHeight);
        rpc.getBlockHash(iHeight, function(errHash, retHash){
            rpc.getBlock(retHash.result, function(errBlock, retBlock){
                if(errBlock){
                    console.log("errBlock code: " + errBlock.code);
                    console.log("errBlock message: " + errBlock.message);
                }else{
                    var index = new BlockIndex({id: retBlock.result.hash, height:retBlock.result.height});
                    console.log(index.id);
                    var prevIndex = new BlockIndex({id:retBlock.result.previousblockhash, height:retBlock.height - 1});
                    var txs = [];
                    var block = new CryptoCurrencyBlock({index:index, prevIndex:prevIndex, txs:txs});
                    for(var i = 0; i < retBlock.result.tx.length; i++){
                        rpc.getRawTransaction(retBlock.result.tx[i], needJson, function(errTx, retTx){
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
                                    constructBlocks(retTx.result.vin[j], retTx.result.vin.length,
                                        retBlock.result.tx.length, blocksFinishLength, request, cctx, block, blocksMsg);
                                }
                            }
                        });
                    }
                }
            });
        });
    }
});

var constructBlocks = function(input, txFinishLength, blockFinishLength, blocksFinishLength, request, cctx, block, blocksMsg){
    console.log("input-txid: " + input.txid);
    console.log("txFinishLength: " + txFinishLength);
    console.log("blockFinishLength: " + blockFinishLength);
    console.log("blocksFinishLength: " + blocksFinishLength);
    console.log("input-vout: " + input.vout);
    console.log("block.index: " + block.index.id);
    var vout = input.vout;
    if(input.txid != undefined){
        rpc.getRawTransaction(input.txid, needJson, function(errIn,retIn){
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
                    proxy.innerClient.get(sigId, function(errRedis, reply){
                        if(errRedis){
                            console("errRedis: " + errRedis);
                        }else{
                            block.txs.push(cctx);
                            if(block.txs.length == blockFinishLength){
                                console.log("*****blocksMsg.blocks.length: " + blocksMsg.blocks.length);
                                blocksMsg.blocks.push(block);
                                if(blocksMsg.blocks.length == blocksFinishLength){
                                    makeFinalBlocksResponse(request, blocksMsg);
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
            proxy.innerClient.get(sigId, function(errRedis, reply){
                if(errRedis){
                    console("errRedis: " + errRedis);
                }else{
                    block.txs.push(cctx);
                    if(block.txs.length == blockFinishLength){
                        console.log("*****blocksMsg.blocks.length: " + blocksMsg.blocks.length);
                        blocksMsg.blocks.push(block);
                        if(blocksMsg.blocks.length == blocksFinishLength){
                            makeFinalBlocksResponse(request, blocksMsg);
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

var makeFinalBlocksResponse = function(request, blocksMsg){
    blocksMsg.blocks.sort(compare);
    for(var i = 0; i < request.startIndexs.length; i++){
        if(request.startIndexs[i].id == blocksMsg.blocks[i].index.id &&
           request.startIndexs[i].height == blocksMsg.blocks[i].index.height){
        }else{
            break;
        }
    }
    var startIndex = new BlockIndex({id: blocksMsg.blocks[i].index.id, height: blocksMsg.blocks[i].index.height});
    var blocks = [];
    for(var j = i; j < blocksMsg.blocks.length; j++){
        blocks.push(blocksMsg.blocks[j]);
    }
    var blocksFinal = new CryptoCurrencyBlocksMessage({startIndex: startIndex, blocks:blocks});
    makeNormalResponse(BitwayResponseType.GET_MISSED_BLOCKS, Currency.BTC, blocksFinal);
};

var handleTx = function(info) {
    console.log('** TX Received **');
    var tx = info.message.tx.getStandardizedObject();
    //console.log(tx);
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

var getInputAddresses = function(input, cctx, finishLength) {
    console.log("getInputAddresses input-txid: " + input.txid);
    console.log("finishLength: " + finishLength);
    console.log("input-vout: " + input.vout);
    var vout = input.vout;
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
                        makeNormalResponse(BitwayResponseType.TRANSACTION, Currency.BTC, cctx);
                    }
                });
            }
        }
    });
};

var handleInv = function(info) {
    console.log('** Inv **');
    /*console.log(info.message);
    var inv = info.message;
    console.log(inv.invs[0].type);
    console.log("hash: " + Bitcore.buffertools.toHex(inv.invs[0].hash));*/
    var invs = info.message.invs;
    info.conn.sendGetData(invs);
};

var handleBlock = function(info) {
    console.log('** Block Received **' + (new Date().getTime()));
    //console.log(info.message);
    //console.log("prev_hash: " + Bitcore.buffertools.toHex(info.message.block.prev_hash));
    //console.log("txs_id: " + Bitcore.buffertools.toHex(info.message.txs[0].hash));
    rpc.getBlockCount(function(errCount,retCount){
        if(errCount){
            console.log("errCount code: " + errCount.code);
            console.log("errCount message: " + errCount.message);
        }else{
            console.log("current block index: " + retCount.result);
            getBlockByIndex(retCount.result);
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
                    var index = new BlockIndex({id: retBlock.result.hash, height:retBlock.result.height});
                    console.log(index.id);
                    var prevIndex = new BlockIndex({id:retBlock.result.previousblockhash,
                        height:retBlock.result.height - 1});
                    var txs = [];
                    var block = new CryptoCurrencyBlock({index:index, prevIndex:prevIndex, txs:txs});
                    for(var i = 0; i < retBlock.result.tx.length; i++){
                        rpc.getRawTransaction(retBlock.result.tx[i], needJson, function(errTx, retTx){
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
    /*console.log("input-txid: " + input.txid);
    console.log("txFinishLength: " + txFinishLength);
    console.log("blockFinishLength: " + blockFinishLength);
    console.log("input-vout: " + input.vout);
    console.log("block.index: " + block.index.id);*/
    var vout = input.vout;
    if(input.txid != undefined){
        rpc.getRawTransaction(input.txid, needJson, function(errIn,retIn){
            if(errIn){
                console.log("errIn code: " + errIn.code);
                console.log("errIn message: " + errIn.message);
            }else{
                for(var j = 0; j < retIn.result.vout.length; j++){
                    if(vout == retIn.result.vout[j].n){
                        var input = new CryptoCurrencyTransactionPort();
                        input.address = retIn.result.vout[j].scriptPubKey.addresses.toString();
                        input.amount = retIn.result.vout[j].value;
                        //console.log("succsess match input.address: " + input.address);
                        cctx.inputs.push(input);
                    }
                }
                //console.log("cctx.inputs.length: " + cctx.inputs.length);
                if(cctx.inputs.length == txFinishLength){
                    //console.log("block.txs.length: " + block.txs.length);
                    var sigId = getSigId(cctx);
                    proxy.innerClient.get(sigId, function(errRedis, reply){
                        if(errRedis){
                            console("errRedis: " + errRedis);
                        }else{
                            console.log("cctx.ids: " + reply);
                            cctx.ids = reply;
                            block.txs.push(cctx);
                            if(block.txs.length == blockFinishLength){
                                var blocksMsg = new CryptoCurrencyBlocksMessage({blocks:[]});
                                blocksMsg.blocks.push(block);
                                makeNormalResponse(BitwayResponseType.AUTO_REPORT_BLOCKS, Currency.BTC, blocksMsg);
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
            proxy.innerClient.get(sigId, function(errRedis, reply){
                if(errRedis){
                    console("errRedis: " + errRedis);
                }else{
                    console.log("cctx.ids: " + reply);
                    cctx.ids = reply;
                    block.txs.push(cctx);
                    if(block.txs.length == blockFinishLength){
                        var blocksMsg = new CryptoCurrencyBlocksMessage({blocks:[]});
                        blocksMsg.blocks.push(block);
                        makeNormalResponse(BitwayResponseType.AUTO_REPORT_BLOCKS, Currency.BTC, blocksMsg);
                    }
                }
            });
        }
    }
}

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
    cctx.sigId = sigId;
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
