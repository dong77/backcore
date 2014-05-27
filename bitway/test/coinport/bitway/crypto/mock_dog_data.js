/**
 *Copyright 2014 Coinport Inc. All Rights Reserved.
 *Author: YangLi--ylautumn84@gmail.com
 *Filename: mock_dog_data.js
 *Description: 
 */
'use strict'

var MockDogData = module.exports.MockDogData = function() {
};

MockDogData.heightHash = {
    '93715' : '34ed2ad068ae662a465b08f2beb18ee251c6543750def337bf5906d410842d7e',
    '93716' : '778835f7edb20df20174afe9316ae2339c9526c9bd2dca9b8d81406f774d6e0b',
    '93717' : '05a23efad2b301d280d5f5379ca5b31d586c685eab2b30ca543f213ce067afc6',
    '93718' : '79123d5fae236fa9f49b7a6f4f3c48367ca96fd59b1de5450827099b9260fa5d',
    '93719' : '94faf7c2feee0451014a886e949e8b5f588920d3417b247bb2a637d5ba003c71',
    '93720' : '600e252d0f2922b0bc255294c15827551d05818409ff887d5ac8e5b6797e5958',
    '93721' : 'd661700668f723ea29be1461d6dedd4cd27c52480896ab368fad1100d15f35a5',
};

MockDogData.hashBlock = {
    '05a23efad2b301d280d5f5379ca5b31d586c685eab2b30ca543f213ce067afc6':
    {
        "hash" : "05a23efad2b301d280d5f5379ca5b31d586c685eab2b30ca543f213ce067afc6",
        "confirmations" : 286,
        "size" : 189,
        "height" : 93717,
        "version" : 2,
        "merkleroot" : "acb2aa6da8fb98d9ee650de1aad8ae181b8b387fd3af2d7c89d0003efa1bff87",
        "tx" : [
            "acb2aa6da8fb98d9ee650de1aad8ae181b8b387fd3af2d7c89d0003efa1bff87"
        ],
        "time" : 1401086944,
        "nonce" : 13620,
        "bits" : "1e0fffff",
        "difficulty" : 0.00024414,
        "chainwork" : "00000000000000000000000000000000000000000000000000000034f24c4ab8",
        "previousblockhash" : "778835f7edb20df20174afe9316ae2339c9526c9bd2dca9b8d81406f774d6e0b",
        "nextblockhash" : "79123d5fae236fa9f49b7a6f4f3c48367ca96fd59b1de5450827099b9260fa5d"
    },
    "79123d5fae236fa9f49b7a6f4f3c48367ca96fd59b1de5450827099b9260fa5d":
    {
        "hash" : "79123d5fae236fa9f49b7a6f4f3c48367ca96fd59b1de5450827099b9260fa5d",
        "confirmations" : 340,
        "size" : 189,
        "height" : 93718,
        "version" : 2,
        "merkleroot" : "367e72d39abc002d930745b64a4b36d5af783094b93da5c2e8a2cc82ece9ad3a",
        "tx" : [
            "367e72d39abc002d930745b64a4b36d5af783094b93da5c2e8a2cc82ece9ad3a"
        ],
        "time" : 1401087059,
        "nonce" : 95347,
        "bits" : "1e0fffff",
        "difficulty" : 0.00024414,
        "chainwork" : "00000000000000000000000000000000000000000000000000000034f25c4ab9",
        "previousblockhash" : "05a23efad2b301d280d5f5379ca5b31d586c685eab2b30ca543f213ce067afc6",
        "nextblockhash" : "94faf7c2feee0451014a886e949e8b5f588920d3417b247bb2a637d5ba003c71"
    }
};

