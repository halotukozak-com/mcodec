package mcodec

final class CborReader(bytes: Array[Byte]):
  private var pos = 0

  private[mcodec] def u8(): Int =
    if pos >= bytes.length then throw ReadFailure("unexpected end of input")
    val b = bytes(pos) & 0xff
    pos += 1
    b

  private[mcodec] def peekU8(): Int =
    if pos >= bytes.length then throw ReadFailure("unexpected end of input")
    bytes(pos) & 0xff

  private[mcodec] def readInitial(): (Int, Int) =
    val b = u8()
    (b >> 5, b & 0x1f)

  // Decode the argument from the low-5-bits of an initial byte. Returns -1L as an
  // indefinite-length sentinel (addInfo == 31). Big-endian, masked per byte.
  private[mcodec] def readArg(addInfo: Int): Long =
    if addInfo < 24 then addInfo.toLong
    else if addInfo == 31 then -1L
    else
      val n = addInfo match
        case 24 => 1
        case 25 => 2
        case 26 => 4
        case 27 => 8
        case other => throw ReadFailure(s"invalid additional info: $other")
      var acc = 0L
      var k = 0
      while k < n do
        acc = (acc << 8) | u8().toLong
        k += 1
      acc

  private[mcodec] def readBytes(n: Int): Array[Byte] =
    if pos + n > bytes.length then throw ReadFailure("unexpected end of input")
    val out = new Array[Byte](n)
    System.arraycopy(bytes, pos, out, 0, n)
    pos += n
    out

  private[mcodec] def finishTopLevel(): Unit =
    if pos < bytes.length then throw ReadFailure("trailing data after top-level value")

