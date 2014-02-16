/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.common;

import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.Test;

@RunWith(JUnit4.class)
public class ConfigTest {

    @Test
    public void testNormalConfig() {
        String configFilePath = System.getProperty("user.dir") + "/" +
            "src/test/java/com/coinport/common/config/sample_config";
        SampleConfig config = ConfigFactory.getConfig(SampleConfig.class, configFilePath);

        assertEquals("test", config.serviceName);

        assertEquals("192.168.1.1", config.ip);
        assertEquals(100, config.port);
        assertEquals(2, config.students.length);
        assertEquals("Lucy", config.students[0].name);
        assertEquals(21, config.students[0].age);
        assertEquals("Hanmeimei", config.students[1].name);
        assertEquals(30, config.students[1].age);
        assertEquals("tBob", config.mentors.get("bob"));
        assertEquals("tLucy", config.mentors.get("lucy"));
    }

    @Test
    public void testConfigFromResources() {
        SampleConfig config = ConfigFactory.getConfigFromResource(SampleConfig.class, "sample_config");

        assertEquals("test", config.serviceName);

        assertEquals("192.168.1.1", config.ip);
        assertEquals(100, config.port);
        assertEquals(2, config.students.length);
        assertEquals("Lucy", config.students[0].name);
        assertEquals(21, config.students[0].age);
        assertEquals("Hanmeimei", config.students[1].name);
        assertEquals(30, config.students[1].age);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBadConfigFilePath() {
        String configFilePath = System.getProperty("user.dir") + "/" +
            "src/test/java/com/coinport/common/config/no_this_file";
        SampleConfig config = ConfigFactory.getConfig(SampleConfig.class, configFilePath);
    }

    @Test(expected = NullPointerException.class)
    public void testBadConfigFilePathFromResources() {
        SampleConfig config = ConfigFactory.getConfigFromResource(SampleConfig.class, "no_this_file");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBadJson() {
        String configFilePath = System.getProperty("user.dir") + "/" +
            "src/test/java/com/coinport/common/config/bad_config";
        SampleConfig config = ConfigFactory.getConfig(SampleConfig.class, configFilePath);
    }
}
