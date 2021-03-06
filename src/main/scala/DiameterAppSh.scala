package diameter

import java.nio.ByteOrder

import akka.io.Tcp.Write
import akka.util.ByteString

class DiameterAppSh extends AppInterface {
  private def handle_udr(header:DiameterHeader, data:ByteString, connection:Connection) {
    println("==== GOT UDR")
    var data_refs = new Array[Int](0)
    var public_identity = "<none>"
    var vendor_app_id: ByteString = null
    var session_id : ByteString = null


    Codec.decodeParameters(data, (code, vendor, data)=>
      vendor match {
        case 0 => code match {
          case DiameterDictionary.AVP_SESSION_ID             => session_id = data
          case DiameterDictionary.AVP_VENDOR_SPECIFIC_APP_ID => vendor_app_id = data
          case DiameterDictionary.AVP_ORIGIN_HOST            => println("origin host: " + data.decodeString("UTF-8"))
          case _                                             => println(s"UDR unknown parameter $code")
        }
        case DiameterDictionary.VENDOR_3GPP => code match {
          case DiameterDictionary.AVP_DATA_REF      => data_refs = data_refs :+ data.iterator.getInt(ByteOrder.BIG_ENDIAN)
          case DiameterDictionary.AVP_USER_IDENTITY => Codec.decodeParameters(data, (code, vendor, dt) =>
            code match {
              case DiameterDictionary.AVP_PUBLIC_IDENTITY => public_identity = dt.decodeString("UTF-8")
            })
          case _   => println(s"UDR unknown parameter $code")
        }
      }
    )

    println(s"public identity = $public_identity")
    print("requested data_refs = ")
    for (ref <- data_refs) print(ref + " ")
    println()

    // now generate response
    var params = connection.common_parameters

    if (session_id != null)
      params ++= Codec.encodeTLV(DiameterDictionary.AVP_SESSION_ID, Codec.AVP_FLAG_MANDATORY, 0, session_id)

    params ++= Codec.encodeInt(DiameterDictionary.AVP_RESULT_CODE, Codec.AVP_FLAG_MANDATORY, 0, 2001)
    params ++= Codec.encodeInt(DiameterDictionary.AVP_AUTH_SESSION_STATE, Codec.AVP_FLAG_MANDATORY, 0, 1)


    if (vendor_app_id != null)
      params ++= Codec.encodeTLV (DiameterDictionary.AVP_VENDOR_SPECIFIC_APP_ID,
                                          Codec.AVP_FLAG_MANDATORY, 0, vendor_app_id)

    val msg = Codec.encodeHeader(DiameterDictionary.CMD_UDR,
                                         Codec.MSG_FLAG_PROXYABLE,
                                         DiameterDictionary.APP_SH,
                                         header.hh, header.ee, params)
    connection.sender() ! Write(msg)
  }

  override def handle_message(header: DiameterHeader, data: ByteString, connection:Connection) {
    if (header.isRequest) {
      header.code match {
        case DiameterDictionary.CMD_UDR => handle_udr(header, data, connection)
      }
    }

  }

  override def application_id = DiameterDictionary.APP_SH

  override def vendor_id = DiameterDictionary.VENDOR_3GPP
}
