import akka.util.ByteString

trait DiameterApp {
  def id: Integer
  def handle_message(header:DiameterHeader, data:ByteString, connection:DiameterConnection)
}
