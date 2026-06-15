package mcodec

// Backend-agnostic, fully-materialised snapshot of an Input value, used by FlatSumCodec
// to buffer a flat object's fields and replay them (in any order) into the selected case
// body codec. The snapshot is INDEPENDENT of the live cursor (Pitfall 2): each value is
// consumed eagerly into this tree at capture time and never refers back to the source Input.

enum CapturedValue:
  case CNull
  case CString(s: String)
  case CBoolean(b: Boolean)
  case CNumber(lexeme: String)
  case CList(items: Vector[CapturedValue])
  case CObject(fields: Vector[(String, CapturedValue)])

object CapturedValue:
  // Eagerly consume one value from `in` into an independent snapshot.
  def capture(in: Input): CapturedValue =
    if in.readNull() then CNull
    else
      in match
        case j: JsonInput => captureJson(j)
        case other => captureGeneric(other)

  // JSON path: peek the next significant token to pick the right typed read, then snapshot.
  private def captureJson(j: JsonInput): CapturedValue =
    j.peekToken match
      case JsonToken.Obj =>
        val oi = j.readObject()
        val b = Vector.newBuilder[(String, CapturedValue)]
        while oi.hasNext do
          val f = oi.nextField()
          b += (f.fieldName -> capture(f))
        CObject(b.result())
      case JsonToken.Arr =>
        val li = j.readList()
        val b = Vector.newBuilder[CapturedValue]
        while li.hasNext do b += capture(li.nextElement())
        CList(b.result())
      case JsonToken.Str => CString(j.readString())
      case JsonToken.Bool => CBoolean(j.readSimple().readBoolean())
      case JsonToken.Num => CNumber(j.rawNumber())

  // Generic fallback: flat-mode reads run against the JSON backend; other backends are unsupported.
  private def captureGeneric(in: Input): CapturedValue =
    throw ReadFailure("flat read: cannot snapshot value from this backend")

// Replays a captured value as an Input. Simple values re-parse their snapshot lazily per the
// typed read the consumer chooses (number lexeme is re-parsed to Int/Long/Double/BigInt/BigDecimal).
class CapturedInput(value: CapturedValue) extends InputAndSimpleInput:
  import CapturedValue.*

  private def mismatch(expected: String): Nothing =
    throw ReadFailure(s"expected $expected but got $value")

  def readNull(): Boolean = value == CNull
  def readString(): String = value match
    case CString(s) => s
    case _ => mismatch("string")
  def readBoolean(): Boolean = value match
    case CBoolean(b) => b
    case _ => mismatch("boolean")
  def readInt(): Int = value match
    case CNumber(lex) => Integer.parseInt(lex)
    case _ => mismatch("int")
  def readLong(): Long = value match
    case CNumber(lex) => java.lang.Long.parseLong(lex)
    case _ => mismatch("long")
  def readDouble(): Double = value match
    case CNumber(lex) => lex.toDouble
    case _ => mismatch("double")
  def readBigInt(): BigInt = value match
    case CNumber(lex) =>
      if lex.contains('.') || lex.contains('e') || lex.contains('E') then BigDecimal(lex).toBigInt else BigInt(lex)
    case _ => mismatch("bigint")
  def readBigDecimal(): BigDecimal = value match
    case CNumber(lex) => BigDecimal(lex)
    case _ => mismatch("bigdecimal")
  def readList(): ListInput = value match
    case CList(items) => new CapturedListInput(items)
    case _ => mismatch("list")
  def readObject(): ObjectInput = value match
    case CObject(fields) => new ReplayObjectInput(fields)
    case _ => mismatch("object")
  def skip(): Unit = ()

final class CapturedListInput(items: Vector[CapturedValue]) extends ListInput:
  private var idx = 0
  def hasNext: Boolean = idx < items.length
  def nextElement(): Input =
    val v = items(idx)
    idx += 1
    new CapturedInput(v)

final class CapturedFieldInput(val fieldName: String, value: CapturedValue) extends CapturedInput(value), FieldInput

final class ReplayObjectInput(fields: Vector[(String, CapturedValue)]) extends ObjectInput:
  private var idx = 0
  def hasNext: Boolean = idx < fields.length
  def nextField(): FieldInput =
    val (name, v) = fields(idx)
    idx += 1
    new CapturedFieldInput(name, v)
  def getNextNamedField(name: String): FieldInput =
    while idx < fields.length do
      val (n, v) = fields(idx)
      idx += 1
      if n == name then return new CapturedFieldInput(n, v)
    throw ReadFailure(s"missing field: $name")
