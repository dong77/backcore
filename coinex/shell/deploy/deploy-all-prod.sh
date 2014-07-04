#!/bin/sh
#
# Copyright 2014 Coinport Inc. All Rights Reserved.
# Author: xiaolu@coinport.com (Wu Xiaolu)

#-------------------------------------------------------------------
# 0. backup jar and zip file
day=`date +%Y%m%d`
cd ~/work/backcore/coinex/shell/deploy/
ssh -i ~/work/xiaolu.pem ubuntu@54.238.180.101 "/var/coinport/code/backcore/coinex/shell/deploy/backup.sh"
ssh -i ~/work/xiaolu.pem ubuntu@54.199.252.18 "/var/coinport/code/frontend/exchange/shell/backup.sh"
ssh -i ~/work/xiaolu.pem ubuntu@54.199.252.18 "/var/coinport/code/admin/shell/backup.sh"

#-------------------------------------------------------------------
# 1. push new branch to remote origin
# push backend branch
./pushReleaseBranch.sh $1
branch=`git branch | grep "*" | awk '{print $2}'`
echo "newest backcore branch is "$branch
# push frontend branch
cd ../../../../frontend/
../backcore/coinex/shell/deploy/pushReleaseBranch.sh $1
branch=`git branch | grep "*" | awk '{print $2}'`
echo "newest frontend branch is "$branch
# push admin branch
cd ../admin/
../backcore/coinex/shell/deploy/pushReleaseBranch.sh $1
branch=`git branch | grep "*" | awk '{print $2}'`
echo "newest admin branch is "$branch

#-------------------------------------------------------------------
# 2. generate new tar and zip package for backend & frontend & admin

# generate coinex-backend tar package
ssh -i ~/work/xiaolu.pem ubuntu@54.238.180.101 "/var/coinport/code/backcore/coinex/shell/deploy/generateJar.sh $branch"

# generate frontend zip package
ssh -i ~/work/xiaolu.pem ubuntu@54.199.252.18 "/var/coinport/code/frontend/exchange/shell/prod_generateZip.sh $branch"

# generate admin zip package
ssh -i ~/work/xiaolu.pem ubuntu@54.199.252.18 "/var/coinport/code/admin/shell/prod_generateZip.sh $branch"

#-------------------------------------------------------------------
# 3. restart service

# restart coinex-backend

ssh -i ~/work/xiaolu.pem ubuntu@54.238.180.101 "/var/coinport/code/backcore/coinex/shell/deploy/stop.sh"
ssh -i ~/work/xiaolu.pem ubuntu@54.238.180.101 'cd /var/coinport/backend && /var/coinport/code/backcore/coinex/shell/deploy/start-coinex-backend.sh'

# restart coinport-frontend
ssh -i ~/work/xiaolu.pem ubuntu@54.199.252.18 "/var/coinport/code/frontend/exchange/shell/stop.sh"
ssh -i ~/work/xiaolu.pem ubuntu@54.199.252.18 "/var/coinport/code/frontend/exchange/shell/prod_start.sh"

# restart coinport-frontend
ssh -i ~/work/xiaolu.pem ubuntu@54.199.252.18 "/var/coinport/code/admin/shell/stop.sh"
ssh -i ~/work/xiaolu.pem ubuntu@54.199.252.18 "/var/coinport/code/admin/shell/prod_start.sh"

#-------------------------------------------------------------------
# 4. confirm the deploy
../nagios/check_service.sh
