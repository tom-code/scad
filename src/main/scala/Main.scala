import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory

object main extends App {
  override def main(args:Array[String]): Unit = {
    println("welcome!")

    val conf = ConfigFactory.load()
    val origin_host = conf.getString("dia.origin-host")
    val origin_realm = conf.getString("dia.origin-realm")
    val listen_port = conf.getInt("dia.listen_port")

    println(s"configured local host=$origin_host realm=$origin_realm")


    val system = ActorSystem("system")
    val app_list = List(new DiameterAppSh)
    val tcp_server = system.actorOf(Props(new TCPServer(listen_port, Props(new DiameterConnection(origin_host, origin_realm, app_list)))))

    println("hit enter to stop me")
    Console.in.read()
    system.shutdown()
  }
}
