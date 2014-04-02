#!/bin/sh
#
# Copyright 2014 Coinport Inc. All Rights Reserved.
# Author: c@coinport.com (Dong)
git ls-files | while read f; do git blame -e $f; done | awk -F '<|>' '{print $2}' | sort | uniq -c