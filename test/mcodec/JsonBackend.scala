package halotukozak.mcodec

object JsonBackend extends Backend:
  type Repr = String
  def output(): (Output, () => Repr) =
    val sb = new java.lang.StringBuilder
    (new JsonOutput(sb), () => sb.toString)
  def input(repr: Repr): Input = new JsonInput(new JsonReader(repr))
