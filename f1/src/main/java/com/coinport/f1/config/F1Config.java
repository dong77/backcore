/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.f1.config;

import com.coinport.common.TConfig;

public class F1Config extends TConfig {
    // The size of the ring buffer. Must be 2^x and better meet the size of CPU's L3 cache.
    // Tips: lscpu to show the L3 cache size in Ubuntu.
    public int bufferSize = 1 << 20;
}
