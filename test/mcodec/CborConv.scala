package mcodec

// Test-only wrapper for byte-string fixtures (Array[Byte] has no structural equals; compare via Arrays.equals).
final class Bytes(val bytes: Array[Byte]):
  override def equals(o: Any): Boolean = o match
    case b: Bytes => java.util.Arrays.equals(bytes, b.bytes)
    case _ => false
  override def hashCode: Int = java.util.Arrays.hashCode(bytes)
object Bytes:
  def apply(s: String): Bytes = new Bytes(s.getBytes("UTF-8").nn)
  given MCodec[Bytes] = MCodec.byteArrayCodec.transform[Bytes](_.bytes, new Bytes(_))

trait CborConv:
  private def hex(bytes: Array[Byte]): String =
    val sb = new java.lang.StringBuilder
    var i = 0
    while i < bytes.length do
      sb.append("%02X".format(bytes(i) & 0xff))
      i += 1
    sb.toString

  extension (x: Any)
    def toCborHex[T >: x.type: MCodec as codec]: String =
      val baos = new java.io.ByteArrayOutputStream
      codec.write(new CborOutput(baos), x)
      hex(baos.toByteArray.nn)

  // Decode from a literal hex string (for read-side / indefinite-input tests).
  def fromCborHex[T: MCodec as codec](h: String): T =
    val clean = h.filterNot(_ == ' ')
    val arr = new Array[Byte](clean.length / 2)
    var i = 0
    while i < arr.length do
      arr(i) = Integer.parseInt(clean.substring(i * 2, i * 2 + 2), 16).toByte
      i += 1
    val reader = new CborReader(arr)
    val v = codec.read(new CborInput(reader))
    reader.finishTopLevel()
    v
