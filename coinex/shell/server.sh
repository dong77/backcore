#!/bin/sh
#
# Copyright 2014 Coinport Inc. All Rights Reserved.
# Author: c@coinport.com (Chao Ma)

sbt "project coinex-backend" "runMain com.coinport.coinex.CoinexApp 25551 127.0.0.1:25551 * 127.0.0.1 private.conf"

# java -server -cp coinex-backend/target/scala-2.10/coinex-backend-assembly-1.0.5-SNAPSHOT.jar "com.coinport.coinex.CoinexApp" 25551 "127.0.0.1:25551" "*" "127.0.0.1"
