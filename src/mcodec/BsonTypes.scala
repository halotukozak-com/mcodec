package mcodec

/** A 12-byte MongoDB ObjectId. BSON-native (type 0x07); falls back to a 24-char hex string elsewhere. */
final class ObjectId private (val bytes: Array[Byte]) extends AnyVal:
  def toHexString: String =
    val sb = new java.lang.StringBuilder
    bytes.foreach(b => sb.append("%02x".format(b & 0xff)))
    sb.toString
  override def equals(o: Any): Boolean = o match
    case other: ObjectId => java.util.Arrays.equals(bytes, other.bytes)
    case _ => false
  override def hashCode: Int = java.util.Arrays.hashCode(bytes)
  override def toString: String = s"ObjectId($toHexString)"

object ObjectId:
  def apply(bytes: Array[Byte]): ObjectId =
    require(bytes.length == 12, s"ObjectId must be exactly 12 bytes, got ${bytes.length}")
    new ObjectId(bytes)

  def fromHex(hex: String): ObjectId =
    if hex.length != 24 then throw ReadFailure(s"ObjectId hex string must be 24 chars, got ${hex.length}")
    val bytes = new Array[Byte](12)
    var i = 0
    while i < 12 do
      bytes(i) =
        try Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16).toByte
        catch case e: NumberFormatException => throw ReadFailure(s"invalid ObjectId hex string: $hex", e)
      i += 1
    new ObjectId(bytes)

/** A BSON regular expression (type 0x0B): a pattern plus MongoDB-style single-char options. */
final case class BsonRegex(pattern: String, options: String)

/** BSON JavaScript code (type 0x0D, without scope). */
final case class JsCode(code: String)

/** BSON MinKey (type 0xFF) — sorts before every other BSON value. */
case object MinKey

/** BSON MaxKey (type 0x7F) — sorts after every other BSON value. */
case object MaxKey
