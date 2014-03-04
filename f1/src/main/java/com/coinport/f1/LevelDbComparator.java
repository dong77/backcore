/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.f1;

import org.iq80.leveldb.DBComparator;
import com.google.common.primitives.Longs;

public class LevelDbComparator implements DBComparator {
    @Override
    public int compare(byte[] key1, byte[] key2) {
        return (int)(Longs.fromByteArray(key1) - Longs.fromByteArray(key2));
    }

    @Override
    public String name() {
        return "levelDbComparator";
    }

    @Override
    public byte[] findShortestSeparator(byte[] start, byte[] limit) {
        return start;
    }

    @Override
    public byte[] findShortSuccessor(byte[] key) {
        return key;
    }
}
