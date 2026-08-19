package halotukozak.mcodec

trait BsonConv:
  private def hex(bytes: Array[Byte]): String =
    val sb = new java.lang.StringBuilder
    var i = 0
    while i < bytes.length do
      sb.append("%02X".format(bytes(i) & 0xff))
      i += 1
    sb.toString

  extension (x: Any)
    def toBsonHex[T >: x.type: MCodec as codec]: String =
      val (out, harvest) = BsonBackend.output()
      codec.write(out, x)
      hex(harvest())

  // Decode from a literal hex string (root-level document only, per the BSON contract).
  def fromBsonHex[T: MCodec as codec](h: String): T =
    val clean = h.filterNot(_ == ' ')
    val arr = new Array[Byte](clean.length / 2)
    var i = 0
    while i < arr.length do
      arr(i) = Integer.parseInt(clean.substring(i * 2, i * 2 + 2), 16).toByte
      i += 1
    val reader = new BsonReader(arr)
    val v = codec.read(new BsonRootInput(reader))
    reader.finishTopLevel()
    v
