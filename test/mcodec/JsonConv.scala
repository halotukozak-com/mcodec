package halotukozak.mcodec

trait JsonConv:
  extension (x: Any) def toJson[T >: x.type: MCodec]: String = Json.write[T](x)

  def fromJson[T: MCodec](s: String): T = Json.read(s)
