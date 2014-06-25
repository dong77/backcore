#!/bin/sh #
# Copyright 2014 Coinport Inc. All Rights Reserved.
# Author: xiaolu@coinport.com (Wu Xiaolu)


# plugin return codes:
# 0     OK
# 1     Warning
# 2     Critical
# 3     Unknown
res=`curl https://coinport.com/api/open/reserve/BTC`
echo "=====>>>>>>>  response is "$res
isOk=`echo $res | grep '"success":true,"code":0' | wc -l`

if [ "$isOk" -eq "1" ];then
  echo "all service is ok now"
  exit 0
else
  echo "some service is not ok"
  exit 2
fi
