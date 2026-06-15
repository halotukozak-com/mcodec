package mcodec

// Test-only in-memory Input/Output fixture proving the Plan-01 contract.
// NOT shipped library API. The real backend is JSON.

enum MValue:
  case MNull
  case MString(s: String)
  case MInt(i: Int)
  case MLong(l: Long)
  case MDouble(d: Double)
  case MBool(b: Boolean)
  case MBigInt(b: BigInt)
  case MBigDecimal(b: BigDecimal)
  case MList(items: Vector[MValue])
  case MObj(fields: Vector[(String, MValue)])

import MValue.*

final class InMemoryOutput(sink: MValue => Unit) extends OutputAndSimpleOutput:
  def writeNull(): Unit = sink(MNull)
  def writeString(str: String): Unit = sink(MString(str))
  def writeBoolean(b: Boolean): Unit = sink(MBool(b))
  def writeInt(i: Int): Unit = sink(MInt(i))
  def writeLong(l: Long): Unit = sink(MLong(l))
  def writeDouble(d: Double): Unit = sink(MDouble(d))
  def writeBigInt(b: BigInt): Unit = sink(MBigInt(b))
  def writeBigDecimal(b: BigDecimal): Unit = sink(MBigDecimal(b))
  def writeList(): ListOutput = new InMemoryListOutput(sink)
  def writeObject(): ObjectOutput = new InMemoryObjectOutput(sink)

final class InMemoryListOutput(sink: MValue => Unit) extends ListOutput:
  private val buffer = Vector.newBuilder[MValue]
  def writeElement(): Output = new InMemoryOutput(v => buffer += v)
  def finish(): Unit = sink(MList(buffer.result()))

final class InMemoryObjectOutput(sink: MValue => Unit) extends ObjectOutput:
  private val buffer = Vector.newBuilder[(String, MValue)]
  def writeField(key: String): Output = new InMemoryOutput(v => buffer += (key -> v))
  def finish(): Unit = sink(MObj(buffer.result()))

class InMemoryInput(value: MValue) extends InputAndSimpleInput:
  private def mismatch(expected: String): Nothing =
    throw ReadFailure(s"expected $expected but got $value")

  def readNull(): Boolean = value == MNull
  def readString(): String = value match
    case MString(s) => s
    case _ => mismatch("string")
  def readBoolean(): Boolean = value match
    case MBool(b) => b
    case _ => mismatch("boolean")
  def readInt(): Int = value match
    case MInt(i) => i
    case _ => mismatch("int")
  def readLong(): Long = value match
    case MLong(l) => l
    case MInt(i) => i.toLong
    case _ => mismatch("long")
  def readDouble(): Double = value match
    case MDouble(d) => d
    case _ => mismatch("double")
  def readBigInt(): BigInt = value match
    case MBigInt(b) => b
    case _ => mismatch("bigint")
  def readBigDecimal(): BigDecimal = value match
    case MBigDecimal(b) => b
    case _ => mismatch("bigdecimal")
  def readList(): ListInput = value match
    case MList(items) => new InMemoryListInput(items)
    case _ => mismatch("list")
  def readObject(): ObjectInput = value match
    case MObj(fields) => new InMemoryObjectInput(fields)
    case _ => mismatch("object")
  def skip(): Unit = ()

final class InMemoryListInput(items: Vector[MValue]) extends ListInput:
  private var idx = 0
  def hasNext: Boolean = idx < items.length
  def nextElement(): Input =
    val v = items(idx)
    idx += 1
    new InMemoryInput(v)

final class InMemoryFieldInput(val fieldName: String, value: MValue) extends InMemoryInput(value), FieldInput

final class InMemoryObjectInput(fields: Vector[(String, MValue)]) extends ObjectInput:
  private var idx = 0
  def hasNext: Boolean = idx < fields.length
  def nextField(): FieldInput =
    val (name, v) = fields(idx)
    idx += 1
    new InMemoryFieldInput(name, v)
  override def peekField(name: String): Option[FieldInput] =
    fields.find(_._1 == name).map((n, v) => new InMemoryFieldInput(n, v))
  def getNextNamedField(name: String): FieldInput =
    peekField(name) match
      case Some(fi) => fi
      case None => throw ReadFailure(s"missing field: $name")

trait Backend:
  type Repr
  def output(): (Output, () => Repr)
  def input(repr: Repr): Input

object InMemoryBackend extends Backend:
  type Repr = MValue
  def output(): (Output, () => Repr) =
    var captured: MValue = MNull
    val out = new InMemoryOutput(v => captured = v)
    (out, () => captured)
  def input(repr: Repr): Input = new InMemoryInput(repr)
