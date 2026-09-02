package halotukozak.mcodec

import scala.annotation.switch

enum JsonToken:
  case Obj, Arr, Str, Bool, Num

final class JsonReader(s: String):
  private var i = 0

  private def posSuffix(at: Int): String =
    var line = 1
    var lastNl = -1
    var k = 0
    val end = math.min(at, s.length)
    while k < end do
      if s.charAt(k) == '\n' then
        line += 1
        lastNl = k
      k += 1
    val col = at - lastNl
    s" (at $line:$col)"

  private def skipWs(): Unit =
    while i < s.length &&
      ((s.charAt(i): @switch) match
        case ' ' | '\t' | '\n' | '\r' => true
        case _ => false)
    do i += 1

  private def expect(c: Char): Unit =
    skipWs()
    if i >= s.length || s.charAt(i) != c then throw ReadFailure(s"expected '$c'" + posSuffix(i))
    i += 1

  def readRawNumber(): String =
    skipWs()
    val start = i
    if i < s.length && (s.charAt(i) == '-' || s.charAt(i) == '+') then i += 1
    while i < s.length && {
        val c = s.charAt(i)
        (c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-'
      }
    do i += 1
    if i == start then throw ReadFailure("expected number" + posSuffix(start))
    s.substring(start, i)

  inline private val NotFastInt = Long.MinValue

  private def fastLong(): Long =
    val start = i
    val n = s.length
    var neg = false
    if i < n && s.charAt(i) == '-' then
      neg = true
      i += 1
    val digitsStart = i
    var acc = 0L
    while i < n && { val c = s.charAt(i); c >= '0' && c <= '9' } do
      acc = acc * 10 + (s.charAt(i) - '0')
      i += 1
    val digits = i - digitsStart
    if digits == 0 || digits > 18 || (i < n && { val c = s.charAt(i); c == '.' || c == 'e' || c == 'E' }) then
      i = start
      NotFastInt
    else if neg then -acc
    else acc

  def readLong(): Long =
    skipWs()
    val v = fastLong()
    if v != NotFastInt then v
    else
      val lex = readRawNumber()
      try java.lang.Long.parseLong(lex)
      catch
        case _: NumberFormatException =>
          val bd = BigDecimal(lex)
          if bd.isValidLong then bd.toLongExact else throw ReadFailure(s"not a Long: $lex")

  def readInt(): Int =
    skipWs()
    val v = fastLong()
    if v != NotFastInt then
      if v >= Int.MinValue && v <= Int.MaxValue then v.toInt
      else throw ReadFailure(s"not an Int: $v")
    else
      val lex = readRawNumber()
      try Integer.parseInt(lex)
      catch
        case _: NumberFormatException =>
          val bd = BigDecimal(lex)
          if bd.isValidInt then bd.toIntExact else throw ReadFailure(s"not an Int: $lex")

  def readRawString(): String =
    skipWs()
    expect('"')
    val start = i
    var j = i
    while j < s.length && { val c = s.charAt(j); c != '"' && c != '\\' } do j += 1
    if j >= s.length then throw ReadFailure("unterminated string" + posSuffix(j))
    if s.charAt(j) == '"' then
      i = j + 1
      return s.substring(start, j)
    val b = new java.lang.StringBuilder(s.length - start)
    b.append(s, start, j)
    i = j
    var done = false
    while !done do
      if i >= s.length then throw ReadFailure("unterminated string" + posSuffix(i))
      val c = s.charAt(i)
      i += 1
      (c: @switch) match
        case '"' => done = true
        case '\\' =>
          if i >= s.length then throw ReadFailure("unterminated string" + posSuffix(i))
          val e = s.charAt(i)
          i += 1
          (e: @switch) match
            case '"' => b.append('"')
            case '\\' => b.append('\\')
            case '/' => b.append('/')
            case 'b' => b.append('\b')
            case 'f' => b.append('\f')
            case 'n' => b.append('\n')
            case 'r' => b.append('\r')
            case 't' => b.append('\t')
            case 'u' =>
              if i + 4 > s.length then throw ReadFailure("truncated \\u escape" + posSuffix(i))
              val hex = s.substring(i, i + 4)
              i += 4
              b.append(Integer.parseInt(hex, 16).toChar)
            case other => throw ReadFailure(s"invalid escape: \\$other" + posSuffix(i))
        case other => b.append(other)
    b.toString

  def readNull(): Boolean =
    skipWs()
    if i + 4 <= s.length && s.charAt(i) == 'n' && s.charAt(i + 1) == 'u' && s.charAt(i + 2) == 'l' && s.charAt(
        i + 3,
      ) == 'l'
    then
      i += 4
      true
    else false

  def readBoolean(): Boolean =
    skipWs()
    if i + 4 <= s.length && s.startsWith("true", i) then
      i += 4
      true
    else if i + 5 <= s.length && s.startsWith("false", i) then
      i += 5
      false
    else throw ReadFailure("expected boolean" + posSuffix(i))

  def skipValue(): Unit =
    skipWs()
    if i >= s.length then throw ReadFailure("unexpected end of input" + posSuffix(i))
    (s.charAt(i): @switch) match
      case '{' => skipContainer('{', '}')
      case '[' => skipContainer('[', ']')
      case '"' => readRawString()
      case _ =>
        val c = s.charAt(i)
        if c == 't' || c == 'f' then readBoolean()
        else if c == 'n' then
          if !readNull() then throw ReadFailure("invalid token" + posSuffix(i))
        else readRawNumber()

  private def skipContainer(open: Char, close: Char): Unit =
    expect(open)
    var depth = 1
    while depth > 0 do
      skipWs()
      if i >= s.length then throw ReadFailure("unterminated container" + posSuffix(i))
      val c = s.charAt(i)
      c match
        case `open` =>
          depth += 1
          i += 1
        case `close` =>
          depth -= 1
          i += 1
        case '"' => readRawString()
        case _ => i += 1

  private[mcodec] def finishTopLevel(): Unit =
    skipWs()
    if i < s.length then throw ReadFailure("trailing data after top-level value" + posSuffix(i))

  // Peek the next significant token kind WITHOUT consuming (for snapshot capture only).
  private[mcodec] def peekToken: JsonToken =
    skipWs()
    if i >= s.length then throw ReadFailure("unexpected end of input" + posSuffix(i))
    (s.charAt(i): @switch) match
      case '{' => JsonToken.Obj
      case '[' => JsonToken.Arr
      case '"' => JsonToken.Str
      case 't' | 'f' => JsonToken.Bool
      case 'n' => JsonToken.Num
      case _ => JsonToken.Num

  // package-private cursor helpers for list/object inputs
  private[mcodec] def consume(c: Char): Unit = expect(c)

  private[mcodec] def tryConsume(c: Char): Boolean =
    skipWs()
    if i < s.length && s.charAt(i) == c then
      i += 1
      true
    else false

class JsonInput(reader: JsonReader) extends InputAndSimpleInput:
  def readNull(): Boolean = reader.readNull()

  def readBoolean(): Boolean = reader.readBoolean()

  def readString(): String = reader.readRawString()

  def readInt(): Int = reader.readInt()

  def readLong(): Long = reader.readLong()

  def readDouble(): Double = reader.readRawNumber().toDouble

  override def readFloat(): Float =
    if reader.peekToken == JsonToken.Str then reader.readRawString().toFloat
    else reader.readRawNumber().toFloat

  def readBigInt(): BigInt = BigInt(stripWholeFraction(reader.readRawNumber()))

  def readBigDecimal(): BigDecimal = BigDecimal(reader.readRawNumber())

  def readList(): ListInput = new JsonListInput(reader)

  def readObject(): ObjectInput = new JsonObjectInput(reader)

  def skip(): Unit = reader.skipValue()

  private[mcodec] def peekToken: JsonToken = reader.peekToken
  private[mcodec] def rawNumber(): String = reader.readRawNumber()

  override def peekKind(): InputKind = reader.peekToken match
    case JsonToken.Obj => InputKind.Object
    case JsonToken.Arr => InputKind.List
    case JsonToken.Str => InputKind.String
    case JsonToken.Bool => InputKind.Boolean
    case JsonToken.Num => InputKind.Number

  private def stripWholeFraction(lex: String): String =
    if lex.contains('.') || lex.contains('e') || lex.contains('E') then BigDecimal(lex).toBigInt.toString
    else lex

final class JsonListInput(reader: JsonReader) extends ListInput:
  private var started = false
  private var ended = false

  def hasNext: Boolean =
    if ended then false
    else if !started then
      reader.consume('[')
      started = true
      if reader.tryConsume(']') then
        ended = true
        false
      else true
    else if reader.tryConsume(',') then true
    else
      reader.consume(']')
      ended = true
      false

  def nextElement(): Input = new JsonInput(reader)

final class JsonFieldInput(reader: JsonReader, val fieldName: String) extends JsonInput(reader), FieldInput

final class JsonObjectInput(reader: JsonReader) extends ObjectInput:
  private var started = false
  private var ended = false

  def hasNext: Boolean =
    if ended then false
    else if !started then
      reader.consume('{')
      started = true
      if reader.tryConsume('}') then
        ended = true
        false
      else true
    else if reader.tryConsume(',') then true
    else
      reader.consume('}')
      ended = true
      false

  def nextField(): FieldInput =
    val name = reader.readRawString()
    reader.consume(':')
    new JsonFieldInput(reader, name)

  def getNextNamedField(name: String): FieldInput =
    val f = nextField()
    if f.fieldName == name then f
    else throw ReadFailure(s"expected field '$name' but got '${f.fieldName}'")
