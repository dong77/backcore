/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.f1;

import java.util.HashMap;
import java.util.Map;

public class BusinessContext {
    // TODO(c): change this
    private long nextUserId = 0;

    private Map<Long, UserInfo> users;

    public BusinessContext() {
        users = new HashMap<Long, UserInfo>();
    }

    public void register(UserInfo ui) {
        long id = nextUserId++;
        ui.setId(id);
        users.put(id, ui);
    }
}
