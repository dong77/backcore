package com.coinport.coinex.opendata

import akka.persistence.hbase.common.Columns._
import com.coinport.coinex.serializers.PrettyJsonSerializer
import com.coinport.coinex.data._
import java.io.{ OutputStreamWriter, BufferedWriter }
import org.apache.hadoop.fs.{ FileSystem, Path }
import org.apache.hadoop.hbase.util.Bytes

object MessageJsonWriter extends MessageWriter {

  override def writeMessages(processorId: String, lastSeqNum: Long, messages: List[(Long, Any)])(implicit config: OpenDataConfig, pFileMap: collection.mutable.Map[String, String], fs: FileSystem) {
    val writer = new BufferedWriter(new OutputStreamWriter(fs.create(
      new Path(config.jsonMessagesDir, s"coinport_events_${pFileMap(processorId)}_${String.valueOf(lastSeqNum).reverse.padTo(16, "0").reverse.mkString}_v1.json".toLowerCase))))
    writer.write(s"""{"timestamp": ${System.currentTimeMillis()},\n"events": [""")
    val data = getMessages(messages)
    writer.write(data.substring(0, data.length - 1))
    writer.write("]}")
    writer.flush()
    writer.close()
  }

  private def getMessages(messages: List[(Long, Any)]): String = {
    val builder = new StringBuilder()
    for ((seqNum, msg) <- messages) {
      val newMsg =
        if (msg.isInstanceOf[DoRequestTransfer]) {
          val doRequestTransfer = msg.asInstanceOf[DoRequestTransfer]
          doRequestTransfer.copy(transfer = emptyCnyAddress(doRequestTransfer.transfer))
        } else if (msg.isInstanceOf[RequestTransferSucceeded]) {
          val transferSucceeded = msg.asInstanceOf[RequestTransferSucceeded]
          transferSucceeded.copy(transfer = emptyCnyAddress(transferSucceeded.transfer))
        } else if (msg.isInstanceOf[DoCancelTransfer]) {
          val doCancelTransfer = msg.asInstanceOf[DoCancelTransfer]
          doCancelTransfer.copy(transfer = emptyCnyAddress(doCancelTransfer.transfer))
        } else if (msg.isInstanceOf[AdminConfirmTransferFailure]) {
          val adminConfirmTransferFailure = msg.asInstanceOf[AdminConfirmTransferFailure]
          adminConfirmTransferFailure.copy(transfer = emptyCnyAddress(adminConfirmTransferFailure.transfer))
        } else if (msg.isInstanceOf[AdminConfirmTransferSuccess]) {
          val adminConfirmTransferSuccess = msg.asInstanceOf[AdminConfirmTransferSuccess]
          adminConfirmTransferSuccess.copy(transfer = emptyCnyAddress(adminConfirmTransferSuccess.transfer))
        } else if (msg.isInstanceOf[AdminConfirmTransferProcessed]) {
          val adminConfirmTransferProcessed = msg.asInstanceOf[AdminConfirmTransferProcessed]
          adminConfirmTransferProcessed.copy(transfer = emptyCnyAddress(adminConfirmTransferProcessed.transfer))
        } else {
          msg
        }
      builder ++= s"""{"${msg.getClass.getEnclosingClass.getSimpleName}":${PrettyJsonSerializer.toJson(newMsg)},"${Bytes.toString(SequenceNr)}":${seqNum.toString}},"""
    }
    builder.toString()
  }

  private def emptyCnyAddress(transfer: AccountTransfer): AccountTransfer = {
    if ((transfer.currency == Currency.Cny || transfer.currency == Currency.Gooc) && transfer.`type` == TransferType.Withdrawal) {
      transfer.copy(address = None)
    } else {
      transfer
    }
  }
}
