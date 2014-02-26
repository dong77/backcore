/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.f1.command;

import com.coinport.f1.BPCommand;

import com.coinport.f1.BusinessContext;

public class RegisterUserHandler extends CommandHandler {
    @Override
    public boolean exec(final BPCommand command, BusinessContext bc) {
        if (command.isSetUserInfo()) {
            return bc.register(command.getUserInfo());
        } else {
            logger.error("no userinfo found in register command");
            return false;
        }
    }
}