MockDogData.txidTx = {
    "acb2aa6da8fb98d9ee650de1aad8ae181b8b387fd3af2d7c89d0003efa1bff87":
    {
        "hex" : "01000000010000000000000000000000000000000000000000000000000000000000000000ffffffff0d03156e010101062f503253482fffffffff010086b167e836000023210386b46794ca0ec0e696feb6388b6ca4011c1e6ab9f9bc567e63baae1e348fd5b0ac00000000",
        "txid" : "acb2aa6da8fb98d9ee650de1aad8ae181b8b387fd3af2d7c89d0003efa1bff87",
        "version" : 1,
        "locktime" : 0,
        "vin" : [
            {
                "coinbase" : "03156e010101062f503253482f",
                "sequence" : 4294967295
            }
        ],
        "vout" : [
            {
                "value" : 603718.00000000,
                "n" : 0,
                "scriptPubKey" : {
                    "asm" : "0386b46794ca0ec0e696feb6388b6ca4011c1e6ab9f9bc567e63baae1e348fd5b0 OP_CHECKSIG",
                    "hex" : "210386b46794ca0ec0e696feb6388b6ca4011c1e6ab9f9bc567e63baae1e348fd5b0ac",
                    "reqSigs" : 1,
                    "type" : "pubkey",
                    "addresses" : [
                        "ne5quHyCBQc9kL4zHxR38gVSFu3tb9AEbN"
                    ]
                }
            }
        ],
        "blockhash" : "05a23efad2b301d280d5f5379ca5b31d586c685eab2b30ca543f213ce067afc6",
        "confirmations" : 293,
        "time" : 1401086944,
        "blocktime" : 1401086944
    },
    "367e72d39abc002d930745b64a4b36d5af783094b93da5c2e8a2cc82ece9ad3a":
    {
        "hex" : "01000000010000000000000000000000000000000000000000000000000000000000000000ffffffff0d03166e010102062f503253482fffffffff0100d85f66ae300000232103e14b64c1ddeae0803a319d179be3878872f20dcad060a8188fa72f978723da96ac00000000",
        "txid" : "367e72d39abc002d930745b64a4b36d5af783094b93da5c2e8a2cc82ece9ad3a",
        "version" : 1,
        "locktime" : 0,
        "vin" : [
            {
                "coinbase" : "03166e010102062f503253482f",
                "sequence" : 4294967295
            }
        ],
        "vout" : [
            {
                "value" : 535256.00000000,
                "n" : 0,
                "scriptPubKey" : {
                    "asm" : "03e14b64c1ddeae0803a319d179be3878872f20dcad060a8188fa72f978723da96 OP_CHECKSIG",
                    "hex" : "2103e14b64c1ddeae0803a319d179be3878872f20dcad060a8188fa72f978723da96ac",
                    "reqSigs" : 1,
                    "type" : "pubkey",
                    "addresses" : [
                        "nrrPrREiPuZua2XmL7YMj4BDSCuZKhemQo"
                    ]
                }
            }
        ],
        "blockhash" : "79123d5fae236fa9f49b7a6f4f3c48367ca96fd59b1de5450827099b9260fa5d",
        "confirmations" : 344,
        "time" : 1401087059,
        "blocktime" : 1401087059
    }
};

MockDogData.addressesByAccount = {
    "hot":
    [
        "nfa5Go7Ni6fVfvRDX9EsyR2khE3kVbDti7",
        "nX4ZPuVe39Y1hnQtjXL8VYGC9gFBXhJDoj",
        "nk3det1CrJRYnBtb8ceUVLktoJzHgxZuVZ",
        "ndJWiS4PotDkcuS6v8rQ1xi2rH54dfQGeu",
        "nYKhKjvRWSSxstk4kATTZC6HG5i8KEoTge",
        "nfUnXG4h8FEPPZiaNmFpjLkG2jzeYnXYK6",
        "nZpMFnwrEdLtHVgVz1KbzuwtKKSXa6DbUs",
        "nftHWxSL9qtgMDffbHGGnkurF6hezFL9xQ",
        "nnmqMVxzvCpDAqzTjPA49ZqDTveu8k1Ygj",
        "nq23nQDcEwP9Zf6k9btDrRapv8EmGF6aZc"
    ]
};


