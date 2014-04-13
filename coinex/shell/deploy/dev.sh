#!/bin/sh
#
# Copyright 2014 Coinport Inc. All Rights Reserved.
# Author: c@coinport.com (Chao Ma)

# =========================== The parameters for JVM ===========================
MaxHeapSizeM=4096  # The max heap size
MaxPermSizeM=512  # The permanent size
CMSRatio=70
InitHeapSizeRatio=4  # the max heap size / the init heap size
NewRatioA=3  # all heap space size / new heap space size
XmsSizeM=`expr $MaxHeapSizeM / $InitHeapSizeRatio`
NewSizeM=`expr $XmsSizeM / $NewRatioA`
MaxNewSizeM=`expr $MaxHeapSizeM / $NewRatioA`
NumOfFullGCBeforeCompaction=1

Xms="-Xms${XmsSizeM}m"  # The init heap size
Xmx="-Xmx${MaxHeapSizeM}m"

NewSize="-XX:NewSize=${NewSizeM}m"  # The init size of new heap space
MaxNewSize="-XX:MaxNewSize=${MaxNewSizeM}m"  # The max size of new heap space

PermSize="-XX:PermSize=${MaxPermSizeM}m"
MaxPermSize="-XX:MaxPermSize=${MaxPermSizeM}m"

# If full GC use CMS, this is the default new GC. Also explicit lists here
UseParNewGC="-XX:+UseParNewGC"
UseConcMarkSweepGc="-XX:+UseConcMarkSweepGC"  # Use CMS as full GC
CMSInitOccupancyFraction="-XX:CMSInitiatingOccupancyFraction=${CMSRatio}"
CMSFullGCsBeforeCompaction="-XX:CMSFullGCsBeforeCompaction=${NumOfFullGCBeforeCompaction}"

# GCLog="-Xloggc:./gc.log"
# GCStopTime="-XX:+PrintGCApplicationStoppedTime"
# GCTimeStamps="-XX:+PrintGCTimeStamps"
# GCDetails="-verbose:gc -XX:+PrintGCDetails -XX:+PrintGCDateStamps"
# ======================== End the parameters for JVM ==========================

COMMAND="java -server $Xms $Xmx $NewSize $MaxNewSize $PermSize $MaxPermSize $UseParNewGC $UseConcMarkSweepGc $CMSInitOccupancyFraction $GCLog $GCStopTime $GCTimeStamps $GCDetails $CMSFullGCsBeforeCompaction -cp ./coinex-backend-assembly-1.0.7-SNAPSHOT.jar com.coinport.coinex.CoinexApp 25551 127.0.0.1:25551 all 127.0.0.1"

echo $COMMAND
nohup $COMMAND &
