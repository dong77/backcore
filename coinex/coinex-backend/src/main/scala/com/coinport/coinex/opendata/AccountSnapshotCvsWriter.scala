package com.coinport.coinex.opendata

import akka.persistence.serialization.Snapshot
import com.coinport.coinex.api.model.CurrencyWrapper
import com.coinport.coinex.data._
import java.io.{ OutputStreamWriter, BufferedWriter }
import org.apache.hadoop.fs.{ Path, FileSystem }
import scala.collection.Map
import scala.collection.mutable.ListBuffer
import scala.util.Sorting

object AccountSnapshotCvsWriter extends SnapshotWriter {

  override def writeSnapshot(processorId: String, seqNum: Long, snapshot: Snapshot)(implicit config: OpenDataConfig, pFileMap: collection.mutable.Map[String, String], fs: FileSystem) {
    val accountMap: Map[Long, com.coinport.coinex.data.UserAccount] = snapshot.data.asInstanceOf[TAccountState].userAccountsMap
    val currencyAccountsMap = collection.mutable.Map.empty[Currency, ListBuffer[(Long, CashAccount)]]
    accountMap.values.filter(_.userId >= 1000000000L).foreach {
      userAccount =>
        userAccount.cashAccounts.keys foreach {
          currency =>
            if (!currencyAccountsMap.contains(currency)) {
              currencyAccountsMap.put(currency, ListBuffer.empty[(Long, CashAccount)])
            }
            currencyAccountsMap(currency).append((userAccount.userId, userAccount.cashAccounts(currency)))
        }
    }

    currencyAccountsMap.keys foreach {
      currency =>
        val accountSeq = currencyAccountsMap(currency).map(tp => CashAccountWrapper(tp._1, tp._2)).toArray
        Sorting.quickSort(accountSeq)
        val accounts = ListBuffer.empty[(Long, CashAccount)]
        accountSeq.foreach(
          account =>
            accounts.append((account.userId, account.cashAccount))
        )
        currencyAccountsMap.put(currency, accounts)
    }

    currencyAccountsMap.keys.foreach {
      cy =>
        writeAsset(cy, currencyAccountsMap(cy).toList)
    }

  }

  private def writeAsset(currency: Currency, accounts: List[(Long, CashAccount)])(implicit config: OpenDataConfig, pFileMap: collection.mutable.Map[String, String], fs: FileSystem) {
    val writer = new BufferedWriter(new OutputStreamWriter(fs.create(
      new Path(s"${config.csvAssetDir}/${currency.toString.toLowerCase()}/", s"${currency.toString}_balance_${currentTime()}.csv".toLowerCase))))
    writer.write(s""""User Id",Amount\n""")
    var sum = 0L
    for (act <- accounts) {
      val total = act._2.available + act._2.locked + act._2.pendingWithdrawal
      sum += total
      writer.write(s"${String.valueOf(act._1)},${String.valueOf(new CurrencyWrapper(total).externalValue(currency))}\n")
    }
    writer.write(s"Sum,${String.valueOf(new CurrencyWrapper(sum).externalValue(currency))}\n")
    writer.flush()
    writer.close()
  }

  case class CashAccountWrapper(userId: Long, cashAccount: CashAccount) extends Ordered[CashAccountWrapper] {
    def compare(that: CashAccountWrapper) = {
      if (userId == that.userId) 0 else if (userId < that.userId) -1 else 1
    }
  }

}
