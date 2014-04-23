#!/bin/sh
#
# Copyright 2014 Coinport Inc. All Rights Reserved.
# Author: c@coinport.com (Chao Ma)

thrift --gen js:node -o ./ proto/data.thrift
thrift --gen js:node -o ./ proto/message.thrift
thrift --gen js:node -o ./ proto/test.thrift

node src/coinport/bitway/index.js
