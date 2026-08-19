package mcodec

final class BsonReader(bytes: Array[Byte]):
  private var pos = 0

  private[mcodec] def position: Int = pos

  private[mcodec] def u8(): Int =
    if pos >= bytes.length then throw ReadFailure("unexpected end of BSON input")
    val b = bytes(pos) & 0xff
    pos += 1
    b

  private[mcodec] def peekU8(): Int =
    if pos >= bytes.length then throw ReadFailure("unexpected end of BSON input")
    bytes(pos) & 0xff

  private[mcodec] def readBytesN(n: Int): Array[Byte] =
    if n < 0 || pos + n > bytes.length then throw ReadFailure("unexpected end of BSON input")
    val out = new Array[Byte](n)
    System.arraycopy(bytes, pos, out, 0, n)
    pos += n
    out

  private[mcodec] def readInt32LE(): Int =
    val b = readBytesN(4)
    (b(0) & 0xff) | ((b(1) & 0xff) << 8) | ((b(2) & 0xff) << 16) | ((b(3) & 0xff) << 24)

  private[mcodec] def readInt64LE(): Long =
    val b = readBytesN(8)
    var acc = 0L
    var i = 0
    while i < 8 do
      acc = acc | ((b(i).toLong & 0xffL) << (8 * i))
      i += 1
    acc

  private[mcodec] def readDoubleLE(): Double = java.lang.Double.longBitsToDouble(readInt64LE())

  private[mcodec] def readCString(): String =
    val start = pos
    while pos < bytes.length && bytes(pos) != 0 do pos += 1
    if pos >= bytes.length then throw ReadFailure("unterminated BSON cstring")
    val s = new String(bytes, start, pos - start, "UTF-8")
    pos += 1 // skip NUL
    s

  private[mcodec] def readBsonString(): String =
    val len = readInt32LE()
    if len < 1 then throw ReadFailure(s"invalid BSON string length: $len")
    val body = readBytesN(len - 1)
    if u8() != 0 then throw ReadFailure("BSON string not NUL-terminated")
    new String(body, "UTF-8")

  private[mcodec] def finishTopLevel(): Unit =
    if pos < bytes.length then throw ReadFailure("trailing data after top-level BSON document")

/** Opportunistic capability for BSON-native scalar types with no cross-format equivalent. */
trait BsonExtInput:
  def readObjectId(): Array[Byte]
  def readRegexValue(): (String, String)
  def readJsCode(): String
  def readMinKey(): Unit
  def readMaxKey(): Unit

/** The true top level of a BSON stream — only a document is legal here (see `BsonRootOutput`). */
final class BsonRootInput(reader: BsonReader) extends Input:
  // Every NullSafeCodec.read probes readNull() unconditionally before dispatching to the
  // shape-specific read — a BSON document itself is never the null sentinel, so this must
  // report false rather than throw (unlike readSimple/readList, which really are illegal here).
  def readNull(): Boolean = false
  def readSimple(): SimpleInput = throw ReadFailure("BSON requires a document at the top level")
  def readList(): ListInput = throw ReadFailure("BSON requires a document at the top level")
  def readObject(): ObjectInput = new BsonObjectInput(reader)
  def skip(): Unit = readObject().skipRemaining()

final class BsonObjectInput(reader: BsonReader) extends ObjectInput:
  private var started = false
  private var startPos = 0
  private var declaredLen = 0
  private var ended = false

  private def ensureHeader(): Unit =
    if !started then
      startPos = reader.position
      declaredLen = reader.readInt32LE()
      started = true

  def hasNext: Boolean =
    ensureHeader()
    if ended then false
    else if reader.peekU8() == 0x00 then
      reader.u8()
      ended = true
      val consumed = reader.position - startPos
      if consumed != declaredLen then
        throw ReadFailure(s"BSON document length mismatch: declared $declaredLen, actual $consumed")
      false
    else true

  def nextField(): FieldInput =
    ensureHeader()
    val tag = reader.u8()
    val name = reader.readCString()
    new BsonFieldInput(reader, name, tag)

  def getNextNamedField(name: String): FieldInput =
    val f = nextField()
    if f.fieldName == name then f
    else throw ReadFailure(s"expected field '$name' but got '${f.fieldName}'")

// A BSON array is byte-identical to a document (decimal-string keys "0","1",...); reuse the
// object reader wholesale and simply discard the (index) names.
final class BsonListInput(reader: BsonReader) extends ListInput:
  private val objIn = new BsonObjectInput(reader)
  def hasNext: Boolean = objIn.hasNext
  def nextElement(): Input = objIn.nextField()

