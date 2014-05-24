/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.bitway

import org.specs2.mutable._
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.Set

import com.coinport.coinex.data._
import Implicits._
import Currency._

class BitwayManagerSpec extends Specification {
  import CryptoCurrencyAddressType._
  import TransferType._
  import TransferStatus._

  "BitwayManager" should {
    "address accocate test" in {
      val bwm = new BitwayManager(Btc, 10)

      bwm.getSupportedCurrency mustEqual Btc

      bwm.isDryUp mustEqual true
      bwm.faucetAddress(Unused, Set("d1", "d2", "d3", "d4", "d5", "d6"))
      bwm.isDryUp mustEqual false

      val d1 = bwm.allocateAddress
      val d1_ = bwm.allocateAddress
      d1 mustEqual d1_
      d1._2 mustEqual false

      bwm.addressAllocated(1, d1._1.get)
      val d2 = bwm.allocateAddress
      d1 mustNotEqual d2
      d2._2 mustEqual false
      bwm.addressAllocated(1, d2._1.get)
      val d3 = bwm.allocateAddress
      d3._2 mustEqual false
      bwm.addressAllocated(1, d3._1.get)
      val d4 = bwm.allocateAddress
      d4._2 mustEqual true
      bwm.addressAllocated(1, d4._1.get)
      val d5 = bwm.allocateAddress
      d5._1 mustNotEqual None
      d5._2 mustEqual true
      bwm.addressAllocated(1, d5._1.get)
      val d6 = bwm.allocateAddress
      d6._1 mustNotEqual None
      d6._2 mustEqual true
      bwm.addressAllocated(1, d6._1.get)
      val none = bwm.allocateAddress
      none mustEqual (None, true)
      val noneAgain = bwm.allocateAddress
      noneAgain mustEqual (None, true)
    }

    "get tx type test" in {
      val bwm = new BitwayManager(Btc, 10)
      bwm.faucetAddress(UserUsed, Set("u1", "u2", "u3", "u4", "u5", "u6"))
      bwm.faucetAddress(Hot, Set("h1", "h2"))
      bwm.faucetAddress(Cold, Set("c1"))

      bwm.getTransferType(Set("d1"), Set("d2")) mustEqual None
      bwm.getTransferType(Set("u1", "d1"), Set("h1", "u1")) mustEqual Some(UserToHot)
      bwm.getTransferType(Set("u1", "d1"), Set("h1")) mustEqual Some(UserToHot)
      bwm.getTransferType(Set("h1"), Set("c1", "h1")) mustEqual Some(HotToCold)
      bwm.getTransferType(Set("c1"), Set("h1", "c1")) mustEqual Some(ColdToHot)
      bwm.getTransferType(Set("d1"), Set("u1", "d1")) mustEqual Some(Deposit)
      bwm.getTransferType(Set("h1"), Set("d1", "h1")) mustEqual Some(Withdrawal)
      bwm.getTransferType(Set("u1"), Set("d1")) mustEqual Some(Unknown)
    }

    "block chain test" in {
      val bwm = new BitwayManager(Btc, 10)
      bwm.getBlockIndexes mustEqual Some(ArrayBuffer.empty[BlockIndex])
      bwm.getCurrentBlockIndex mustEqual None
      bwm.appendBlockChain(List(
        BlockIndex(Some("b1"), Some(1)),
        BlockIndex(Some("b2"), Some(2)),
        BlockIndex(Some("b3"), Some(3)),
        BlockIndex(Some("b4"), Some(4)),
        BlockIndex(Some("b5"), Some(5)),
        BlockIndex(Some("b6"), Some(6))
      ), None)
      bwm.getBlockContinuity(CryptoCurrencyBlocksMessage(None, List(
        CryptoCurrencyBlock(BlockIndex(Some("b1"), Some(1)), BlockIndex(None, None), Nil),
        CryptoCurrencyBlock(BlockIndex(Some("b2"), Some(2)), BlockIndex(Some("b1"), Some(1)), Nil),
        CryptoCurrencyBlock(BlockIndex(Some("b3"), Some(3)), BlockIndex(Some("b2"), Some(2)), Nil)
      ))) mustEqual BlockContinuityEnum.DUP

      bwm.getBlockContinuity(CryptoCurrencyBlocksMessage(None, List(
        CryptoCurrencyBlock(BlockIndex(Some("b7"), Some(7)), BlockIndex(Some("b6"), Some(6)), Nil),
        CryptoCurrencyBlock(BlockIndex(Some("b8"), Some(8)), BlockIndex(Some("b7"), Some(7)), Nil),
        CryptoCurrencyBlock(BlockIndex(Some("b9"), Some(9)), BlockIndex(Some("b8"), Some(8)), Nil)
      ))) mustEqual BlockContinuityEnum.SUCCESSOR

      bwm.getBlockContinuity(CryptoCurrencyBlocksMessage(None, List(
        CryptoCurrencyBlock(BlockIndex(Some("b8"), Some(8)), BlockIndex(Some("b7"), Some(7)), Nil),
        CryptoCurrencyBlock(BlockIndex(Some("b9"), Some(9)), BlockIndex(Some("b8"), Some(8)), Nil)
      ))) mustEqual BlockContinuityEnum.GAP

      bwm.getBlockContinuity(CryptoCurrencyBlocksMessage(Some(BlockIndex(Some("b6"), Some(6))), List(
        CryptoCurrencyBlock(BlockIndex(Some("b7"), Some(7)), BlockIndex(Some("b6"), Some(6)), Nil),
        CryptoCurrencyBlock(BlockIndex(Some("b8"), Some(8)), BlockIndex(Some("b7"), Some(7)), Nil),
        CryptoCurrencyBlock(BlockIndex(Some("b9"), Some(9)), BlockIndex(Some("b8"), Some(8)), Nil)
      ))) mustEqual BlockContinuityEnum.SUCCESSOR

      bwm.getBlockContinuity(CryptoCurrencyBlocksMessage(Some(BlockIndex(Some("b2"), Some(2))), List(
        CryptoCurrencyBlock(BlockIndex(Some("b8"), Some(8)), BlockIndex(Some("b7p"), Some(7)), Nil),
        CryptoCurrencyBlock(BlockIndex(Some("b9"), Some(9)), BlockIndex(Some("b8"), Some(8)), Nil)
      ))) mustEqual BlockContinuityEnum.REORG

      bwm.getBlockContinuity(CryptoCurrencyBlocksMessage(Some(BlockIndex(None, None)), List(
        CryptoCurrencyBlock(BlockIndex(Some("b8"), Some(8)), BlockIndex(Some("b7"), Some(7)), Nil),
        CryptoCurrencyBlock(BlockIndex(Some("b9"), Some(9)), BlockIndex(Some("b8"), Some(8)), Nil)
      ))) mustEqual BlockContinuityEnum.OTHER_BRANCH

      bwm.appendBlockChain(List(
        BlockIndex(Some("b7"), Some(7)),
        BlockIndex(Some("b8"), Some(8))
      ), None)

      bwm.getBlockIndexes mustEqual Some(ArrayBuffer(BlockIndex(Some("b1"), Some(1)), BlockIndex(Some("b2"), Some(2)), BlockIndex(Some("b3"), Some(3)), BlockIndex(Some("b4"), Some(4)), BlockIndex(Some("b5"), Some(5)), BlockIndex(Some("b6"), Some(6)), BlockIndex(Some("b7"), Some(7)), BlockIndex(Some("b8"), Some(8))))

      bwm.appendBlockChain(List(
        BlockIndex(Some("b8"), Some(8)),
        BlockIndex(Some("b9"), Some(9))
      ), None)
      bwm.getBlockIndexes mustEqual Some(ArrayBuffer(BlockIndex(Some("b1"), Some(1)), BlockIndex(Some("b2"), Some(2)), BlockIndex(Some("b3"), Some(3)), BlockIndex(Some("b4"), Some(4)), BlockIndex(Some("b5"), Some(5)), BlockIndex(Some("b6"), Some(6)), BlockIndex(Some("b7"), Some(7)), BlockIndex(Some("b8"), Some(8)), BlockIndex(Some("b8"), Some(8)), BlockIndex(Some("b9"), Some(9))))

      bwm.appendBlockChain(List(
        BlockIndex(Some("b10"), Some(10)),
        BlockIndex(Some("b11"), Some(11))
      ), Some(BlockIndex(Some("b9"), Some(9))))

      bwm.getBlockIndexes mustEqual Some(ArrayBuffer(BlockIndex(Some("b3"), Some(3)), BlockIndex(Some("b4"), Some(4)), BlockIndex(Some("b5"), Some(5)), BlockIndex(Some("b6"), Some(6)), BlockIndex(Some("b7"), Some(7)), BlockIndex(Some("b8"), Some(8)), BlockIndex(Some("b8"), Some(8)), BlockIndex(Some("b9"), Some(9)), BlockIndex(Some("b10"), Some(10)), BlockIndex(Some("b11"), Some(11))))

      bwm.appendBlockChain(List(
        BlockIndex(Some("b10"), Some(10)),
        BlockIndex(Some("b11"), Some(11))
      ), Some(BlockIndex(Some("b3"), Some(3))))

      bwm.getBlockIndexes mustEqual Some(ArrayBuffer(BlockIndex(Some("b3"), Some(3)), BlockIndex(Some("b10"), Some(10)), BlockIndex(Some("b11"), Some(11))))

      bwm.appendBlockChain(List(
        BlockIndex(Some("b10"), Some(10)),
        BlockIndex(Some("b11"), Some(11))
      ), Some(BlockIndex(None, None)))
      bwm.getBlockIndexes mustEqual Some(ArrayBuffer(BlockIndex(Some("b3"), Some(3)), BlockIndex(Some("b10"), Some(10)), BlockIndex(Some("b11"), Some(11)), BlockIndex(Some("b10"), Some(10)), BlockIndex(Some("b11"), Some(11))))
    }

    "tx generation test" in {
      val bwm = new BitwayManager(Btc, 10)
      bwm.faucetAddress(Unused, Set("u7"))
      bwm.faucetAddress(UserUsed, Set("u1", "u2", "u3", "u4", "u5", "u6"))
      bwm.faucetAddress(Hot, Set("h1", "h2", "h3"))
      bwm.faucetAddress(Cold, Set("c1"))

      bwm.addressAllocated(1, "u7")

      val bi1 = BlockIndex(Some("b1"), Some(1))
      val rawTx = CryptoCurrencyTransaction(
        txid = Some("t1"),
        inputs = Some(List(CryptoCurrencyTransactionPort("u7", Some(1.1)))),
        outputs = Some(List(CryptoCurrencyTransactionPort("h1", Some(0.9)))),
        includedBlock = Some(bi1), status = Confirming)
      bwm.completeCryptoCurrencyTransaction(rawTx, None, None) mustEqual Some(CryptoCurrencyTransaction(None, Some("t1"), None, Some(List(CryptoCurrencyTransactionPort("u7", Some(1.1), Some(1100), Some(1)))), Some(List(CryptoCurrencyTransactionPort("h1", Some(0.9), Some(900), Some(-1)))), None, None, Some(UserToHot), Confirming))

      val infos = Seq(
        CryptoCurrencyTransferInfo(1, Some("i1"), Some(1000)),
        CryptoCurrencyTransferInfo(2, Some("i2"), Some(80)))
      bwm.completeTransferInfos(infos) mustEqual (List(CryptoCurrencyTransferInfo(1, Some("i1"), Some(1000), Some(1.0), None), CryptoCurrencyTransferInfo(2, Some("i2"), Some(80), Some(0.08), None)), false)

      val tx1 = CryptoCurrencyTransaction(
        txid = Some("t1"),
        inputs = Some(List(CryptoCurrencyTransactionPort("h1", Some(1.1)))),
        outputs = Some(List(CryptoCurrencyTransactionPort("d1", Some(0.9)))),
        includedBlock = Some(bi1), status = Confirming)
      val tx2 = CryptoCurrencyTransaction(
        txid = Some("t2"),
        inputs = Some(List(CryptoCurrencyTransactionPort("h2", Some(2.1)))),
        outputs = Some(List(CryptoCurrencyTransactionPort("d2", Some(2.9)))),
        includedBlock = Some(bi1), status = Confirming)
      val tx3 = CryptoCurrencyTransaction(
        txid = Some("t3"),
        inputs = Some(List(CryptoCurrencyTransactionPort("h3", Some(3.1)))),
        outputs = Some(List(CryptoCurrencyTransactionPort("d3", Some(3.9)))),
        includedBlock = Some(bi1), status = Confirming)
      val tx4 = CryptoCurrencyTransaction(
        txid = Some("t4"),
        inputs = Some(List(CryptoCurrencyTransactionPort("h4", Some(4.1)))),
        outputs = Some(List(CryptoCurrencyTransactionPort("d4", Some(4.9)))),
        includedBlock = Some(bi1), status = Confirming)
      val blocks = List(
        CryptoCurrencyBlock(BlockIndex(Some("b10"), Some(10)), BlockIndex(Some("b9"), Some(9)), List(
          tx1, tx2)),
        CryptoCurrencyBlock(BlockIndex(Some("b11"), Some(11)), BlockIndex(Some("b10"), Some(10)), List(
          tx3, tx4))
      )

      bwm.extractTxsFromBlocks(blocks) mustEqual List(
        CryptoCurrencyTransaction(None, Some("t1"), None,
          Some(List(CryptoCurrencyTransactionPort("h1", Some(1.1), Some(1100), Some(-1)))),
          Some(List(CryptoCurrencyTransactionPort("d1", Some(0.9), Some(900)))),
          Some(BlockIndex(Some("b9"), Some(9))),
          Some(BlockIndex(Some("b10"), Some(10))), Some(Withdrawal), Confirming),
        CryptoCurrencyTransaction(None, Some("t2"), None,
          Some(List(CryptoCurrencyTransactionPort("h2", Some(2.1), Some(2100), Some(-1)))),
          Some(List(CryptoCurrencyTransactionPort("d2", Some(2.9), Some(2900)))),
          Some(BlockIndex(Some("b9"), Some(9))),
          Some(BlockIndex(Some("b10"), Some(10))), Some(Withdrawal), Confirming),
        CryptoCurrencyTransaction(None, Some("t3"), None,
          Some(List(CryptoCurrencyTransactionPort("h3", Some(3.1), Some(3100), Some(-1)))),
          Some(List(CryptoCurrencyTransactionPort("d3", Some(3.9), Some(3900)))),
          Some(BlockIndex(Some("b10"), Some(10))), Some(BlockIndex(Some("b11"), Some(11))),
          Some(Withdrawal), Confirming))
    }

    "getAddressStatus/getNetworkStatus test" in {
      val bwm = new BitwayManager(Btc, 10)
      bwm.faucetAddress(UserUsed, Set("u1", "u2", "u3", "u4", "u5", "u6"))
      bwm.faucetAddress(Hot, Set("h1", "h2"))
      bwm.faucetAddress(Cold, Set("c1"))

      val bi1 = BlockIndex(Some("b1"), Some(1))
      val bi2 = BlockIndex(Some("b2"), Some(2))

      bwm.updateAddressStatus(Seq(
        CryptoCurrencyTransaction(
          txid = Some("t1"),
          inputs = Some(List(CryptoCurrencyTransactionPort("h1", Some(1.2)))),
          outputs = Some(List(CryptoCurrencyTransactionPort("u1", Some(1.0)),
            CryptoCurrencyTransactionPort("h1", Some(0.2))
          )),
          includedBlock = Some(bi1),
          status = Confirming
        )), bi1.height)
      bwm.updateAddressStatus(Seq(
        CryptoCurrencyTransaction(
          txid = Some("t2"),
          inputs = Some(List(CryptoCurrencyTransactionPort("h1", Some(0.2)), CryptoCurrencyTransactionPort("h2", Some(0.4)))),
          outputs = Some(List(CryptoCurrencyTransactionPort("u2", Some(0.2)), CryptoCurrencyTransactionPort("h1", Some(0.1)), CryptoCurrencyTransactionPort("h2", Some(0.1)))),
          includedBlock = Some(bi2),
          status = Confirming
        )), bi2.height)
      bwm.appendBlockChain(List(BlockIndex(Some("b1"), Some(1)), BlockIndex(Some("b2"), Some(2)),
        BlockIndex(Some("b3"), Some(3))))

      bwm.updateLastAlive(1234L)
      bwm.getNetworkStatus mustEqual CryptoCurrencyNetworkStatus(Some("b3"), Some(3L), Some(1234L))

      bwm.getAddressStatus(Hot) mustEqual Map("h2" -> AddressStatusResult(Some("t2"), Some(2), -300), "h1" -> AddressStatusResult(Some("t2"), Some(2), -1100))
      bwm.getAddressStatus(UserUsed) mustEqual Map("u2" -> AddressStatusResult(Some("t2"), Some(2), 200), "u1" -> AddressStatusResult(Some("t1"), Some(1), 1000))
      bwm.getAddressStatus(Cold) mustEqual Map.empty[String, BlockIndex]

      bwm.updateBlocks(Some(BlockIndex(Some("b1"), Some(1))), Seq(
        CryptoCurrencyBlock(
          index = BlockIndex(Some("b2"), Some(2)),
          prevIndex = BlockIndex(Some("b1"), Some(1)),
          txs = List(
            CryptoCurrencyTransaction(
              txid = Some("t2"),
              inputs = Some(List(CryptoCurrencyTransactionPort("h1", Some(0.2)), CryptoCurrencyTransactionPort("h1", Some(0.2)))),
              outputs = Some(List(CryptoCurrencyTransactionPort("u2", Some(0.2)), CryptoCurrencyTransactionPort("u2", Some(0.2)))),
              status = Confirming
            ))
        )
      ))

      bwm.getAddressStatus(Hot) mustEqual Map("h2" -> AddressStatusResult(Some("t2"), Some(2), 0), "h1" -> AddressStatusResult(Some("t2"), Some(2), -1400))
      bwm.getAddressStatus(UserUsed) mustEqual Map("u2" -> AddressStatusResult(Some("t2"), Some(2), 400), "u1" -> AddressStatusResult(Some("t1"), Some(1), 1000))
      bwm.getAddressStatus(Cold) mustEqual Map.empty[String, BlockIndex]
    }
  }
}
