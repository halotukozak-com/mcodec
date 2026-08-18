//> using target.platform jvm

package halotukozak.mcodec

// Scala.js represents Float as a Double under the hood (no native 32-bit float
// type), so it doesn't preserve exact Float bit patterns: values can drift
// through Double precision, and -0.0f loses its sign bit. Both are genuine
// Scala.js runtime limitations, not bugs in this codebase — kept JVM-only.
// See https://github.com/halotukozak-com/mcodec/issues/23
class IoContractExtJvmOnlyTest extends munit.FunSuite:

  private def jsonHarvest(write: SimpleOutput => Unit): String =
    val (out, get) = JsonBackend.output()
    write(out.writeSimple())
    get()

  private def jsonRead[A](s: String)(read: SimpleInput => A): A =
    JsonBackend.input(s).readSimple().pipeRead(read)

  extension (in: SimpleInput) private def pipeRead[A](read: SimpleInput => A): A = read(in)

  test("Float JSON does not drift through Double for 1.1f"):
    val s = jsonHarvest(_.writeFloat(1.1f))
    assertEquals(jsonRead(s)(_.readFloat()), 1.1f)
    assert(!s.contains("1.100000023"), s"Float drifted through Double: $s")

  test("Float -0.0f preserves sign bit"):
    val back = jsonRead(jsonHarvest(_.writeFloat(-0.0f)))(_.readFloat())
    assertEquals(java.lang.Float.floatToIntBits(back), java.lang.Float.floatToIntBits(-0.0f))
