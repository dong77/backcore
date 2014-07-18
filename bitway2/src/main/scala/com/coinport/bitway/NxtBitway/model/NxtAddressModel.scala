package com.coinport.bitway.NxtBitway.model

import com.coinport.coinex.data.CryptoCurrencyAddressType

case class NxtAddressModel(accountId: String,
                           accountRS: String,
                           secret: String,
                           publicKey: String,
                           addressType: CryptoCurrencyAddressType,
                           created: Long,
                           updated: Long)

