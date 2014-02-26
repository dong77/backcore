#!/bin/sh
#
# Copyright 2014 Coinport Inc. All Rights Reserved.
# Author: c@coinport.com (Chao Ma)

wget "http://localhost:8888/registerUser?name=hoss&pw=1234" -O ./tmp 2> /dev/null
wget "http://localhost:8888/dw?uid=0&dw=1&coin=1000&amount=1000000" -O ./tmp 2> /dev/null

wget "http://localhost:8888/placeOrder?uid=0&f=1000&t=1&q=56&bos=1&p=134" -O ./tmp 2> /dev/null
wget "http://localhost:8888/cancelOrder?uid=0&id=3&f=1000&t=1" -O ./tmp 2> /dev/null
rm ./tmp
