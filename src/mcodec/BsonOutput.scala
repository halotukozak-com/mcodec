package halotukozak.mcodec

import java.io.ByteArrayOutputStream

// Growable buffer that additionally allows patching a previously-reserved int32 length
// field once the byte count it describes becomes known (BSON documents/arrays are prefixed
// by their own total length). `buf`/`count` are inherited protected fields of the JDK class.
private[mcodec] final class BsonBuffer extends ByteArrayOutputStream:
  def position: Int = count
  def patchInt32LE(at: Int, value: Int): Unit =
    buf(at) = (value & 0xff).toByte
    buf(at + 1) = ((value >>> 8) & 0xff).toByte
    buf(at + 2) = ((value >>> 16) & 0xff).toByte
    buf(at + 3) = ((value >>> 24) & 0xff).toByte

private def writeInt32LE(buf: BsonBuffer, i: Int): Unit =
  buf.write(i & 0xff)
  buf.write((i >>> 8) & 0xff)
  buf.write((i >>> 16) & 0xff)
  buf.write((i >>> 24) & 0xff)

private def writeInt64LE(buf: BsonBuffer, l: Long): Unit =
  var s = 0
  while s < 64 do
    buf.write(((l >>> s) & 0xffL).toInt)
    s += 8

private def writeDoubleLE(buf: BsonBuffer, d: Double): Unit =
  writeInt64LE(buf, java.lang.Double.doubleToLongBits(d))

// cstring: raw UTF-8 bytes + trailing NUL. Must not itself contain an embedded NUL byte.
private def writeCString(buf: BsonBuffer, s: String): Unit =
  val bytes = s.getBytes("UTF-8").nn
  if bytes.contains(0.toByte) then throw WriteFailure(s"BSON cstring must not contain a NUL byte: $s")
  buf.write(bytes)
  buf.write(0)

// BSON "string": int32 byte-length (including the trailing NUL) + UTF-8 bytes + trailing NUL.
private def writeBsonString(buf: BsonBuffer, s: String): Unit =
  val bytes = s.getBytes("UTF-8").nn
  writeInt32LE(buf, bytes.length + 1)
  buf.write(bytes)
  buf.write(0)

/** Opportunistic capability for BSON-native scalar types with no cross-format equivalent. */
trait BsonExtOutput:
  def writeObjectId(bytes: Array[Byte]): Unit
  def writeRegexValue(pattern: String, options: String): Unit
  def writeJsCode(code: String): Unit
  def writeMinKey(): Unit
  def writeMaxKey(): Unit

/**
 * The true top level of a BSON stream. Per the BSON spec every encoded value is a document
 * (there is no bare top-level scalar/array), so only `writeObject()` is legal here.
 */
final class BsonRootOutput(buf: BsonBuffer) extends Output:
  def writeNull(): Unit = throw WriteFailure("BSON requires a document at the top level, got null")
  def writeSimple(): SimpleOutput = throw WriteFailure("BSON requires a document at the top level, got a scalar value")
  def writeList(): ListOutput = throw WriteFailure("BSON requires a document at the top level, got a list/array")
  def writeObject(): ObjectOutput = new BsonObjectOutput(buf)

// Shared document-body framing (int32 length placeholder, elements, trailing NUL, then patch
// the length once known) used identically by top-level and nested documents, and by arrays
// (a BSON array is byte-identical to a document, keyed by decimal-string index).
private def openDocument(buf: BsonBuffer): Int =
  val start = buf.position
  writeInt32LE(buf, 0)
  start

private def closeDocument(buf: BsonBuffer, start: Int): Unit =
  buf.write(0)
  buf.patchInt32LE(start, buf.position - start)

final class BsonObjectOutput(buf: BsonBuffer) extends ObjectOutput:
  private val start = openDocument(buf)
  def writeField(key: String): Output = new BsonValueOutput(buf, key)
  def finish(): Unit = closeDocument(buf, start)

final class BsonListOutput(buf: BsonBuffer) extends ListOutput:
  private val start = openDocument(buf)
  private var idx = 0
  def writeElement(): Output =
    val key = idx.toString
    idx += 1
    new BsonValueOutput(buf, key)
  def finish(): Unit = closeDocument(buf, start)

/**
 * A field/element value position. Unlike CBOR, a BSON element is `<type-tag><cstring name><payload>`
 * — the tag precedes the name, but we only learn the type once the codec calls one of the `write*`
 * methods below. So the tag + name are emitted lazily, on first write.
 */
final class BsonValueOutput(buf: BsonBuffer, name: String) extends OutputAndSimpleOutput, BsonExtOutput:
  private def tag(t: Int): Unit =
    buf.write(t)
    writeCString(buf, name)

  def writeNull(): Unit = tag(0x0a)

  def writeList(): ListOutput =
    tag(0x04)
    new BsonListOutput(buf)

  def writeObject(): ObjectOutput =
    tag(0x03)
    new BsonObjectOutput(buf)

  def writeString(s: String): Unit =
    tag(0x02)
    writeBsonString(buf, s)

  def writeBoolean(b: Boolean): Unit =
    tag(0x08)
    buf.write(if b then 1 else 0)

  def writeInt(i: Int): Unit =
    tag(0x10)
    writeInt32LE(buf, i)

  def writeLong(l: Long): Unit =
    tag(0x12)
    writeInt64LE(buf, l)

  def writeDouble(d: Double): Unit =
    tag(0x01)
    writeDoubleLE(buf, d)

  // BSON has no arbitrary-precision numeric type (only int32/int64/double/Decimal128); both
  // fall back to a BSON string (type 0x02), lossless for any magnitude/scale.
  def writeBigInt(b: BigInt): Unit = writeString(b.toString)
  def writeBigDecimal(b: BigDecimal): Unit = writeString(b.toString)

  override def writeBinary(bytes: Array[Byte]): Unit =
    tag(0x05)
    writeInt32LE(buf, bytes.length)
    buf.write(0x00) // generic binary subtype
    buf.write(bytes)

  // BSON UTC datetime (0x09): int64 milliseconds since the epoch — matches our epoch-millis contract exactly.
  override def writeTimestamp(millis: Long): Unit =
    tag(0x09)
    writeInt64LE(buf, millis)

  def writeObjectId(bytes: Array[Byte]): Unit =
    tag(0x07)
    buf.write(bytes)

  def writeRegexValue(pattern: String, options: String): Unit =
    tag(0x0b)
    writeCString(buf, pattern)
    writeCString(buf, options)

  def writeJsCode(code: String): Unit =
    tag(0x0d)
    writeBsonString(buf, code)

  def writeMinKey(): Unit = tag(0xff)
  def writeMaxKey(): Unit = tag(0x7f)
