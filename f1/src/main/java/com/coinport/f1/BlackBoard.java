/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.f1;

import java.util.HashMap;
import java.util.Map;

public class BlackBoard {
    private final Map<CoinType, Map<CoinType, BlackBoardZone>> zones =
        new HashMap<CoinType, Map<CoinType, BlackBoardZone>>();
}
