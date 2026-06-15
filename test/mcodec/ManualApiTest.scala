package mcodec

class ManualApiTest extends munit.FunSuite, JsonConv:

  case class Wrap(n: Int)

  test("MANUAL-01 create builds a working codec that round-trips"):
    given MCodec[Wrap] =
      MCodec.create[Wrap](in => Wrap(MCodec.read[Int](in)), (out, w) => MCodec.write(out, w.n))
    assertEquals(toJson(Wrap(5)), "5")
    assertEquals(fromJson[Wrap]("5"), Wrap(5))

  test("MANUAL-02 transform round-trips (existing API)"):
    given MCodec[Wrap] = MCodec[Int].transform[Wrap](_.n, Wrap(_))
    assertEquals(toJson(Wrap(5)), "5")
    assertEquals(fromJson[Wrap]("5"), Wrap(5))

  test("MANUAL-02 transformed round-trips (read-first, NEW)"):
    given MCodec[Wrap] = MCodec[Int].transformed[Wrap](Wrap(_))(_.n)
    assertEquals(toJson(Wrap(5)), "5")
    assertEquals(fromJson[Wrap]("5"), Wrap(5))
