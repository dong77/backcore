/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.f1;

import static org.fusesource.leveldbjni.JniDBFactory.*;

import java.io.*;

import org.iq80.leveldb.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LevelDbHelper {
    private final static Logger logger = LoggerFactory.getLogger(LevelDbHelper.class);
    public static DB getOrCreateLevelDbInstance(String dbname) {
        try {
            Options options = new Options();
            options.createIfMissing(true);
            File dbdir = new File(dbname);
            File parent = dbdir.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            return factory.open(dbdir, options);
        } catch (Exception e) {
            logger.error("leveldb error", e);
            return null;
        }
    }
}
