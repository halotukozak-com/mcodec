package mcodec

trait JsonConv:
  extension [T: MCodec as codec](x: T)
    def toJson: String =
      val (out, harvest) = JsonBackend.output()
      codec.write(out, x)
      harvest()

  def fromJson[T: MCodec as codec](s: String): T = codec.read(JsonBackend.input(s))
