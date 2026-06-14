package mcodec

trait Output:
  def writeNull(): Unit
  def writeSimple(): SimpleOutput
  def writeList(): ListOutput
  def writeObject(): ObjectOutput

trait SimpleOutput:
  def writeString(str: String): Unit
  def writeBoolean(b: Boolean): Unit
  def writeInt(i: Int): Unit
  def writeLong(l: Long): Unit
  def writeDouble(d: Double): Unit
  def writeBigInt(b: BigInt): Unit
  def writeBigDecimal(b: BigDecimal): Unit

trait SequentialOutput:
  def finish(): Unit
  def declareSize(size: Int): Unit = ()
  def knownSize: Int = -1

trait ListOutput extends SequentialOutput:
  def writeElement(): Output

trait ObjectOutput extends SequentialOutput:
  def writeField(key: String): Output

trait OutputAndSimpleOutput extends Output, SimpleOutput:
  final def writeSimple(): SimpleOutput = this
