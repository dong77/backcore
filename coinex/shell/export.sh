#!/bin/sh
#
# Copyright 2014 Coinport Inc. All Rights Reserved.

echo ================= Coinport Data Exporter =================
echo export $1 events
# data path
path=/data/export
tempFile=/tmp/export_cmd.sh
touch $tempFile
chmod +x $tempFile

# query recent snapshot
snapshotFile=${path}/lastsnapshot_${1}
lastSnapshot=`cat $snapshotFile`

if [ ! -f "$snapshotFile" ]; then
    lastSnapshot=0
fi

echo from last snapshot $lastSnapshot
query="'{\"metadata.height\": {\$gt: $lastSnapshot}}'"
echo mongoexport -d coinex_events -c p_${1}_metadata -f metadata.height -q $query --csv -o /tmp/snapshot > $tempFile
$tempFile

# for each snapshot
for i in `sed -n '2,$p' /tmp/snapshot`; do
    snapshot=`expr $i - 1`
    echo current snapshot $i, prepare to export snapshot $snapshot ...
    file=${path}/coinport_${1}_snapshot_${snapshot}.json

    query="'{snapshot: $snapshot}'"

    if [ ! -f "$file" ]; then
           echo mongoexport -d coinex_events -c p_${1}_events -q $query --jsonArray -o $file > $tempFile
           $tempFile
           # remove empty files
           if [ ! -s "$file" ]; then
               rm $file
           fi
    fi
done
echo $snapshot > $snapshotFile
echo last snapshot is $snapshot

echo ==========================================================
