/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 *
 * NOTE: This class is thread unsafe.
 */

package com.coinport.common;

import java.io.InputStream;
import java.io.FileInputStream;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigFactory {

    private static Logger logger = LoggerFactory.getLogger(ConfigFactory.class);
    private static String logDelimiter = "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~";

    public static <T> T getConfig(Class<T> clazz, String configFile) {
        logger.info(logDelimiter);
        logger.info("load config from config file: {}", configFile);
        FileInputStream configInput = null;
        try {
            configInput = new FileInputStream(configFile);
            return getConfigFromStream(clazz, configInput);
        } catch (Exception e) {
            throw new IllegalArgumentException("failed to load config with file: " + configFile, e);
        } finally {
            IOUtils.closeQuietly(configInput);
        }
    }

    public static <T> T getConfigFromResource(Class<T> clazz, String configFile) {
        logger.info(logDelimiter);
        String realPath = ConfigFactory.class.getClassLoader().getResource(configFile).getPath();
        logger.info("load config from file: {}", realPath);
        try (InputStream in = ConfigFactory.class.getClassLoader().getResourceAsStream(configFile)) {
            return getConfigFromStream(clazz, in);
        } catch (Exception e) {
            throw new IllegalArgumentException("failed to load config with file: " + configFile, e);
        }
    }

    private static <T> T getConfigFromStream(Class<T> clazz, InputStream configInput) throws Exception {
        Gson gson = new Gson();
        try {
            String configStr = IOUtils.toString(configInput);
            logger.info("config file content: \n" + configStr);
            logger.info(logDelimiter);
            return gson.fromJson(configStr, clazz);
        } catch (JsonSyntaxException e) {
            logger.error("bad json format", e);
            throw e;
        } catch (Exception e) {
            logger.error("exception occur when parse config file", e);
            throw e;
        }
    }
}
