/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.common;

import java.util.Map;

public class SampleConfig extends TConfig {

    public class Student {
        public String name;
        public int age;
    }

    public String ip;
    public int port = 100;
    public Student[] students;
    public Map<String, String> mentors;
}
