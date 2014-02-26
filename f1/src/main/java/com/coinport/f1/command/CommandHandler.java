/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.f1.command;

import com.coinport.f1.BPCommand;
import com.coinport.f1.BusinessContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class CommandHandler {
    protected final static Logger logger = LoggerFactory.getLogger(CommandHandler.class);
    public abstract boolean exec(final BPCommand command, BusinessContext bc);
}
