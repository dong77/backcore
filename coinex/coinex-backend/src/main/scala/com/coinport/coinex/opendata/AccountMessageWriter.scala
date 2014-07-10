package com.coinport.coinex.opendata

import com.coinport.coinex.api.model.CurrencyWrapper
import com.coinport.coinex.data._
import java.io.{ OutputStreamWriter, BufferedWriter }
import org.apache.hadoop.fs.{ Path, FileSystem }
import scala.collection.mutable.ListBuffer

object AccountMessageWriter extends MessageWriter {

  def writeMessages(processorId: String, lastSeqNum: Long, messages: List[(Long, Any)])(implicit config: OpenDataConfig, pFileMap: collection.mutable.Map[String, String], fs: FileSystem) {
    val currencyTransferMap = collection.mutable.Map.empty[Currency, ListBuffer[AccountTransfer]]
    messages.foreach {
      info =>
        info._2 match {
          case adm: AdminConfirmTransferSuccess =>
            appendTransfer(adm.transfer, currencyTransferMap)
          case cs: CryptoTransferSucceeded =>
            cs.transfers.foreach(appendTransfer(_, currencyTransferMap))
          case cr: CryptoTransferResult =>
            cr.multiTransfers.values.foreach {
              tmf =>
                tmf.transfers.filter {
                  tf =>
                    tf.status == TransferStatus.Succeeded && (tf.`type` == TransferType.Deposit || tf.`type` == TransferType.Withdrawal)
                } foreach {
                  appendTransfer(_, currencyTransferMap)
                }
            }
        }
    }
    if (!currencyTransferMap.isEmpty) {
      currencyTransferMap.keys foreach {
        cy =>
          writeTransfers(processorId, cy, currencyTransferMap(cy).toList)
      }
    }
  }

  private def appendTransfer(transfer: AccountTransfer, transferMap: collection.mutable.Map[Currency, ListBuffer[AccountTransfer]]) {
    transferMap.getOrElse(transfer.currency, ListBuffer.empty[AccountTransfer]).append(transfer)
  }

  private def writeTransfers(processorId: String, currency: Currency, transfers: List[AccountTransfer])(implicit config: OpenDataConfig, pFileMap: collection.mutable.Map[String, String], fs: FileSystem) {
    val writer = new BufferedWriter(new OutputStreamWriter(fs.create(
      new Path(config.csvDwDir + "/" + currency.toString, s"coinport_${currency}_${pFileMap(processorId)}_${currentTime()}.cvs".toLowerCase))))
    writer.write(s""""User Id","Transaction Type",Amount,Time\n""")
    val formatter = new java.text.SimpleDateFormat("yyyy-MM-dd hh:mm:ss")
    for (tf <- transfers) {
      writer.write(s"${String.valueOf(tf.userId)},${tf.`type`.toString},${String.valueOf(new CurrencyWrapper(tf.amount).externalValue(currency))},${formatter.format(new java.util.Date(tf.updated.get))}\n")
    }
    writer.flush()
    writer.close()
  }
}
