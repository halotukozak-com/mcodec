package halotukozak.mcodec

private def writeJsonString(sb: java.lang.StringBuilder, s: String): Unit =
  sb.append('"')
  var i = 0
  while i < s.length do
    val c = s.charAt(i)
    c match
      case '"' => sb.append("\\\"")
      case '\\' => sb.append("\\\\")
      case '\b' => sb.append("\\b")
      case '\f' => sb.append("\\f")
      case '\n' => sb.append("\\n")
      case '\r' => sb.append("\\r")
      case '\t' => sb.append("\\t")
      case _ =>
        if c < 0x20 then
          sb.append("\\u00")
          val hex = "0123456789abcdef"
          sb.append(hex.charAt((c >> 4) & 0xf))
          sb.append(hex.charAt(c & 0xf))
        else sb.append(c)
    i += 1
  sb.append('"')

final class JsonOutput(sb: java.lang.StringBuilder) extends OutputAndSimpleOutput:
  def writeNull(): Unit =
    sb.append("null")

  def writeBoolean(b: Boolean): Unit =
    sb.append(b)

  def writeInt(i: Int): Unit =
    sb.append(i)

  def writeLong(l: Long): Unit =
    sb.append(l)

  def writeBigInt(b: BigInt): Unit =
    sb.append(b.toString)

  def writeDouble(d: Double): Unit =
    if java.lang.Double.isFinite(d) then sb.append(d.toString)
    else sb.append('"').append(d.toString).append('"')
    ()

  override def writeFloat(f: Float): Unit =
    if java.lang.Float.isFinite(f) then sb.append(f.toString)
    else sb.append('"').append(f.toString).append('"')
    ()

  def writeBigDecimal(b: BigDecimal): Unit =
    sb.append(b.toString)
    ()

  def writeString(s: String): Unit = writeJsonString(sb, s)

  def writeList(): ListOutput = new JsonListOutput(sb)

  def writeObject(): ObjectOutput = new JsonObjectOutput(sb)

final class JsonListOutput(sb: java.lang.StringBuilder) extends ListOutput:
  private var first = true

  def writeElement(): Output =
    sb.append(if first then '[' else ',')
    first = false
    new JsonOutput(sb)

  def finish(): Unit =
    if first then sb.append('[')
    sb.append(']')

final class JsonObjectOutput(sb: java.lang.StringBuilder) extends ObjectOutput:
  private var first = true

  def writeField(key: String): Output =
    sb.append(if first then '{' else ',')
    first = false
    writeJsonString(sb, key)
    sb.append(':')
    new JsonOutput(sb)

  def finish(): Unit =
    if first then sb.append('{')
    sb.append('}')
