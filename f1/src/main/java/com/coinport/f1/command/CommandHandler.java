/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.f1.command;

import com.coinport.f1.BPCommand;
import com.coinport.f1.BusinessContext;

public abstract class CommandHandler {
    public abstract void exec(final BPCommand command, BusinessContext bc);
}