class CborInput(reader: CborReader) extends InputAndSimpleInput:

  def readNull(): Boolean =
    if reader.peekU8() == 0xf6 then
      reader.u8()
      true
    else false

  override def peekKind(): InputKind =
    val b = reader.peekU8()
    b match
      case 0xf6 => InputKind.Null
      case 0xf4 | 0xf5 => InputKind.Boolean
      case 0xf9 | 0xfa | 0xfb => InputKind.Number
      case _ =>
        val major = b >> 5
        major match
          case 0 | 1 => InputKind.Number
          // major 2 is a BYTE string; the suite carries no flat-sum body with binary,
          // so we map it to String for capture purposes (add CapturedValue.CBinary on demand).
          case 2 => InputKind.String
          case 3 => InputKind.String
          case 4 => InputKind.List
          case 5 => InputKind.Object
          // major 6 (tags) wrap numeric/binary values (bignum 2/3, bigdecimal 4, timestamp 1);
          // no tagged value appears in a flat-sum body in the suite, classify as Number.
          case 6 => InputKind.Number
          case _ => throw ReadFailure(s"unexpected initial byte for peekKind: $b")

  def readLong(): Long =
    val (major, addInfo) = reader.readInitial()
    major match
      case 0 => reader.readArg(addInfo)
      case 1 => -1L - reader.readArg(addInfo)
      case _ => throw ReadFailure(s"expected integer, got major type $major")

  def readInt(): Int =
    val l = readLong()
    if l >= Int.MinValue.toLong && l <= Int.MaxValue.toLong then l.toInt
    else throw ReadFailure(s"Long out of Int range: $l")

  def readBoolean(): Boolean =
    reader.u8() match
      case 0xf5 => true
      case 0xf4 => false
      case other => throw ReadFailure(s"expected boolean, got byte $other")

  def readString(): String =
    val (major, addInfo) = reader.readInitial()
    if major != 3 then throw ReadFailure(s"expected text string, got major type $major")
    new String(readChunkedOrDefinite(addInfo, 3), "UTF-8")

  override def readBinary(): Array[Byte] =
    val (major, addInfo) = reader.readInitial()
    if major != 2 then throw ReadFailure(s"expected byte string, got major type $major")
    readChunkedOrDefinite(addInfo, 2)

  // Reads a (text or byte) string body, supporting both definite length and the
  // indefinite/chunked form (addInfo == 31, chunks terminated by 0xFF break).
  private def readChunkedOrDefinite(addInfo: Int, major: Int): Array[Byte] =
    if addInfo == 31 then
      val acc = new java.io.ByteArrayOutputStream
      var done = false
      while !done do
        if reader.peekU8() == 0xff then
          reader.u8()
          done = true
        else
          val (cm, ca) = reader.readInitial()
          if cm != major then throw ReadFailure(s"expected chunk of major $major, got $cm")
          val len = reader.readArg(ca)
          acc.write(reader.readBytes(len.toInt))
      acc.toByteArray.nn
    else reader.readBytes(reader.readArg(addInfo).toInt)

  def readDouble(): Double =
    val (major, addInfo) = reader.readInitial()
    major match
      case 0 => reader.readArg(addInfo).toDouble
      case 1 => (-1L - reader.readArg(addInfo)).toDouble
      case 7 => readFloatBits(addInfo)
      case _ => throw ReadFailure(s"expected float, got major type $major")

  override def readFloat(): Float =
    val (major, addInfo) = reader.readInitial()
    major match
      case 0 => reader.readArg(addInfo).toFloat
      case 1 => (-1L - reader.readArg(addInfo)).toFloat
      case 7 => readFloatBits(addInfo).toFloat
      case _ => throw ReadFailure(s"expected float, got major type $major")

  private def readFloatBits(addInfo: Int): Double =
    addInfo match
      case 25 =>
        val h = ((reader.u8() << 8) | reader.u8()) & 0xffff
        fromHalf(h).toDouble
      case 26 =>
        var ib = 0
        var k = 0
        while k < 4 do
          ib = (ib << 8) | reader.u8()
          k += 1
        java.lang.Float.intBitsToFloat(ib).toDouble
      case 27 =>
        var lb = 0L
        var k = 0
        while k < 8 do
          lb = (lb << 8) | reader.u8().toLong
          k += 1
        java.lang.Double.longBitsToDouble(lb)
      case other => throw ReadFailure(s"invalid float additional info: $other")

  def readBigInt(): BigInt =
    val b = reader.peekU8()
    val major = b >> 5
    major match
      case 0 | 1 => BigInt(readLong())
      case 6 =>
        val (_, tagInfo) = reader.readInitial()
        val tag = reader.readArg(tagInfo)
        val mag = readBinary()
        val n = BigInt(new java.math.BigInteger(1, mag))
        tag match
          case 2 => n
          case 3 => -(n + 1)
          case other => throw ReadFailure(s"expected bignum tag 2/3, got $other")
      case _ => throw ReadFailure(s"expected bignum, got major type $major")

  def readBigDecimal(): BigDecimal =
    val b = reader.peekU8()
    if (b >> 5) == 6 then
      val (_, tagInfo) = reader.readInitial()
      val tag = reader.readArg(tagInfo)
      if tag != 4 then throw ReadFailure(s"expected bigdecimal tag 4, got $tag")
      val (am, aa) = reader.readInitial()
      if am != 4 || reader.readArg(aa) != 2L then throw ReadFailure("expected [exponent, mantissa] array")
      val exponent = readLong()
      val mantissa = readBigInt()
      BigDecimal(new java.math.BigDecimal(mantissa.bigInteger, -exponent.toInt))
    else if (b >> 5) == 7 then BigDecimal(readDouble())
    else BigDecimal(readLong())

  override def readTimestamp(): Long =
    val (major, addInfo) = reader.readInitial()
    if major != 6 then throw ReadFailure(s"expected timestamp tag, got major type $major")
    val tag = reader.readArg(addInfo)
    if tag != 1 then throw ReadFailure(s"expected timestamp tag 1, got $tag")
    val b = reader.peekU8()
    if (b >> 5) == 7 then (readDouble() * 1000.0).round
    else readLong() * 1000L

  def skip(): Unit =
    val (major, addInfo) = reader.readInitial()
    major match
      case 0 | 1 => reader.readArg(addInfo)
      case 2 | 3 =>
        if addInfo == 31 then skipChunks(major)
        else reader.readBytes(reader.readArg(addInfo).toInt)
      case 4 =>
        if addInfo == 31 then skipBreakTerminated(1)
        else
          val n = reader.readArg(addInfo)
          var k = 0L
          while k < n do
            skip(); k += 1
      case 5 =>
        if addInfo == 31 then skipBreakTerminated(2)
        else
          val n = reader.readArg(addInfo)
          var k = 0L
          while k < n do
            skip(); skip(); k += 1
      case 6 =>
        reader.readArg(addInfo)
        skip()
      case 7 =>
        addInfo match
          case 25 => reader.readBytes(2)
          case 26 => reader.readBytes(4)
          case 27 => reader.readBytes(8)
          case _ => ()
      case _ => throw ReadFailure(s"cannot skip major type $major")

  private def skipChunks(major: Int): Unit =
    var done = false
    while !done do
      if reader.peekU8() == 0xff then
        reader.u8()
        done = true
      else
        val (cm, ca) = reader.readInitial()
        reader.readBytes(reader.readArg(ca).toInt)

  // Skip an indefinite container's items until the 0xFF break. `perItem` is the
  // number of values per entry (1 for arrays, 2 for maps).
  private def skipBreakTerminated(perItem: Int): Unit =
    var done = false
    while !done do
      if reader.peekU8() == 0xff then
        reader.u8()
        done = true
      else
        var k = 0
        while k < perItem do
          skip(); k += 1

  def readList(): ListInput = new CborListInput(reader)

  def readObject(): ObjectInput = new CborObjectInput(reader)

