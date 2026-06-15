package mcodec

trait JsonConv:
  extension [T: MCodec](x: T) def toJson: String = Json.write(x)

  def fromJson[T: MCodec](s: String): T = Json.read(s)
