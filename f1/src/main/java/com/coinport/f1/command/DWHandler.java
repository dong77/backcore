/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.f1.command;

import com.coinport.f1.BPCommand;
import com.coinport.f1.CoinType;
import com.coinport.f1.DOW;
import com.coinport.f1.DWInfo;

import com.coinport.f1.Trader;

public class DWHandler extends CommandHandler {
    @Override
    public boolean exec(final BPCommand command, Trader trader) {
        if (command.isSetDwInfo()) {
            DWInfo dwi = command.getDwInfo();
            long uid = dwi.getUid();
            long amount = dwi.getAmount();
            CoinType coinType = dwi.getCoinType();
            switch (dwi.getDwtype())  {
                case DEPOSIT:
                    return trader.deposit(uid, coinType, amount, true);
                case WITHDRAWAL:
                    return trader.withdrawal(uid, coinType, amount, true);
                default:
                    return false;
            }
        } else {
            logger.error("no dwinfo found in dw command");
            return false;
        }
    }
}
