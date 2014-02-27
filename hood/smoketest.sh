#!/bin/sh
#
# Copyright 2014 Coinport Inc. All Rights Reserved.
# Author: c@coinport.com (Chao Ma)

wget "http://localhost:8888/registerUser?n=hoss&pw=1234" -O ./tmp 2> /dev/null
wget "http://localhost:8888/dw?u=0&d=1&c=1000&a=1000000" -O ./tmp 2> /dev/null

wget "http://localhost:8888/placeOrder?u=0&f=1000&t=1&q=56&b=1&p=134" -O ./tmp 2> /dev/null
wget "http://localhost:8888/cancelOrder?u=0&i=3&f=1000&t=1" -O ./tmp 2> /dev/null
rm ./tmp
