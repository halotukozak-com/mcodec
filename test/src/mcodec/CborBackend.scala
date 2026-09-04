package halotukozak.mcodec

object CborBackend extends Backend:
  type Repr = Array[Byte]
  def output(): (Output, () => Repr) =
    val baos = new java.io.ByteArrayOutputStream
    (new CborOutput(baos), () => baos.toByteArray.nn)
  def input(repr: Repr): Input = new CborInput(new CborReader(repr))
