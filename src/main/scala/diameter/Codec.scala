package diameter

import java.nio.ByteOrder

import akka.util.{ByteIterator, ByteString, ByteStringBuilder}

object Codec {
  val AVP_FLAG_MANDATORY = 0x40
  val AVP_FLAG_VENDOR    = 0x80

  val MSG_FLAG_PROXYABLE = 0x40

  private def decode24bitInt(i: ByteIterator) = (i.getByte&0xff)<<16 | (i.getByte&0xff)<<8 | i.getByte&0xff

  def decodeHeader(data: ByteString) = {
    val i          = data.iterator
    val version    = i.getByte
    val len        = decode24bitInt(i) - 20
    val flags:Int  = i.getByte&0xff
    val code       = decode24bitInt(i)
    val app_id     = i.getInt(ByteOrder.BIG_ENDIAN)
    val hh_id      = i.getInt(ByteOrder.BIG_ENDIAN)
    val ee_id      = i.getInt(ByteOrder.BIG_ENDIAN)

    new DiameterHeader(version, len, flags, code, app_id, hh_id, ee_id)
  }

  def decodeTLV(data:ByteString) = {
    val i     = data.iterator
    val code  = i.getInt(ByteOrder.BIG_ENDIAN)
    val flags = i.getByte&0xff
    var len   = decode24bitInt(i) - 8

    var vendor = 0
    var header_size   = 8
    if ((flags & 0x80) == 0x80) {
      vendor = i.getInt(ByteOrder.BIG_ENDIAN)
      len  -= 4
      header_size += 4
    }
    (code, flags, len, vendor, data.drop(header_size))
  }

  def decodeParameters(data:ByteString, callback:(Int, Int, ByteString)=>Unit) : Unit =  {
    var (code, flags, len, vendor, rest) = decodeTLV(data)

    callback(code, vendor, rest.take(len))
    rest = rest.drop(len)

    if ((len % 4) > 0) rest = rest.drop(4 - len%4)

    if (rest.nonEmpty) decodeParameters(rest, callback)
  }

  private def encode24bitInt(value:Int, builder: ByteStringBuilder) = {
    builder.putByte(((value>>16)&0xff).toByte)
    builder.putByte(((value>>8)&0xff).toByte)
    builder.putByte((value&0xff).toByte)
  }

  private val zeroBytes = new Array[Byte](4)
  def encodeTLV(code:Int, flags:Int, vendor:Int, data:ByteString) = {
    val builder = ByteString.newBuilder

    builder.putInt(code)(ByteOrder.BIG_ENDIAN)
    builder.putByte(flags.toByte)
    var len = data.length + 8
    if (vendor > 0) len += 4
    encode24bitInt(len, builder)
    if (vendor > 0) builder.putInt(vendor)(ByteOrder.BIG_ENDIAN)
    builder.append(data)
    if ((len % 4) > 0) builder.putBytes(zeroBytes, 0, 4 - len%4)
    builder.result()
  }


  def encodeString(code:Int, flags:Int, vendor:Int, data:String) = {
    val bytes = ByteString.apply(data, "UTF-8")
    encodeTLV(code, flags, vendor, bytes)
  }

  def encodeInt(code:Int, flags:Int, vendor:Int, data:Int) = {
    val bytes = ByteString.newBuilder.putInt(data)(ByteOrder.BIG_ENDIAN).result()
    encodeTLV(code, flags, vendor, bytes)
  }

  def encodeHeader(code:Int, flags:Int, app:Int, hh:Int, ee:Int, data:ByteString) = {
    val builder = ByteString.newBuilder
    val len = data.length + 20
    builder.putByte(1) //version

    encode24bitInt(len, builder)

    builder.putByte(flags.toByte)

    encode24bitInt(code, builder)

    builder.putInt(app)(ByteOrder.BIG_ENDIAN)
    builder.putInt(hh)(ByteOrder.BIG_ENDIAN)
    builder.putInt(ee)(ByteOrder.BIG_ENDIAN)
    builder.append(data)

    builder.result()
  }
}
