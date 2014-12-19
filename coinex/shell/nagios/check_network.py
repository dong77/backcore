#!/usr/bin/python
# -*- coding: utf-8 -*-
#
# Copyright 2014 Coinport.com. All Rights Reserved.
# Author: Wu Xiaolu
# Desp: script checking the coins network is or isn't ok
 
import httplib2
import json

MAX_DELAY = 60 * 60 * 1000
currency = ['BTC', 'LTC', 'DOGE', 'BC', 'DRK', 'VRC', 'ZET', 'BTSX', 'NXT', 'XRP']
h = httplib2.Http(".cache") 
isAllOk = True
delayedCurrency = []

print "Begin Check Network"
print "---------------------------------------"

for c in currency:
    (resp_headers, content) = h.request("https://exchange.coinport.com/api/open/network/" + c, "GET")
    result = json.loads(content)
    if (result['success'] == False):
        print "fetch network data failed, CURRENCY : " + c
        delayedCurrency.append(c)
        isAllOk = False
    if (result['success'] == True and result['data']['delay'] > MAX_DELAY):
        print "Last crypto-currency block was received more than 60 minutes ago, CURRENCY : " + c
        delayedCurrency.append(c)
        isAllOk = False
    else:
        print "Network " + c + " is Ok"

print "---------------------------------------"
if (isAllOk):
    print "All network is Ok"
    exit(0)
else:
    print "Network " + str(delayedCurrency) + " is not Ok now!!!"
    exit(2)

