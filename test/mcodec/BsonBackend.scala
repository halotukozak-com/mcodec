package mcodec

object BsonBackend extends Backend:
  type Repr = Array[Byte]
  def output(): (Output, () => Repr) =
    val buf = new BsonBuffer
    (new BsonRootOutput(buf), () => buf.toByteArray.nn)
  def input(repr: Repr): Input = new BsonRootInput(new BsonReader(repr))
