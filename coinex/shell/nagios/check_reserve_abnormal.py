#!/usr/bin/python
# -*- coding: utf-8 -*-
#
# Copyright 2014 Coinport.com. All Rights Reserved.
# Author: Wu Xiaolu
# Desp: script checking the reverve rate less than 100%
 
import httplib2
import json

currency = ['BTC', 'LTC', 'DOGE', 'BC', 'DRK', 'VRC', 'ZET', 'BTSX', 'NXT', 'XRP']
h = httplib2.Http(".cache") 
totalReserve = {}
totalPlateReserve = {}

print "Begin Check Reserve"
print "---------------------------------------"

(rh, reserveTotalContent) = h.request("https://exchange.coinport.com/api/account/-1000")
totalUserReserveJson = json.loads(reserveTotalContent)
if (totalUserReserveJson['success'] == False):
    print "user reserves fetch failed"
    exit(2)
else:
    for c in currency:
        totalReserve[c] = totalUserReserveJson['data']['accounts'][c]['total']['value']

print totalReserve

for c in currency:
    (resp_headers, content) = h.request("https://exchange.coinport.com/api/open/reserve/" + c, "GET")
    result = json.loads(content)
    if (result['success'] == False):
        print "fetch reserve data failed, CURRENCY : " + c
        exit(2)
    if (result['success'] == True):
        totalPlateReserve[c] = result['data']['total']['value']

for c in currency:
    if (totalReserve[c] == 0 or round(totalPlateReserve[c] / totalReserve[c], 4) < 1.0):
        print "curreny " + c + " reverse rate is less than 100%"
        exit(2)

print "All reserves are Ok"
exit(0)
