package diameter

import scala.language.postfixOps
import scala.concurrent.duration._


class ConnectionConfiguration  {
  var originHost = "localhost"
  var originRealm = "local"
  var apps:List[DiameterApp] = List.empty
  var dwr_period = 20 seconds


  def setOriginHost(v : String) = {
    originHost = v
    this
  }

  def setOriginRealm(v : String) = {
    originRealm = v
    this
  }

  def setApps(v : List[DiameterApp] ) = {
    apps = v
    this
  }

  def setDwrPeriod(v : Long) = {
    dwr_period = FiniteDuration(v, scala.concurrent.duration.SECONDS)
    this
  }


}
