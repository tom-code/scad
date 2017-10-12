package diameter


class IdGenerator {
  private val value = new java.util.concurrent.atomic.AtomicInteger()
  def get = value.incrementAndGet()
}
