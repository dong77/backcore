package com.coinport.coinex.util

import java.security.MessageDigest
import javax.crypto.spec.SecretKeySpec
import javax.crypto.Mac
import com.google.common.io.BaseEncoding

object Hash {
  def sha256(str: String) = BaseEncoding.base64.encode(MessageDigest.getInstance("SHA-256").digest(str.getBytes("UTF-8")))
  def murmur3(str: String): Long = MurmurHash3.MurmurHash3_x64_64(str.getBytes, 100416)
  def sha256ThenMurmur3(text: String): Long = Hash.murmur3(sha256(text))

  def hmacSha1Base64(text: String, key: String): String = {
    val signingKey = new SecretKeySpec(key.getBytes, "HmacSHA1");
    val mac = Mac.getInstance("HmacSHA1");
    mac.init(signingKey);
    val bytes = mac.doFinal(text.getBytes)
    BaseEncoding.base64.encode(bytes)
  }
}