package mcodec

import MValue.*

class IoContractTest extends munit.FunSuite:

  given intCodec: MCodec[Int] = new SimpleCodec[Int]:
    def readSimple(in: SimpleInput): Int = in.readInt()
    def writeSimple(out: SimpleOutput, v: Int): Unit = out.writeInt(v)

  given stringCodec: MCodec[String] = new SimpleCodec[String]:
    def readSimple(in: SimpleInput): String = in.readString()
    def writeSimple(out: SimpleOutput, v: String): Unit = out.writeString(v)

  final case class Pair(a: Int, b: String)
  given pairCodec: MCodec[Pair] = new ObjectCodec[Pair]:
    def writeObject(o: ObjectOutput, v: Pair): Unit =
      intCodec.write(o.writeField("a"), v.a)
      stringCodec.write(o.writeField("b"), v.b)
    def readObject(i: ObjectInput): Pair =
      Pair(intCodec.read(i.getNextNamedField("a")), stringCodec.read(i.getNextNamedField("b")))

  private def harvest[T: MCodec as codec](v: T): MValue =
    val (out, get) = InMemoryBackend.output()
    codec.write(out, v)
    get()

  // CORE-01: Output writes null / simple / list / object

  test("CORE-01: writeNull produces MNull"):
    val (out, get) = InMemoryBackend.output()
    out.writeNull()
    assertEquals(get(), MNull)

  test("CORE-01: writeSimple int produces MInt"):
    assertEquals(harvest(42), MInt(42))

  test("CORE-01: writeList via writeElement produces MList"):
    val (out, get) = InMemoryBackend.output()
    val lo = out.writeList()
    intCodec.write(lo.writeElement(), 1)
    intCodec.write(lo.writeElement(), 2)
    lo.finish()
    assertEquals(get(), MList(Vector(MInt(1), MInt(2))))

  test("CORE-01: writeObject via writeField+finish produces ordered MObj"):
    assertEquals(harvest(Pair(7, "x")), MObj(Vector("a" -> MInt(7), "b" -> MString("x"))))

  // CORE-02: Input reads simple, iterates fields/elements, skips

  test("CORE-02: readSimple reads back an int"):
    assertEquals(intCodec.read(InMemoryBackend.input(MInt(9))), 9)

  test("CORE-02: nextElement/hasNext iterate a list"):
    val li = InMemoryBackend.input(MList(Vector(MInt(1), MInt(2), MInt(3)))).readList()
    val buf = Vector.newBuilder[Int]
    while li.hasNext do buf += intCodec.read(li.nextElement())
    assertEquals(buf.result(), Vector(1, 2, 3))

  test("CORE-02: nextField iterates object fields in order"):
    val oi = InMemoryBackend.input(MObj(Vector("a" -> MInt(1), "b" -> MString("y")))).readObject()
    val f1 = oi.nextField()
    assertEquals(f1.fieldName, "a")
    assertEquals(intCodec.read(f1), 1)
    val f2 = oi.nextField()
    assertEquals(f2.fieldName, "b")
    assertEquals(stringCodec.read(f2), "y")
    assert(!oi.hasNext)

  test("CORE-02: peekField finds a field by name, returns None when absent"):
    val oi = InMemoryBackend.input(MObj(Vector("a" -> MInt(1)))).readObject()
    assert(oi.peekField("a").isDefined)
    assertEquals(intCodec.read(oi.peekField("a").get), 1)
    assertEquals(oi.peekField("missing"), None)

  test("CORE-02: skip over an unknown nested field leaves stream readable (Pitfall B)"):
    val fields = Vector(
      "skipme" -> MObj(Vector("deep" -> MList(Vector(MInt(99))))),
      "a" -> MInt(1),
      "b" -> MString("z"),
    )
    val p = pairCodec.read(InMemoryBackend.input(MObj(fields)))
    assertEquals(p, Pair(1, "z"))

  // CORE-03 is covered by RoundTripStubTest (backend-generic property).

  // CORE-04 (Pitfall A): one reused codec instance, two distinct values

  test("CORE-04: reuse one codec instance for two distinct values"):
    val r1 = pairCodec.read(InMemoryBackend.input(harvest(Pair(1, "x"))))
    val r2 = pairCodec.read(InMemoryBackend.input(harvest(Pair(2, "y"))))
    assertEquals(r1, Pair(1, "x"))
    assertEquals(r2, Pair(2, "y"))

  test("CORE-04: same value round-trips twice identically (no state leak)"):
    val a = pairCodec.read(InMemoryBackend.input(harvest(Pair(5, "k"))))
    val b = pairCodec.read(InMemoryBackend.input(harvest(Pair(5, "k"))))
    assertEquals(a, b)

  // CORE-05: hand-written ObjectCodec on the base helpers round-trips end-to-end

  test("CORE-05: ObjectCodec[Pair] round-trips via base finish/skipRemaining"):
    assertEquals(pairCodec.read(InMemoryBackend.input(harvest(Pair(3, "q")))), Pair(3, "q"))

  // ERR-01: ReadFailure / WriteFailure

  test("ERR-01: a failing write throws WriteFailure"):
    val boom = new SimpleCodec[Int]:
      def readSimple(in: SimpleInput): Int = in.readInt()
      def writeSimple(out: SimpleOutput, v: Int): Unit = throw WriteFailure("boom")
    intercept[WriteFailure](boom.write(InMemoryBackend.output()._1, 1))

  test("ERR-01: reading a wire null for a non-null codec throws ReadFailure"):
    intercept[ReadFailure](intCodec.read(InMemoryBackend.input(MNull)))

  test("ERR-01: type-mismatch read throws ReadFailure"):
    intercept[ReadFailure](intCodec.read(InMemoryBackend.input(MString("not an int"))))

  // Pitfall B: shuffled field order

  test("Pitfall B: shuffled-field-order object round-trips"):
    val shuffled = MObj(Vector("b" -> MString("w"), "a" -> MInt(8)))
    assertEquals(pairCodec.read(InMemoryBackend.input(shuffled)), Pair(8, "w"))

  // Pitfall C: null-vs-absent are distinct paths

  test("Pitfall C: writeNull round-trips as null detection"):
    val (out, get) = InMemoryBackend.output()
    out.writeNull()
    assert(InMemoryBackend.input(get()).readNull())

  test("Pitfall C: omitted field is observably absent, distinct from null"):
    val onlyA = MObj(Vector("a" -> MInt(1)))
    val oi = InMemoryBackend.input(onlyA).readObject()
    assertEquals(oi.peekField("b"), None)
    assert(!InMemoryBackend.input(MObj(Vector("a" -> MInt(1)))).readNull())
