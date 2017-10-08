
import akka.actor.Actor
import akka.io.Tcp.{PeerClosed, Received, Write}
import akka.util.ByteString

class DiameterConnection(origin_host:String, origin_realm:String, apps:List[DiameterApp]) extends Actor {
  println("connected")

  def common_parameters = DiameterCodec.encodeString(DiameterDictionary.AVP_ORIGIN_HOST,
                                                     DiameterCodec.AVP_FLAG_MANDATORY, 0, origin_host) ++
    DiameterCodec.encodeString(DiameterDictionary.AVP_ORIGIN_REALM, DiameterCodec.AVP_FLAG_MANDATORY, 0, origin_realm)

  private def handle_cer(header:DiameterHeader, data:ByteString) {
    println("==== GOT CER")
    var vendor_app_id: ByteString = null

    DiameterCodec.decodeParameters(data, (code, vendor, data) =>
      if (vendor == 0)
        code match {
          case DiameterDictionary.AVP_VENDOR_SPECIFIC_APP_ID => vendor_app_id = data
          case DiameterDictionary.AVP_ORIGIN_HOST            => println("origin host: "+ data.decodeString("UTF-8"))
          case _   => println(s"CER unknown parameter $code")
        }
    )

    var params = common_parameters
    if (vendor_app_id != null)
      params ++= DiameterCodec.encodeTLV(DiameterDictionary.AVP_VENDOR_SPECIFIC_APP_ID,
                                         DiameterCodec.AVP_FLAG_MANDATORY, 0, vendor_app_id)

    params ++= DiameterCodec.encodeInt(DiameterDictionary.AVP_VENDOR_ID, DiameterCodec.AVP_FLAG_MANDATORY, 0, 10415)
    params ++= DiameterCodec.encodeInt(DiameterDictionary.AVP_RESULT_CODE, DiameterCodec.AVP_FLAG_MANDATORY, 0, 2001)

    val msg = DiameterCodec.encodeHeader(DiameterDictionary.CMD_CER, 0, 0, header.hh, header.ee, params)

    sender() ! Write(msg)
  }

  private def handle_dwr(header:DiameterHeader, data:ByteString) {
    println("==== GOT DWR")

    var params = common_parameters
    params ++= DiameterCodec.encodeInt(DiameterDictionary.AVP_RESULT_CODE, DiameterCodec.AVP_FLAG_MANDATORY, 0, 2001)

    val msg = DiameterCodec.encodeHeader(DiameterDictionary.CMD_DWR, 0, 0, header.hh, header.ee, params)

    sender() ! Write(msg)
  }

  private def callback_message(header:DiameterHeader, data:ByteString) {
    for (app <- apps) {
      if (header.app == app.id) {
        app.handle_message(header, data, this)
        return
      }
    }
    if (header.isRequest) {
      header.code match {
        case DiameterDictionary.CMD_CER => handle_cer(header, data)
        case DiameterDictionary.CMD_DWR => handle_dwr(header, data)
        case _ => println(s"unhandled message ${header.code}")
      }
    }
  }

  private val decoder = new Decoder(callback_message)
  override def receive = {
    case PeerClosed =>  println("closed")
                        context stop self

    case Received(data) =>  decoder.newData(data)

    case _ => println("handler default")
  }
}

