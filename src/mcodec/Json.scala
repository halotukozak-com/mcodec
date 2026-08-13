package halotukozak.mcodec

object Json:
  def read[T: MCodec as codec](s: String): T =
    val reader = new JsonReader(s)
    val value = codec.read(new JsonInput(reader))
    reader.finishTopLevel()
    value

  def write[T: MCodec as codec](value: T): String =
    val sb = new java.lang.StringBuilder
    codec.write(new JsonOutput(sb), value)
    sb.toString
