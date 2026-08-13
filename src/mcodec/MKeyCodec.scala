package halotukozak.mcodec

trait MKeyCodec[K]:
  def writeKey(key: K): String
  def readKey(name: String): K

object MKeyCodec:
  def apply[K: MKeyCodec as k]: MKeyCodec[K] = k

  def create[K](w: K => String, r: String => K): MKeyCodec[K] = new:
    def writeKey(key: K): String = w(key)
    def readKey(name: String): K = r(name)

  given MKeyCodec[String] = create(identity, identity)

  given MKeyCodec[Int] = create(
    _.toString,
    s =>
      try s.toInt
      catch case e: NumberFormatException => throw ReadFailure(s"invalid Int key: $s", e),
  )

  given MKeyCodec[Long] = create(
    _.toString,
    s =>
      try s.toLong
      catch case e: NumberFormatException => throw ReadFailure(s"invalid Long key: $s", e),
  )

  given MKeyCodec[java.util.UUID] = create(
    _.toString,
    s =>
      try java.util.UUID.fromString(s)
      catch case e: IllegalArgumentException => throw ReadFailure(s"invalid UUID key: $s", e),
  )
