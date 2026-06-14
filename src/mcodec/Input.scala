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
