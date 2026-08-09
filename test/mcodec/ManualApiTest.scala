package mcodec

class ManualApiTest extends munit.FunSuite, JsonConv:

  case class Wrap(n: Int)

  test("MANUAL-01 create builds a working codec that round-trips"):
    given MCodec[Wrap] =
      MCodec.create[Wrap](in => Wrap(MCodec.read[Int](in)), (out, w) => MCodec.write(out, w.n))
    assertEquals(Wrap(5).toJson, "5")
    assertEquals(fromJson[Wrap]("5"), Wrap(5))

  test("MANUAL-02 transform round-trips (existing API)"):
    given MCodec[Wrap] = MCodec[Int].transform[Wrap](_.n, Wrap(_))
    assertEquals(Wrap(5).toJson, "5")
    assertEquals(fromJson[Wrap]("5"), Wrap(5))

  test("MANUAL-02 transformed round-trips (read-first, NEW)"):
    given MCodec[Wrap] = MCodec[Int].transformed[Wrap](Wrap(_))(_.n)
    assertEquals(Wrap(5).toJson, "5")
    assertEquals(fromJson[Wrap]("5"), Wrap(5))

  case class Pair(a: Int, b: Int)

  private def emitJson[T](c: MCodec[T], v: T): String =
    val (out, harvest) = JsonBackend.output()
    c.write(out, v)
    harvest()

  test("createSimple round-trips with bare-number wire"):
    given MCodec[Wrap] = MCodec.createSimple[Wrap](in => Wrap(in.readInt()), (out, w) => out.writeInt(w.n))
    assertEquals(Wrap(5).toJson, "5")
    assertEquals(fromJson[Wrap]("5"), Wrap(5))

  test("createList round-trips as a positional list"):
    given MCodec[Pair] = MCodec.createList[Pair](
      in =>
        in.hasNext
        val a = in.nextElement().readSimple().readInt()
        in.hasNext
        val b = in.nextElement().readSimple().readInt()
        Pair(a, b)
      ,
      (out, p) =>
        out.writeElement().writeSimple().writeInt(p.a)
        out.writeElement().writeSimple().writeInt(p.b),
    )
    assertEquals(Pair(1, 2).toJson, "[1,2]")
    assertEquals(fromJson[Pair]("[1,2]"), Pair(1, 2))

  test("createObject round-trips as an object"):
    given MCodec[Wrap] = MCodec.createObject[Wrap](
      in =>
        in.hasNext
        Wrap(in.nextField().readSimple().readInt())
      ,
      (out, w) => out.writeField("n").writeSimple().writeInt(w.n),
    )
    assertEquals(Wrap(5).toJson, "{\"n\":5}")
    assertEquals(fromJson[Wrap]("{\"n\":5}"), Wrap(5))

  test("nonNullString uses a quoted-string wire"):
    given MCodec[Wrap] = MCodec.nonNullString[Wrap](s => Wrap(s.toInt), w => w.n.toString)
    assertEquals(Wrap(5).toJson, "\"5\"")
    assertEquals(fromJson[Wrap]("\"5\""), Wrap(5))

  test("fromKeyCodec serializes value as the QUOTED key string"):
    import mcodec.MKeyCodec.given
    val c = MCodec.fromKeyCodec[Int]
    // intCodec would write `7` (bare number); fromKeyCodec writes "7" (a string)
    assertEquals(emitJson(c, 7), "\"7\"")
    assertEquals(c.read(JsonBackend.input("\"7\"")), 7)
