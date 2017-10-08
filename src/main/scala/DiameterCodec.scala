import java.nio.ByteOrder

import akka.util.ByteString

object DiameterCodec {
  val AVP_FLAG_MANDATORY = 0x40
  val AVP_FLAG_VENDOR    = 0x80

  val MSG_FLAG_PROXYABLE = 0x40

  def decodeHeader(data: ByteString) = {
    val i          = data.iterator
    val version    = i.getByte
    val len:Int    = (i.getByte&0xff)<<16 | (i.getByte&0xff)<<8 | (i.getByte&0xff) - 20
    val flags:Int  = i.getByte&0xff
    val code:Int   = (i.getByte&0xff)<<16 | (i.getByte&0xff)<<8 | i.getByte&0xff
    val app_id:Int = i.getInt(ByteOrder.BIG_ENDIAN)
    val hh_id:Int  = i.getInt(ByteOrder.BIG_ENDIAN)
    val ee_id:Int  = i.getInt(ByteOrder.BIG_ENDIAN)

    new DiameterHeader(version, len, flags, code, app_id, hh_id, ee_id)
  }

  def decodeTLV(data:ByteString) = {
    val i     = data.iterator
    val code  = i.getInt(ByteOrder.BIG_ENDIAN)
    val flags = i.getByte&0xff
    var len   = (i.getByte&0xff)<<16 | (i.getByte&0xff)<<8 | (i.getByte&0xff) - 8

    var vendor = 0
    var size   = 8
    if ((flags & 0x80) == 0x80) {
      vendor = i.getInt(ByteOrder.BIG_ENDIAN)
      len  -= 4
      size += 4
    }
    (code, flags, len, vendor, data.drop(size))
  }

  def decodeParameters(data:ByteString, callback:(Int, Int, ByteString)=>Unit) {
    var (code, flags, len, vendor, rest) = decodeTLV(data)

    callback(code, vendor, rest.take(len))
    rest = rest.drop(len)

    if ((len % 4) > 0) rest = rest.drop(4 - len%4)

    if (rest.length > 0) decodeParameters(rest, callback)
  }

  private val someBytes = new Array[Byte](4)
  def encodeTLV(code:Int, flags:Int, vendor:Int, data:ByteString) = {
    val a = ByteString.newBuilder
    a.putInt(code)(ByteOrder.BIG_ENDIAN)
    a.putByte(flags.toByte)
    var len = data.length + 8
    if (vendor > 0) len += 4
    a.putByte(((len>>16)&0xff).toByte)
    a.putByte(((len>>8)&0xff).toByte)
    a.putByte((len&0xff).toByte)
    if (vendor > 0) a.putInt(vendor)(ByteOrder.BIG_ENDIAN)
    a.append(data)
    if ((len % 4) > 0) a.putBytes(someBytes, 0, 4 - len%4)
    a.result()
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
    val a = ByteString.newBuilder
    val len = data.length + 20
    a.putByte(1)

    a.putByte(((len>>16)&0xff).toByte)
    a.putByte(((len>>8)&0xff).toByte)
    a.putByte((len&0xff).toByte)

    a.putByte(flags.toByte)

    a.putByte(((code>>16)&0xff).toByte)
    a.putByte(((code>>8)&0xff).toByte)
    a.putByte((code&0xff).toByte)
    a.putInt(app)(ByteOrder.BIG_ENDIAN)
    a.putInt(hh)(ByteOrder.BIG_ENDIAN)
    a.putInt(ee)(ByteOrder.BIG_ENDIAN)
    a.append(data)

    a.result()
  }
}
