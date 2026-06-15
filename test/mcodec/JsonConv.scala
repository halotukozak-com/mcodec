package mcodec

trait JsonConv:
  def toJson[T: MCodec as codec](x: T): String =
    val (out, harvest) = JsonBackend.output()
    codec.write(out, x)
    harvest()

  def fromJson[T: MCodec as codec](s: String): T = codec.read(JsonBackend.input(s))
