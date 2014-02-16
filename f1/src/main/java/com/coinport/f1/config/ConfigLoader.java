/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.f1.config;

import com.coinport.common.ConfigFactory;

public class ConfigLoader {
    private static F1Config config;

    static {
        config = ConfigFactory.getConfigFromResource(F1Config.class, "f1.json");
    }

    public static F1Config getConfig() {
        return config;
    }

    public static F1Config getConfig(String fileName) {
        return ConfigFactory.getConfigFromResource(F1Config.class, fileName);
    }

    public static <T> T getConfig(Class<T> clazz, String fileName) {
        return ConfigFactory.getConfigFromResource(clazz, fileName);
    }
}
