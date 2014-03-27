package com.coinport.coinex.util

import java.security.MessageDigest
import javax.crypto.spec.SecretKeySpec
import javax.crypto.Mac
import org.apache.commons.codec.binary.Hex
import com.google.common.io.BaseEncoding

object Hash {
  def sha256(str: String) = MessageDigest.getInstance("SHA-256").digest(str.getBytes("UTF-8"));
  def murmur3(str: String): Long = MurmurHash3.MurmurHash3_x64_64(str.getBytes, 100416)

  def hmacSha1Base64(text: String, key: String): String = {
    val signingKey = new SecretKeySpec(key.getBytes, "HmacSHA1");
    val mac = Mac.getInstance("HmacSHA1");
    mac.init(signingKey);
    val bytes = mac.doFinal(text.getBytes)
    BaseEncoding.base64.encode(bytes)
  }
}