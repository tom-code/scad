package diameter

import java.net.InetSocketAddress

import akka.actor._
import akka.event.Logging
import akka.io.Tcp._
import akka.io.{IO, Tcp}


case class ClientConnected(actor: ActorRef)

class TCPServer(port: Int, con_actor_cfg: Props) extends Actor {
  val log = Logging(context.system, this)

  import context.system
  IO(Tcp) ! Bind(self, new InetSocketAddress(port))

  override def receive = {
    case b @ Bound(local_address)     => log.info(s"socket bound [address=$local_address]")
    case CommandFailed(_:Bind)        => log.error(s"socket bind failed [port=$port]")
                                          context stop self
    case c @ Connected(remote, local) =>
                                          log.info(s"new connection from $remote")
                                          val h = context.actorOf(con_actor_cfg)
                                          h ! ClientConnected(sender())
                                          sender() ! Register(h)

    case _ => log.error("receive default - should not happen")
  }
}

