package diameter

import akka.actor.{Actor, ActorRef, Cancellable}
import akka.event.Logging
import akka.io.Tcp.{PeerClosed, Received, Write}
import akka.util.ByteString



class Connection(config: ConnectionConfiguration) extends Actor {
  val log = Logging(context.system, this)
  log.debug("connected")

  var connection : ActorRef = _


  val idGenerator = new IdGenerator

  case object DwrTick

  val conf = config

  val dwrHandler = new DwrHandler(this)

  def common_parameters = Codec.encodeString(DiameterDictionary.AVP_ORIGIN_HOST,
                                                     Codec.AVP_FLAG_MANDATORY, 0, config.originHost) ++
    Codec.encodeString(DiameterDictionary.AVP_ORIGIN_REALM, Codec.AVP_FLAG_MANDATORY, 0, config.originRealm)



  private def handle_cer(header:DiameterHeader, data:ByteString): Unit = {
    log.debug("==== GOT CER")
    var vendor_app_id: ByteString = null

    Codec.decodeParameters(data, (code, vendor, data) =>
      if (vendor == 0)
        code match {
          case DiameterDictionary.AVP_VENDOR_SPECIFIC_APP_ID => vendor_app_id = data
          case DiameterDictionary.AVP_ORIGIN_HOST            => log.debug("origin host: "+ data.decodeString("UTF-8"))
          case _   => log.debug(s"CER unknown parameter $code")
        }
    )

    var params = common_parameters
    if (vendor_app_id != null)
      params ++= Codec.encodeTLV(DiameterDictionary.AVP_VENDOR_SPECIFIC_APP_ID,
                                         Codec.AVP_FLAG_MANDATORY, 0, vendor_app_id)

    for (app <- config.apps)
      if (app.vendor_id != null)
        params ++= Codec.encodeInt(DiameterDictionary.AVP_VENDOR_ID, Codec.AVP_FLAG_MANDATORY, 0, app.vendor_id)

    params ++= Codec.encodeInt(DiameterDictionary.AVP_RESULT_CODE, Codec.AVP_FLAG_MANDATORY, 0, DiameterDictionary.STATUS_SUCCESS)

    val msg = Codec.encodeHeader(DiameterDictionary.CMD_CER, 0, 0, header.hh, header.ee, params)

    sender() ! Write(msg)

    dwrHandler.scheduleDwr()
  }


  private def callback_message(header:DiameterHeader, data:ByteString): Unit = {
    for (app <- config.apps) {
      if (header.app == app.application_id) {
        app.handle_message(header, data, this)
        return
      }
    }
    if (header.isRequest) {
      header.code match {
        case DiameterDictionary.CMD_CER => handle_cer(header, data)
        case DiameterDictionary.CMD_DWR => dwrHandler.handle_dwr(header, data)
        case _ => log.info(s"unhandled message ${header.code}")
      }
    } else {
      header.code match {
        case DiameterDictionary.CMD_DWR => dwrHandler.handle_dwa(header, data)
        case _ => log.info(s"unhandled message ${header.code}")
      }
    }
  }


  private val decoder = new MessageDecoder(callback_message)
  override def receive = {
    case PeerClosed =>  log.debug("closed")
                        dwrHandler.stop()
                        context stop self

    case Received(data) =>  decoder.newData(data)
    case DwrTick => dwrHandler.send_dwr()
    case ClientConnected(con_actor) => connection = con_actor.asInstanceOf[ActorRef]

    case a @ _ => log.error("unexpected message" + a.toString)
  }
}

