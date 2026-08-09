package mcodec

import java.io.ByteArrayOutputStream

private def writeMajor(out: ByteArrayOutputStream, major: Int, arg: Long): Unit =
  val head = major << 5
  if java.lang.Long.compareUnsigned(arg, 24L) < 0 then out.write(head | arg.toInt)
  else if java.lang.Long.compareUnsigned(arg, 0xffL) <= 0 then
    out.write(head | 24)
    out.write((arg & 0xff).toInt)
  else if java.lang.Long.compareUnsigned(arg, 0xffffL) <= 0 then
    out.write(head | 25)
    out.write(((arg >>> 8) & 0xff).toInt)
    out.write((arg & 0xff).toInt)
  else if java.lang.Long.compareUnsigned(arg, 0xffffffffL) <= 0 then
    out.write(head | 26)
    var s = 24
    while s >= 0 do
      out.write(((arg >>> s) & 0xff).toInt)
      s -= 8
  else
    out.write(head | 27)
    var s = 56
    while s >= 0 do
      out.write(((arg >>> s) & 0xff).toInt)
      s -= 8

// binary16 pack: returns 16-bit half encoding of a binary32 float (round-to-nearest not required —
// callers only emit half when fromHalf(toHalf(f)) == f bit-exactly, so a truncating mantissa is fine).
private def toHalf(f: Float): Int =
  val bits = java.lang.Float.floatToIntBits(f)
  val sign = (bits >>> 16) & 0x8000
  val exp = (bits >>> 23) & 0xff
  val mant = bits & 0x7fffff
  if exp == 0xff then
    // Inf / NaN
    if mant == 0 then sign | 0x7c00
    else sign | 0x7e00
  else
    val unbiased = exp - 127
    if unbiased > 15 then sign | 0x7c00 // overflow -> Inf (will fail round-trip unless truly Inf)
    else if unbiased < -14 then
      // subnormal or underflow to zero in half
      val shift = -unbiased - 14 // >= 1
      if shift > 24 then sign
      else
        val m = (mant | 0x800000) >>> (shift + 13)
        sign | m
    else
      val halfExp = (unbiased + 15) << 10
      val halfMant = mant >>> 13
      sign | halfExp | halfMant

private def fromHalf(h: Int): Float =
  val sign = (h & 0x8000) << 16
  val exp = (h >>> 10) & 0x1f
  val mant = h & 0x3ff
  val bits =
    if exp == 0 then
      if mant == 0 then sign // +/- 0
      else
        // subnormal half -> normalized single
        var e = -14
        var m = mant
        while (m & 0x400) == 0 do
          m <<= 1
          e -= 1
        m &= 0x3ff
        sign | ((e + 127) << 23) | (m << 13)
    else if exp == 0x1f then sign | 0x7f800000 | (mant << 13) // Inf / NaN
    else sign | ((exp - 15 + 127) << 23) | (mant << 13)
  java.lang.Float.intBitsToFloat(bits)

final class CborOutput(out: ByteArrayOutputStream) extends OutputAndSimpleOutput:

  def writeNull(): Unit = out.write(0xf6)

  def writeBoolean(b: Boolean): Unit = out.write(if b then 0xf5 else 0xf4)

  def writeLong(l: Long): Unit =
    if l >= 0 then writeMajor(out, 0, l)
    else writeMajor(out, 1, -(l + 1))

  def writeInt(i: Int): Unit = writeLong(i.toLong)

  def writeString(s: String): Unit =
    val bytes = s.getBytes("UTF-8").nn
    writeMajor(out, 3, bytes.length.toLong)
    out.write(bytes)

  override def writeBinary(bytes: Array[Byte]): Unit =
    writeMajor(out, 2, bytes.length.toLong)
    out.write(bytes)

  def writeDouble(d: Double): Unit =
    if d.isNaN then
      out.write(0xf9); out.write(0x7e); out.write(0x00)
    else if java.lang.Double.doubleToRawLongBits(d) == 0L then writeMajor(out, 0, 0L) // +0.0 -> 00
    else encodeFloatShortest(d)

  override def writeFloat(f: Float): Unit = writeDouble(f.toDouble)

  private def encodeFloatShortest(d: Double): Unit =
    if d.isNaN then
      out.write(0xf9); out.write(0x7e); out.write(0x00)
    else
      val single = d.toFloat
      if single.toDouble == d then
        val half = toHalf(single)
        if fromHalf(half) == single &&
          java.lang.Float.floatToIntBits(fromHalf(half)) == java.lang.Float.floatToIntBits(single)
        then
          out.write(0xf9)
          out.write((half >>> 8) & 0xff)
          out.write(half & 0xff)
        else
          out.write(0xfa)
          val ib = java.lang.Float.floatToIntBits(single)
          var s = 24
          while s >= 0 do
            out.write((ib >>> s) & 0xff)
            s -= 8
      else
        out.write(0xfb)
        val lb = java.lang.Double.doubleToLongBits(d)
        var s = 56
        while s >= 0 do
          out.write(((lb >>> s) & 0xff).toInt)
          s -= 8

  def writeBigInt(b: BigInt): Unit =
    if b.isValidLong then writeLong(b.toLong)
    else
      val (tag, n) = if b.signum >= 0 then (0xc2, b) else (0xc3, (-b) - 1)
      out.write(tag)
      val mag = n.toByteArray
      val trimmed = if mag.length > 1 && mag(0) == 0 then mag.drop(1) else mag
      writeMajor(out, 2, trimmed.length.toLong)
      out.write(trimmed)

  def writeBigDecimal(b: BigDecimal): Unit =
    out.write(0xc4) // tag 4
    out.write(0x82) // array of length 2
    writeLong(-b.scale.toLong) // exponent = -scale
    writeBigInt(BigInt(b.bigDecimal.unscaledValue.nn)) // mantissa

  override def writeTimestamp(millis: Long): Unit =
    out.write(0xc1) // tag 1
    if millis % 1000L == 0 then writeLong(millis / 1000L)
    else
      out.write(0xfb)
      val lb = java.lang.Double.doubleToLongBits(millis.toDouble / 1000.0)
      var s = 56
      while s >= 0 do
        out.write(((lb >>> s) & 0xff).toInt)
        s -= 8

  def writeList(): ListOutput = new CborListOutput(out)

  def writeObject(): ObjectOutput = new CborObjectOutput(out)

final class CborListOutput(out: ByteArrayOutputStream) extends ListOutput:
  private var declared = -1
  private var headerWritten = false
  override def declareSize(size: Int): Unit = declared = size
  private def ensureHeader(): Unit =
    if !headerWritten then
      if declared >= 0 then writeMajor(out, 4, declared.toLong)
      else out.write(0x9f)
      headerWritten = true
  def writeElement(): Output =
    ensureHeader()
    new CborOutput(out)
  def finish(): Unit =
    ensureHeader()
    if declared < 0 then out.write(0xff)

final class CborObjectOutput(out: ByteArrayOutputStream) extends ObjectOutput:
  private var declared = -1
  private var headerWritten = false
  override def declareSize(size: Int): Unit = declared = size
  private def ensureHeader(): Unit =
    if !headerWritten then
      if declared >= 0 then writeMajor(out, 5, declared.toLong)
      else out.write(0xbf)
      headerWritten = true
  def writeField(key: String): Output =
    ensureHeader()
    new CborOutput(out).writeString(key)
    new CborOutput(out)
  def finish(): Unit =
    ensureHeader()
    if declared < 0 then out.write(0xff)
