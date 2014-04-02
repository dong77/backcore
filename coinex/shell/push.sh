#!/bin/sh
#
# Copyright 2014 Coinport Inc. All Rights Reserved.
# Author: c@coinport.com (Chao Ma)
echo "git fetch" && \
git fetch && \
echo "git rebase origin/master" && \
git rebase origin/master && \
echo "git push origin HEAD:master" && \
git push origin HEAD:master
