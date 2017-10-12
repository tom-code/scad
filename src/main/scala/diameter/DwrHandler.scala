package diameter

import akka.actor.Cancellable
import akka.io.Tcp.Write
import akka.util.ByteString

class DwrHandler (con: Connection) {

  var dwr_schedule_handler: Cancellable = _

  def scheduleDwr(): Unit = {
    import scala.concurrent.ExecutionContext.Implicits.global //for now
    dwr_schedule_handler = con.context.system.scheduler.scheduleOnce(con.conf.dwr_period) {
      con.self ! con.DwrTick
    }
  }

  def send_dwr(): Unit = {
    con.log.debug("going to send dwr")
    dwr_schedule_handler = null
    val msg = Codec.encodeHeader(DiameterDictionary.CMD_DWR, 0x80, 0, con.idGenerator.get, con.idGenerator.get, con.common_parameters)
    con.connection ! Write(msg)
  }

  def handle_dwa(header:DiameterHeader, data:ByteString): Unit = {
    con.log.debug("==== GOT DWA")
    if (dwr_schedule_handler != null) {
      con.log.debug("unexpected dwa")
      return
    }

    scheduleDwr()
  }

  def handle_dwr(header:DiameterHeader, data:ByteString): Unit = {
    con.log.debug("==== GOT DWR")

    var params = con.common_parameters
    params ++= Codec.encodeInt(DiameterDictionary.AVP_RESULT_CODE, Codec.AVP_FLAG_MANDATORY, 0, DiameterDictionary.STATUS_SUCCESS)

    val msg = Codec.encodeHeader(DiameterDictionary.CMD_DWR, 0, 0, header.hh, header.ee, params)

    con.sender() ! Write(msg)
  }

  def stop() = if (dwr_schedule_handler != null) dwr_schedule_handler.cancel()

}