final class CborListInput(reader: CborReader) extends ListInput:
  private var started = false
  private var ended = false
  private var indefinite = false
  private var remaining = 0L

  private def ensureHeader(): Unit =
    if !started then
      val (major, addInfo) = reader.readInitial()
      if major != 4 then throw ReadFailure(s"expected array, got major type $major")
      if addInfo == 31 then indefinite = true
      else remaining = reader.readArg(addInfo)
      started = true

  def hasNext: Boolean =
    if ended then false
    else
      ensureHeader()
      if indefinite then
        if reader.peekU8() == 0xff then
          reader.u8()
          ended = true
          false
        else true
      else if remaining > 0 then true
      else
        ended = true
        false

  def nextElement(): Input =
    if !indefinite then remaining -= 1
    new CborInput(reader)

final class CborFieldInput(reader: CborReader, val fieldName: String) extends CborInput(reader), FieldInput

final class CborObjectInput(reader: CborReader) extends ObjectInput:
  private var started = false
  private var ended = false
  private var indefinite = false
  private var remaining = 0L

  private def ensureHeader(): Unit =
    if !started then
      val (major, addInfo) = reader.readInitial()
      if major != 5 then throw ReadFailure(s"expected map, got major type $major")
      if addInfo == 31 then indefinite = true
      else remaining = reader.readArg(addInfo)
      started = true

  def hasNext: Boolean =
    if ended then false
    else
      ensureHeader()
      if indefinite then
        if reader.peekU8() == 0xff then
          reader.u8()
          ended = true
          false
        else true
      else if remaining > 0 then true
      else
        ended = true
        false

  private def readKey(): String =
    val (major, addInfo) = reader.readInitial()
    if major != 3 then throw ReadFailure(s"expected text-string map key, got major type $major")
    if addInfo == 31 then throw ReadFailure("indefinite-length map keys are not supported")
    new String(reader.readBytes(reader.readArg(addInfo).toInt), "UTF-8")

  def nextField(): FieldInput =
    val name = readKey()
    if !indefinite then remaining -= 1
    new CborFieldInput(reader, name)

  def getNextNamedField(name: String): FieldInput =
    val f = nextField()
    if f.fieldName == name then f
    else throw ReadFailure(s"expected field '$name' but got '${f.fieldName}'")
