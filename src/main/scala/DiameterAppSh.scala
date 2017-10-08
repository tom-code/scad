import java.nio.ByteOrder

import akka.io.Tcp.Write
import akka.util.ByteString

class DiameterAppSh extends DiameterApp {
  private def handle_udr(header:DiameterHeader, data:ByteString, connection:DiameterConnection) {
    println("==== GOT UDR")
    var data_refs = new Array[Int](0)
    var public_identity = "<none>"
    var vendor_app_id: ByteString = null
    var session_id : ByteString = null


    DiameterCodec.decodeParameters(data, (code, vendor, len, data)=>
      vendor match {
        case 0 => code match {
          case DiameterDictionary.AVP_SESSION_ID             => session_id = data
          case DiameterDictionary.AVP_VENDOR_SPECIFIC_APP_ID => vendor_app_id = data
          case DiameterDictionary.AVP_ORIGIN_HOST            => println("origin host: " + data.decodeString("UTF-8"))
          case _                                   => println(s"UDR unknown parameter $code")
        }
        case DiameterDictionary.VENDOR_3GPP => code match {
          case 703 => data_refs = data_refs :+ data.iterator.getInt(ByteOrder.BIG_ENDIAN)
          case 700 => DiameterCodec.decodeParameters(data, (code, vendor, len, dt) =>
            code match {
              case 601 => public_identity = dt.decodeString("UTF-8")
            })
          case _   => println(s"UDR unknown parameter $code")
        }
      }
    )

    println(s"public identity = $public_identity")
    print("requested data_refs = ")
    for (ref <- data_refs) print(ref + " ")
    println()

    var params = connection.common_parameters

    if (session_id != null)
      params ++= DiameterCodec.encodeTLV (DiameterDictionary.AVP_SESSION_ID, DiameterCodec.AVP_FLAG_MANDATORY, 0, session_id)

    params ++= DiameterCodec.encodeInt(DiameterDictionary.AVP_RESULT_CODE, DiameterCodec.AVP_FLAG_MANDATORY, 0, 2001)
    params ++= DiameterCodec.encodeInt(DiameterDictionary.AVP_AUTH_SESSION_STATE, DiameterCodec.AVP_FLAG_MANDATORY, 0, 1)

    if (vendor_app_id != null)
      params ++= DiameterCodec.encodeTLV (DiameterDictionary.AVP_VENDOR_SPECIFIC_APP_ID, DiameterCodec.AVP_FLAG_MANDATORY, 0, vendor_app_id)

    val msg = DiameterCodec.encodeHeader(DiameterDictionary.CMD_UDR, DiameterCodec.MSG_FLAG_PROXYABLE, DiameterDictionary.APP_SH, header.hh, header.ee, params)
    connection.sender() ! Write(msg)
  }

  override def handle_message(header: DiameterHeader, data: ByteString, connection:DiameterConnection) {
    println("sh handle message")
    if (header.code == DiameterDictionary.CMD_UDR)
      handle_udr(header, data, connection)
  }

  override def id: Integer = DiameterDictionary.APP_SH
}
