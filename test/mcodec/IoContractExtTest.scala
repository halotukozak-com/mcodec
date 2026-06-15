package mcodec

import MValue.*

class IoContractExtTest extends munit.FunSuite:

  // ---- InMemory harness ----
  private def imHarvest(write: SimpleOutput => Unit): MValue =
    val (out, get) = InMemoryBackend.output()
    write(out.writeSimple())
    get()

  private def imRead[A](v: MValue)(read: SimpleInput => A): A =
    InMemoryBackend.input(v).readSimple().pipeRead(read)

  extension (in: SimpleInput) private def pipeRead[A](read: SimpleInput => A): A = read(in)

  // ---- JSON harness ----
  private def jsonHarvest(write: SimpleOutput => Unit): String =
    val (out, get) = JsonBackend.output()
    write(out.writeSimple())
    get()

  private def jsonRead[A](s: String)(read: SimpleInput => A): A =
    JsonBackend.input(s).readSimple().pipeRead(read)

  // ===== IOX-01: Byte =====
  test("IOX-01: Byte round-trips via InMemory at boundaries"):
    for b <- Seq(Byte.MinValue, (-1).toByte, 0.toByte, 1.toByte, Byte.MaxValue) do
      assertEquals(imHarvest(_.writeByte(b)), MInt(b.toInt))
      assertEquals(imRead(MInt(b.toInt))(_.readByte()), b)

  test("IOX-01: Byte round-trips via JSON at boundaries"):
    for b <- Seq(Byte.MinValue, (-1).toByte, 0.toByte, 1.toByte, Byte.MaxValue) do
      val s = jsonHarvest(_.writeByte(b))
      assertEquals(jsonRead(s)(_.readByte()), b)

  test("IOX-01: readByte rejects Int out of Byte range (no silent wrap)"):
    intercept[ReadFailure](imRead(MInt(300))(_.readByte()))
    intercept[ReadFailure](imRead(MInt(-200))(_.readByte()))

  // ===== IOX-01: Short =====
  test("IOX-01: Short round-trips via InMemory at boundaries"):
    for sh <- Seq(Short.MinValue, (-1).toShort, 0.toShort, 1.toShort, Short.MaxValue) do
      assertEquals(imHarvest(_.writeShort(sh)), MInt(sh.toInt))
      assertEquals(imRead(MInt(sh.toInt))(_.readShort()), sh)

  test("IOX-01: Short round-trips via JSON at boundaries"):
    for sh <- Seq(Short.MinValue, (-1).toShort, 0.toShort, 1.toShort, Short.MaxValue) do
      val s = jsonHarvest(_.writeShort(sh))
      assertEquals(jsonRead(s)(_.readShort()), sh)

  test("IOX-01: readShort rejects Int out of Short range"):
    intercept[ReadFailure](imRead(MInt(40000))(_.readShort()))
    intercept[ReadFailure](imRead(MInt(-40000))(_.readShort()))

  // ===== IOX-01: Char =====
  test("IOX-01: Char round-trips via InMemory as 1-char string"):
    assertEquals(imHarvest(_.writeChar('a')), MString("a"))
    assertEquals(imRead(MString("a"))(_.readChar()), 'a')

  test("IOX-01: Char JSON wire-shape is a 1-char quoted string"):
    assertEquals(jsonHarvest(_.writeChar('a')), "\"a\"")
    assertEquals(jsonRead("\"z\"")(_.readChar()), 'z')

  test("IOX-01: readChar rejects a string whose length != 1"):
    intercept[ReadFailure](imRead(MString("ab"))(_.readChar()))
    intercept[ReadFailure](imRead(MString(""))(_.readChar()))

  // ===== IOX-01: Float precision =====
  private val floatCases =
    Seq(Float.MinValue, Float.MaxValue, Float.MinPositiveValue, -0.0f, 0.0f, 1.1f, 1.4e-45f, 3.14159f)

  test("IOX-01: Float round-trips EXACTLY via InMemory"):
    for f <- floatCases do assertEquals(imRead(imHarvest(_.writeFloat(f)))(_.readFloat()), f)

  test("IOX-01: Float round-trips EXACTLY via JSON (lexeme, not via Double)"):
    for f <- floatCases do
      val s = jsonHarvest(_.writeFloat(f))
      assertEquals(jsonRead(s)(_.readFloat()), f)

  test("IOX-01: Float JSON does not drift through Double for 1.1f"):
    val s = jsonHarvest(_.writeFloat(1.1f))
    assertEquals(jsonRead(s)(_.readFloat()), 1.1f)
    assert(!s.contains("1.100000023"), s"Float drifted through Double: $s")

  test("IOX-01: Float -0.0f preserves sign bit"):
    val back = jsonRead(jsonHarvest(_.writeFloat(-0.0f)))(_.readFloat())
    assertEquals(java.lang.Float.floatToIntBits(back), java.lang.Float.floatToIntBits(-0.0f))

  test("IOX-01: Float NaN/Infinity write quoted in JSON and round-trip"):
    val nan = jsonRead(jsonHarvest(_.writeFloat(Float.NaN)))(_.readFloat())
    assert(java.lang.Float.isNaN(nan))
    val pinf = jsonRead(jsonHarvest(_.writeFloat(Float.PositiveInfinity)))(_.readFloat())
    assert(pinf.isPosInfinity)
    val ninf = jsonRead(jsonHarvest(_.writeFloat(Float.NegativeInfinity)))(_.readFloat())
    assert(ninf.isNegInfinity)
    assert(jsonHarvest(_.writeFloat(Float.NaN)).startsWith("\""))

  // ===== IOX-02: Timestamp =====
  private val tsCases = Seq(0L, 1L, 1L << 50, Long.MaxValue, -1000L)

  test("IOX-02: Timestamp round-trips via InMemory"):
    for t <- tsCases do assertEquals(imRead(imHarvest(_.writeTimestamp(t)))(_.readTimestamp()), t)

  test("IOX-02: Timestamp round-trips via JSON"):
    for t <- tsCases do assertEquals(jsonRead(jsonHarvest(_.writeTimestamp(t)))(_.readTimestamp()), t)

  test("IOX-02: Timestamp JSON wire-shape is a bare number (not a string)"):
    assertEquals(jsonHarvest(_.writeTimestamp(0L)), "0")
    assertEquals(jsonHarvest(_.writeTimestamp(1750000000000L)), "1750000000000")

  // ===== IOX-03: Binary =====
  private val binCases: Seq[Array[Byte]] =
    Seq(
      Array.empty[Byte],
      Array[Byte](1, 2, 3),
      Array[Byte](0, -1, 127, -128),
      Array.tabulate(10000)(i => (i % 256 - 128).toByte),
    )

  test("IOX-03: Binary round-trips via InMemory (sameElements)"):
    for b <- binCases do assert(imRead(imHarvest(_.writeBinary(b)))(_.readBinary()).sameElements(b))

  test("IOX-03: Binary round-trips via JSON (sameElements)"):
    for b <- binCases do assert(jsonRead(jsonHarvest(_.writeBinary(b)))(_.readBinary()).sameElements(b))

  test("IOX-03: Binary InMemory wire-shape is an MString (base64 default routes through writeString)"):
    imHarvest(_.writeBinary(Array[Byte](1, 2, 3))) match
      case MString(_) => ()
      case other => fail(s"expected MString base64, got $other")

  test("IOX-03: Binary JSON wire-shape is a quoted base64 string"):
    val s = jsonHarvest(_.writeBinary(Array[Byte](0, 1, 2)))
    assert(s.startsWith("\"") && s.endsWith("\""), s"expected quoted base64, got $s")
    val decoded = java.util.Base64.getDecoder.nn.decode(s.substring(1, s.length - 1)).nn
    assert(decoded.sameElements(Array[Byte](0, 1, 2)))