class BsonValueInput(reader: BsonReader, tag: Int) extends InputAndSimpleInput, BsonExtInput:
  private def mismatch(expected: String): Nothing =
    throw ReadFailure(f"expected BSON $expected, got type tag 0x$tag%02x")

  def readNull(): Boolean = tag == 0x0a

  def readList(): ListInput =
    if tag != 0x04 then mismatch("array")
    new BsonListInput(reader)

  def readObject(): ObjectInput =
    if tag != 0x03 then mismatch("document")
    new BsonObjectInput(reader)

  def readString(): String =
    if tag != 0x02 then mismatch("string")
    reader.readBsonString()

  def readBoolean(): Boolean =
    if tag != 0x08 then mismatch("boolean")
    reader.u8() != 0

  def readInt(): Int =
    if tag != 0x10 then mismatch("int32")
    reader.readInt32LE()

  def readLong(): Long =
    if tag != 0x12 then mismatch("int64")
    reader.readInt64LE()

  def readDouble(): Double =
    if tag != 0x01 then mismatch("double")
    reader.readDoubleLE()

  // Also accepts the native int32/int64 tags (not just our string fallback): the backend-neutral
  // flat-sum/@outOfOrder capture path (Replay.scala) reads ANY numeric field generically via
  // readBigDecimal/readBigInt regardless of the field's real static type, exactly as CBOR's do.
  def readBigInt(): BigInt = tag match
    case 0x10 => BigInt(reader.readInt32LE())
    case 0x12 => BigInt(reader.readInt64LE())
    case 0x02 => BigInt(reader.readBsonString())
    case _ => mismatch("int32/int64/string (BigInt fallback)")

  def readBigDecimal(): BigDecimal = tag match
    case 0x01 => BigDecimal(reader.readDoubleLE())
    case 0x10 => BigDecimal(reader.readInt32LE())
    case 0x12 => BigDecimal(reader.readInt64LE())
    case 0x02 => BigDecimal(reader.readBsonString())
    case _ => mismatch("double/int32/int64/string (BigDecimal fallback)")

  override def readBinary(): Array[Byte] =
    if tag != 0x05 then mismatch("binary")
    val len = reader.readInt32LE()
    reader.u8() // subtype, ignored
    reader.readBytesN(len)

  override def readTimestamp(): Long =
    if tag != 0x09 then mismatch("UTC datetime")
    reader.readInt64LE()

  def readObjectId(): Array[Byte] =
    if tag != 0x07 then mismatch("ObjectId")
    reader.readBytesN(12)

  def readRegexValue(): (String, String) =
    if tag != 0x0b then mismatch("regex")
    (reader.readCString(), reader.readCString())

  def readJsCode(): String =
    if tag != 0x0d then mismatch("JS code")
    reader.readBsonString()

  def readMinKey(): Unit = if tag != 0xff then mismatch("MinKey")
  def readMaxKey(): Unit = if tag != 0x7f then mismatch("MaxKey")

  override def peekKind(): InputKind = tag match
    case 0x0a => InputKind.Null
    case 0x08 => InputKind.Boolean
    case 0x01 | 0x10 | 0x12 => InputKind.Number
    case 0x02 => InputKind.String
    case 0x03 => InputKind.Object
    case 0x04 => InputKind.List
    // binary/ObjectId/regex/JS/MinKey/MaxKey etc. have no natural bucket; approximate as String,
    // matching CBOR's choice for its own no-natural-bucket major types.
    case _ => InputKind.String

  def skip(): Unit = BsonValueInput.skipPayload(reader, tag)

object BsonValueInput:
  private[mcodec] def skipPayload(reader: BsonReader, tag: Int): Unit = tag match
    case 0x01 => reader.readBytesN(8) // double
    case 0x02 => val len = reader.readInt32LE(); reader.readBytesN(len) // string, NUL included in len
    case 0x03 => new BsonObjectInput(reader).skipRemaining()
    case 0x04 => new BsonListInput(reader).skipRemaining()
    case 0x05 => val len = reader.readInt32LE(); reader.u8(); reader.readBytesN(len)
    case 0x07 => reader.readBytesN(12)
    case 0x08 => reader.u8()
    case 0x09 => reader.readBytesN(8)
    case 0x0a => ()
    case 0x0b => reader.readCString(); reader.readCString()
    case 0x0d => val len = reader.readInt32LE(); reader.readBytesN(len)
    case 0x10 => reader.readBytesN(4)
    case 0x12 => reader.readBytesN(8)
    case 0x7f | 0xff => ()
    case other => throw ReadFailure(f"cannot skip unknown BSON type tag 0x$other%02x")

final class BsonFieldInput(reader: BsonReader, val fieldName: String, tag: Int)
  extends BsonValueInput(reader, tag), FieldInput
