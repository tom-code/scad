import java.net.InetSocketAddress


import akka.actor._
import akka.io.Tcp._
import akka.io.{IO, Tcp}



class TCPServer(port: Int, con_actor_cfg: Props) extends Actor {
  import context.system
  val io = IO(Tcp) ! Bind(self, new InetSocketAddress(port))

  override def receive = {
    case b @ Bound(local_address) => println(s"socket bound [address=$local_address]")
    case CommandFailed(_:Bind)    => println(s"socket bind failed [port=$port]")
                                     context stop self
    case c @ Connected(remote, local) =>
                                          println(s"new connection from $remote")
                                          val h = context.actorOf(con_actor_cfg)
                                          sender() ! Register(h)
    case _ => println("receive default - should not happen")
  }
}

