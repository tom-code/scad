import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory
import diameter.{DiameterAppSh, Connection, TCPServer}

object Main extends App {
  override def main(args:Array[String]): Unit = {
    println("welcome!")

    val system = ActorSystem("system")

    val conf = ConfigFactory.load()
    val listen_port = conf.getInt("dia.listen_port")


    //val tcp_server = system.actorOf(Props(new diameter.TCPServer(listen_port, Props(new diameter.DiameterConnection(origin_host, origin_realm, app_list)))))
    //val tcp_server = system.actorOf(Props(classOf[diameter.TCPServer], listen_port, Props(classOf[diameter.DiameterConnection], origin_host, origin_realm, app_list)))


    val con_config = new diameter.ConnectionConfiguration()
      .setOriginHost(conf.getString("dia.origin-host"))
      .setOriginRealm(conf.getString("dia.origin-realm"))
      .setApps(List(new DiameterAppSh))
      .setDwrPeriod(10)


    val con_actor_cfg = Props(classOf[Connection], con_config)
    system.actorOf(Props(classOf[TCPServer], listen_port, con_actor_cfg))


    println("hit enter to stop me")
    Console.in.read()
    system.shutdown()
  }
}
