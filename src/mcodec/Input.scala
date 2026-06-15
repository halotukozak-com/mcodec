package mcodec

trait Input:
  /** Returns true (and consumes the value) if it is null, otherwise returns false and leaves the cursor. */
  def readNull(): Boolean
  def readSimple(): SimpleInput
  def readList(): ListInput
  def readObject(): ObjectInput
  def skip(): Unit
  final def skipValue(): Unit = skip()

trait SimpleInput:
  def readString(): String
  def readBoolean(): Boolean
  def readInt(): Int
  def readLong(): Long
  def readDouble(): Double
  def readBigInt(): BigInt
  def readBigDecimal(): BigDecimal

  def readByte(): Byte =
    val i = readInt()
    if i >= Byte.MinValue && i <= Byte.MaxValue then i.toByte
    else throw ReadFailure(s"Int out of Byte range: $i")

  def readShort(): Short =
    val i = readInt()
    if i >= Short.MinValue && i <= Short.MaxValue then i.toShort
    else throw ReadFailure(s"Int out of Short range: $i")

  def readChar(): Char =
    val s = readString()
    if s.length == 1 then s.charAt(0)
    else throw ReadFailure(s"expected single-char string, got length ${s.length}")

  def readFloat(): Float = readDouble().toFloat

  def readTimestamp(): Long = readLong()

  def readBinary(): Array[Byte] =
    // PLATFORM-SEAM: java.util.Base64 (JVM-only; replace behind a platform seam in cross-build Phase F)
    java.util.Base64.getDecoder.nn.decode(readString()).nn

trait SequentialInput:
  def hasNext: Boolean
  def skipRemaining(): Unit

trait ListInput extends SequentialInput:
  def nextElement(): Input
  def skipRemaining(): Unit = while hasNext do nextElement().skip()

trait ObjectInput extends SequentialInput:
  def nextField(): FieldInput
  def peekField(name: String): Option[FieldInput] = None
  def getNextNamedField(name: String): FieldInput
  def skipRemaining(): Unit = while hasNext do nextField().skip()

trait FieldInput extends Input:
  def fieldName: String

trait InputAndSimpleInput extends Input, SimpleInput:
  final def readSimple(): SimpleInput = this
