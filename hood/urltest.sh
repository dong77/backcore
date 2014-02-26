#!/bin/sh
#
# Copyright 2014 Coinport Inc. All Rights Reserved.
# Author: c@coinport.com (Chao Ma)

wget "http://localhost:8888/registerUser?name=hoss&pw=1234" -O ./tmp 2> /dev/null
wget "http://localhost:8888/dw?uid=1&type=deposit&coin=cny&amount=100" -O ./tmp 2> /dev/null
wget "http://localhost:8888/placeOrder?uid=1&f=cny&t=btc&q=56&bos=b&p=134" -O ./tmp 2> /dev/null
wget "http://localhost:8888/cancelOrder?uid=1&id=3&f=cny&t=btc" -O ./tmp 2> /dev/null
rm ./tmp
