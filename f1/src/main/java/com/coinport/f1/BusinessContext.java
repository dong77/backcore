/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.f1;

import java.util.HashMap;
import java.util.Map;

public class BusinessContext {
    private Map<Long, UserInfo> users;

    public BusinessContext() {
        users = new HashMap<Long, UserInfo>();
    }

    public void display() {
        for (Map.Entry<Long, UserInfo> entry : users.entrySet()) {
            System.out.println(entry.getKey() + "-->" + entry.getValue().toString());
        }
    }

    public void register(UserInfo ui) {
        users.put(ui.getId(), ui);
    }
}
