/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.f1.command;

import com.coinport.f1.BPCommand;
import com.coinport.f1.CoinType;
import com.coinport.f1.DOW;
import com.coinport.f1.DWInfo;

import com.coinport.f1.BusinessContext;

public class DWHandler extends CommandHandler {
    @Override
    public void exec(final BPCommand command, BusinessContext bc) {
        if (command.isSetDwInfo()) {
            DWInfo dwi = command.getDwInfo();
            long uid = dwi.getUid();
            long amount = dwi.getAmount();
            CoinType coinType = dwi.getCoinType();
            switch (dwi.getDwtype())  {
                case DEPOSIT:
                    bc.deposit(uid, coinType, amount, true);
                    break;
                case WITHDRAWAL:
                    bc.withdrawal(uid, coinType, amount, true);
                    break;
            }
        } else {
            logger.error("no dwinfo found in dw command");
        }
    }
}
