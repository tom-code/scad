package diameter

import akka.actor.{Actor, ActorRef, Cancellable}
import akka.event.Logging
import akka.io.Tcp.{PeerClosed, Received, Write}
import akka.util.ByteString



class DiameterConnection(config: ConnectionConfiguration) extends Actor {
  val log = Logging(context.system, this)
  log.debug("connected")

  var connection : ActorRef = _

  var dwr_schedule_handler: Cancellable = _

  val idGenerator = new IdGenerator

  case object DwrTick

  def common_parameters = DiameterCodec.encodeString(DiameterDictionary.AVP_ORIGIN_HOST,
                                                     DiameterCodec.AVP_FLAG_MANDATORY, 0, config.originHost) ++
    DiameterCodec.encodeString(DiameterDictionary.AVP_ORIGIN_REALM, DiameterCodec.AVP_FLAG_MANDATORY, 0, config.originRealm)

  private def scheduleDwr(): Unit = {
    import scala.concurrent.ExecutionContext.Implicits.global //for now
    dwr_schedule_handler = context.system.scheduler.scheduleOnce(config.dwr_period) {
      self ! DwrTick
    }
  }

  private def handle_cer(header:DiameterHeader, data:ByteString) {
    log.debug("==== GOT CER")
    var vendor_app_id: ByteString = null

    DiameterCodec.decodeParameters(data, (code, vendor, data) =>
      if (vendor == 0)
        code match {
          case DiameterDictionary.AVP_VENDOR_SPECIFIC_APP_ID => vendor_app_id = data
          case DiameterDictionary.AVP_ORIGIN_HOST            => log.debug("origin host: "+ data.decodeString("UTF-8"))
          case _   => log.debug(s"CER unknown parameter $code")
        }
    )

    var params = common_parameters
    if (vendor_app_id != null)
      params ++= DiameterCodec.encodeTLV(DiameterDictionary.AVP_VENDOR_SPECIFIC_APP_ID,
                                         DiameterCodec.AVP_FLAG_MANDATORY, 0, vendor_app_id)

    for (app <- config.apps)
      if (app.vendor_id != null)
        params ++= DiameterCodec.encodeInt(DiameterDictionary.AVP_VENDOR_ID, DiameterCodec.AVP_FLAG_MANDATORY, 0, app.vendor_id)

    params ++= DiameterCodec.encodeInt(DiameterDictionary.AVP_RESULT_CODE, DiameterCodec.AVP_FLAG_MANDATORY, 0, DiameterDictionary.STATUS_SUCCESS)

    val msg = DiameterCodec.encodeHeader(DiameterDictionary.CMD_CER, 0, 0, header.hh, header.ee, params)

    sender() ! Write(msg)

    scheduleDwr()
  }


  private def send_dwr(): Unit = {
    log.debug("going to send dwr")
    dwr_schedule_handler = null
    val msg = DiameterCodec.encodeHeader(DiameterDictionary.CMD_DWR, 0x80, 0, idGenerator.get, idGenerator.get, common_parameters)
    connection ! Write(msg)
  }

  private def handle_dwa(header:DiameterHeader, data:ByteString): Unit = {
    log.debug("==== GOT DWA")
    if (dwr_schedule_handler != null) {
      log.debug("unexpected dwa")
      return
    }

    scheduleDwr()
  }

  private def handle_dwr(header:DiameterHeader, data:ByteString) {
    log.debug("==== GOT DWR")

    var params = common_parameters
    params ++= DiameterCodec.encodeInt(DiameterDictionary.AVP_RESULT_CODE, DiameterCodec.AVP_FLAG_MANDATORY, 0, DiameterDictionary.STATUS_SUCCESS)

    val msg = DiameterCodec.encodeHeader(DiameterDictionary.CMD_DWR, 0, 0, header.hh, header.ee, params)

    sender() ! Write(msg)
  }

  private def callback_message(header:DiameterHeader, data:ByteString) {
    for (app <- config.apps) {
      if (header.app == app.application_id) {
        app.handle_message(header, data, this)
        return
      }
    }
    if (header.isRequest) {
      header.code match {
        case DiameterDictionary.CMD_CER => handle_cer(header, data)
        case DiameterDictionary.CMD_DWR => handle_dwr(header, data)
        case _ => log.info(s"unhandled message ${header.code}")
      }
    } else {
      header.code match {
        case DiameterDictionary.CMD_DWR => handle_dwa(header, data)
        case _ => log.info(s"unhandled message ${header.code}")
      }
    }
  }


  private val decoder = new DiameterMessageDecoder(callback_message)
  override def receive = {
    case PeerClosed =>  log.debug("closed")
                        if (dwr_schedule_handler != null) dwr_schedule_handler.cancel()
                        context stop self

    case Received(data) =>  decoder.newData(data)
    case DwrTick => send_dwr()
    case ClientConnected(con_actor) => connection = con_actor.asInstanceOf[ActorRef]

    case a @ _ => log.error("unexpected message" + a.toString)
  }
}

