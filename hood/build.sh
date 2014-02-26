#!/bin/sh
#
# Copyright 2014 Coinport Inc. All Rights Reserved.
# Author: c@coinport.com (Chao Ma)

thrift --gen js:node -o ./ proto/bp.thrift
