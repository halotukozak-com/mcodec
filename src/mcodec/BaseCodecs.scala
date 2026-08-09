package mcodec

trait NullSafeCodec[T] extends MCodec[T]:
  def readNonNull(input: Input): T
  def writeNonNull(output: Output, value: T): Unit

  final def read(input: Input): T =
    if input.readNull() then throw ReadFailure("null is not allowed here")
    else readNonNull(input)

  final def write(output: Output, value: T): Unit = writeNonNull(output, value)

trait SimpleCodec[T] extends NullSafeCodec[T]:
  def readSimple(input: SimpleInput): T
  def writeSimple(output: SimpleOutput, value: T): Unit
  final def readNonNull(input: Input): T = readSimple(input.readSimple())
  final def writeNonNull(output: Output, value: T): Unit = writeSimple(output.writeSimple(), value)

trait ListCodec[T] extends NullSafeCodec[T]:
  def readList(input: ListInput): T
  def writeList(output: ListOutput, value: T): Unit
  final def readNonNull(input: Input): T =
    val li = input.readList()
    val r = readList(li)
    li.skipRemaining()
    r
  final def writeNonNull(output: Output, value: T): Unit =
    val lo = output.writeList()
    writeList(lo, value)
    lo.finish()

/** A codec able to report the exact number of elements/fields it will write for a value. */
trait SizedCodec[T]:
  def sizeOf(value: T): Int

trait ObjectCodec[T] extends NullSafeCodec[T]:
  def readObject(input: ObjectInput): T
  def writeObject(output: ObjectOutput, value: T): Unit
  final def readNonNull(input: Input): T =
    val oi = input.readObject()
    val r = readObject(oi)
    oi.skipRemaining()
    r
  final def writeNonNull(output: Output, value: T): Unit =
    val oo = output.writeObject()
    writeObject(oo, value)
    oo.finish()
