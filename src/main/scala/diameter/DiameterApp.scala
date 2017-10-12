package diameter

import akka.util.ByteString

trait DiameterApp {
  def vendor_id : Integer
  def application_id: Integer
  def handle_message(header:DiameterHeader, data:ByteString, connection:DiameterConnection)
}
