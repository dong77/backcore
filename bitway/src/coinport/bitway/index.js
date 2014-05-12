/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

var RedisProxy                      = require('./redis/redis_proxy').RedisProxy,
    BitwayRequestType               = require('../../../gen-nodejs/data_types').BitwayRequestType,
    GenerateAddressesResult         = require('../../../gen-nodejs/message_types').GenerateAddressesResult,
    CryptoCurrencyTransaction       = require('../../../gen-nodejs/data_types').CryptoCurrencyTransaction,
    CryptoCurrencyTransactionPort   = require('../../../gen-nodejs/data_types').CryptoCurrencyTransactionPort,
    CryptoCurrencyTransactionPort   = require('../../../gen-nodejs/data_types').CryptoCurrencyTransactionPort,
    CryptoCurrencyBlock             = require('../../../gen-nodejs/data_types').CryptoCurrencyBlock,
    CryptoCurrencyTransactionStatus = require('../../../gen-nodejs/data_types').CryptoCurrencyTransactionStatus,
    Currency                        = require('../../../gen-nodejs/data_types').Currency,
    ErrorCode                       = require('../../../gen-nodejs/data_types').ErrorCode,
    BitwayMessage                   = require('../../../gen-nodejs/message_types').BitwayMessage,
    Bitcore                         = require('bitcore'),
    Peer                            = Bitcore.Peer,
    Networks                        = Bitcore.networks,
    PeerManager                     = require('soop').load('bitcore/PeerManager', {
                                          network: Networks.testnet
                                      });

var proxy = new RedisProxy("btc", "127.0.0.1", "6379");
var RpcClient = Bitcore.RpcClient;
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
var needJson = 1;
var AUTO_REPORT = 0;

proxy.start();

proxy.on(RedisProxy.EventType.GENERATE_ADDRESS, function(currency, request) {
    var startTime = new Date().getTime();
    console.log(RedisProxy.EventType.GENERATE_ADDRESS);
    console.log(currency);
    console.log(request);
    if(request.num < MIN_GENERATE_ADDR_NUM || request.num > MAX_GENERATE_ADDR_NUM){
       proxy.publish(new BitwayMessage({type: BitwayRequestType.GENERATE_ADDRESS,
           currency: Currency.BTC,
           generateAddressResponse: new GenerateAddressesResult({error: ErrorCode.ROBOT_DNA_EXIST})}));
    }else{
        var addresses = [];
        for(var i = 0; i < request.num; i++){
            rpc.getNewAddress(ACCOUNT, function(err, retAddress) {
                var address = "";
                if (err) {
                    console.error('An error occured generate address');
                    console.error(err);
                    proxy.publish(new BitwayMessage({type: BitwayRequestType.GENERATE_ADDRESS,
                        currency: Currency.BTC,
                        generateAddressResponse: new GenerateAddressesResult({error: ErrorCode.ROBOT_DNA_EXIST})}));
                    return;
                }
                console.log(retAddress.result);
                address = retAddress.result;
                var addr = new Bitcore.Address(address);
                if(addr.isValid()){
                    rpc.dumpPrivKey(addr.data, function(err, retPrivKey) {
                        if (err) {
                           console.error('An error occured dumpPrivKey', hash);
                           console.error(err);
                           return;
                        }
                        addresses.push(address);
                        console.log(retPrivKey);
                        if (addresses.length == request.num){
                            console.log("addresses: " + addresses);
                            proxy.publish(new BitwayMessage({type: BitwayRequestType.GENERATE_ADDRESS,
                                currency: Currency.BTC,
                            generateAddressResponse: new GenerateAddressesResult({error: ErrorCode.OK,
                                addresses: addresses})}));
                            console.log("costTime: " + (new Date().getTime() - startTime) + "ms");
                       }
                    });
                }
            });
        }
    }
});

proxy.on(RedisProxy.EventType.TRANSFER, function(requestId, currency, request) {
    console.log('** TransferRequest Received **');
    console.log(RedisProxy.EventType.TRANSFER);
    console.log(requestId);
    console.log(requestId === 123);
    console.log(currency);
    console.log(request);
    var fromAddresses = [];
    fromAddresses[0] = request.from;
    var toAddress = request.to;
    var amount = request.amount;
    var minConfirmedNum = 6;
    var maxConfirmedNum = 9999999;
    var addresses = {};
    addresses[request.to] = request.amount;
    rpc.listUnspent(minConfirmedNum, maxConfirmedNum, function(err, ret){
        if(err){
            console.error('An error occured listUnspent', hash);
            console.error(err);
            return;
        }else{
            if(result.length == 0){
            //TODO:
            }else{
                var amountCanUse = 0;
                var i = 0;
                var transactions = [];
                for(i = 0; i < result.length; i++){
                    amountCanUse += result[i].amount;
                    var transaction = {
                        txid: result[i].txid,
                        vout: result[i].amount,
                        };
                    if(amountCanUse > (amount + tip)){//TODO:
                        addresses[request.to] = request.amount;
                        addresses[changeAddress] = amountCanUse - amount - tip;
                        break;
                    }else if(amountCanUse == (amount + tip)){
                        addresses[request.to] = request.amount;
                        break;
                    }
                }
                if(i <= result.length){
                        createSignSend(transactions, addresses);
                }
            }
        }
    });
});

var getTransactionInfo = function(txid){
    rpc.getRawTransaction(txid, needJson, function(err,ret){
        if(err){
            console.log("fail code: " + err.code);
            console.log("fail message: " + err.message);
        }else{
            var cctx = new CryptoCurrencyTransaction({inputs: [], outputs: [],
                status: CryptoCurrencyTransactionStatus.PENDING});
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
    rpc.createRawTransaction(transactions, addresses, function(errCreate, retCreate){
        if(errCreate){
            //TODO:
        }else{
            rpc.signRawTransaction(retCreate.result.transaction, function(errSign, retSign){
                if(errSign){
                    //TODO:
                }else{
                    rpc.sendRawTransaction(retSign.result.hex, function(errSend, retSend){
                        if(errSend){
                            //TODO:
                        }else{
                            getTransactionInfo(retSend.result.hex);
                        }
                    });
                }
            });
         }
    });
}

proxy.on(RedisProxy.EventType.QUERY_ADDRESS, function(requestId, currency, request) {
    console.log(RedisProxy.EventType.QUERY_ADDRESS);
    console.log(requestId);
    console.log(currency);
    console.log(request);
});

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
                status: CryptoCurrencyTransactionStatus.PENDING});
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
            proxy.publish(new BitwayMessage({type: type,
                requestId: requestId, currency: currency, tx: response}));
            break;
        case BitwayRequestType.GET_MISSED_BLOCKS:
            proxy.publish(new BitwayMessage({type: type,
                requestId: requestId, currency: currency, block: response}));
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
            console.log("fail code: " + errHash.code);
            console.log("fail message: " + errHash.message);
        //TODO:
        }else{
            rpc.getBlock(retHash.result, function(errBlock, retBlock){
                if(errBlock){
                    console.log("fail code: " + errBlock.code);
                    console.log("fail message: " + errBlock.message);
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
                                    status: CryptoCurrencyTransactionStatus.PENDING});
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
                        makeNormalResponse(BitwayRequestType.GET_MISSED_BLOCKS, AUTO_REPORT, Currency.BTC, block);
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
                makeNormalResponse(BitwayRequestType.GET_MISSED_BLOCKS, AUTO_REPORT, Currency.BTC, block);
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

var peerman = new PeerManager();

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
