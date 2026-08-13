package halotukozak.mcodec

trait Output:
  def writeNull(): Unit
  def writeSimple(): SimpleOutput
  def writeList(): ListOutput
  def writeObject(): ObjectOutput
  def hasMarker(marker: Marker): Boolean = false

trait SimpleOutput:
  def writeString(str: String): Unit
  def writeBoolean(b: Boolean): Unit
  def writeInt(i: Int): Unit
  def writeLong(l: Long): Unit
  def writeDouble(d: Double): Unit
  def writeBigInt(b: BigInt): Unit
  def writeBigDecimal(b: BigDecimal): Unit

  def writeByte(b: Byte): Unit = writeInt(b.toInt)

  def writeShort(s: Short): Unit = writeInt(s.toInt)

  def writeChar(c: Char): Unit = writeString(c.toString)

  def writeFloat(f: Float): Unit = writeDouble(f.toDouble)

  def writeTimestamp(millis: Long): Unit = writeLong(millis)

  def writeBinary(bytes: Array[Byte]): Unit =
    // PLATFORM-SEAM: java.util.Base64 (JVM-only; replace behind a platform seam in cross-build Phase F)
    writeString(java.util.Base64.getEncoder.nn.encodeToString(bytes).nn)

trait SequentialOutput:
  def finish(): Unit
  def declareSize(size: Int): Unit = ()
  def knownSize: Int = -1
  def hasMarker(marker: Marker): Boolean = false

trait ListOutput extends SequentialOutput:
  def writeElement(): Output

trait ObjectOutput extends SequentialOutput:
  def writeField(key: String): Output

trait OutputAndSimpleOutput extends Output, SimpleOutput:
  final def writeSimple(): SimpleOutput = this
