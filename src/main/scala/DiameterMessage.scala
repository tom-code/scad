
import akka.util.ByteString

class DiameterHeader(version:Byte, leni:Int, flagsi:Int, codei:Int, app_id:Int, hh_id: Int, ee_id:Int) {
  def len   = leni
  def code  = codei
  def ee    = ee_id
  def hh    = hh_id
  def flags = flagsi
  def app   = app_id
  def isRequest = (flags & 0x80) != 0
}

class DiameterMessageDecoder(callback:(DiameterHeader, ByteString) =>Unit) {
  private var got_header = false
  private var header:DiameterHeader = null
  private var data = ByteString.empty


  private def parseMessage() : Unit = {
    if (!got_header && data.length >=20) {
      header = DiameterCodec.decodeHeader(data)
      got_header = true
      data = data.drop(20)
    }

    if (got_header && data.length >= header.len) {
      got_header = false
      callback(header, data.take(header.len))
      data = data.drop(header.len)
    }
    if (!got_header && data.length >=20) parseMessage()
  }


  def newData(d : ByteString) = {
    data = data ++ d
    parseMessage()
  }
}