MockDogData.listUnspent = {
    "nmoEYptTPTRfzn9U8x58q97vioduyS98dE":
    [
        {
        "txid" : "f72cf190df6c13b704c4830fc043a4b7b0ef56ad3d4bcf0324928365dd82cfdc",
        "vout" : 0,
        "address" : "nmoEYptTPTRfzn9U8x58q97vioduyS98dE",
        "account" : "customers",
        "scriptPubKey" : "76a914c11c4637c1c016c2a22ff4231f9971d852777bff88ac",
        "amount" : 10.00000000,
        "confirmations" : 153
        },
        {
        "txid" : "854dbdbefde63e0435fee1519e3893860d6687061ebb79624a0542968a29a92b",
        "vout" : 1,
        "address" : "nmoEYptTPTRfzn9U8x58q97vioduyS98dE",
        "account" : "customers",
        "scriptPubKey" : "76a914c11c4637c1c016c2a22ff4231f9971d852777bff88ac",
        "amount" : 5.00000000,
        "confirmations" : 4853
        }
    ],
    "nfa5Go7Ni6fVfvRDX9EsyR2khE3kVbDti7,nX4ZPuVe39Y1hnQtjXL8VYGC9gFBXhJDoj,nk3det1CrJRYnBtb8ceUVLktoJzHgxZuVZ,ndJWiS4PotDkcuS6v8rQ1xi2rH54dfQGeu,nYKhKjvRWSSxstk4kATTZC6HG5i8KEoTge,nfUnXG4h8FEPPZiaNmFpjLkG2jzeYnXYK6,nZpMFnwrEdLtHVgVz1KbzuwtKKSXa6DbUs,nftHWxSL9qtgMDffbHGGnkurF6hezFL9xQ,nnmqMVxzvCpDAqzTjPA49ZqDTveu8k1Ygj,nq23nQDcEwP9Zf6k9btDrRapv8EmGF6aZc":
    [
        {
            "txid" : "6ea96574c2325dd1c2bd00f3d6e8b34dfbedb72593e34f4f15f67cb6295d8cf5",
            "vout" : 0,
            "address" : "ndJWiS4PotDkcuS6v8rQ1xi2rH54dfQGeu",
            "account" : "hot",
            "scriptPubKey" : "76a91463ecb3134112ebaf2515c10ae7a82ac20b06ae9d88ac",
            "amount" : 9.99990000,
            "confirmations" : 4900
        },
        {
            "txid" : "854dbdbefde63e0435fee1519e3893860d6687061ebb79624a0542968a29a92b",
            "vout" : 0,
            "address" : "nYKhKjvRWSSxstk4kATTZC6HG5i8KEoTge",
            "account" : "hot",
            "scriptPubKey" : "76a9142d4d57a106bffd2120b5469b67d279640076ccb288ac",
            "amount" : 74.99990000,
            "confirmations" : 4900
        },
        {
            "txid" : "95a73d840d5aed94af9c5c89c309d6878c9d3be7ae1bf6a6957af39d81556eb6",
            "vout" : 0,
            "address" : "nfa5Go7Ni6fVfvRDX9EsyR2khE3kVbDti7",
            "account" : "hot",
            "scriptPubKey" : "76a9147cce5e58be7184a99fd80963094718922915080e88ac",
            "amount" : 45.00000000,
            "confirmations" : 4958
        },
        {
            "txid" : "b929198cd413b95eaca8ec9e6d92954b09b0b6773a7e1d34231cd6ff9adcbbed",
            "vout" : 0,
            "address" : "nfa5Go7Ni6fVfvRDX9EsyR2khE3kVbDti7",
            "account" : "hot",
            "scriptPubKey" : "76a9147cce5e58be7184a99fd80963094718922915080e88ac",
            "amount" : 59.00000000,
            "confirmations" : 4919
        },
        {
            "txid" : "fca649288b456891a17f3997f6a772a4890cb15a025cfffe13e4896b5a53da2a",
            "vout" : 0,
            "address" : "nZpMFnwrEdLtHVgVz1KbzuwtKKSXa6DbUs",
            "account" : "hot",
            "scriptPubKey" : "76a9143db0c76c6e31737bf577f1dacc9d7b6c11494ae088ac",
            "amount" : 9793.99990000,
            "confirmations" : 4895
        }
    ]
};




