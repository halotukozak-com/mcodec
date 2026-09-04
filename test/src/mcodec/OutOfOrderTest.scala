package halotukozak.mcodec

import halotukozak.mcodec.annotation.outOfOrder

object OutOfOrderFixtures:
  case class OOO(@outOfOrder tag: String, value: Int) derives MCodec

class OutOfOrderTest extends munit.FunSuite, JsonConv:
  import OutOfOrderFixtures.*

  test("reordered JSON input reads correctly (tag after value)"):
    assertEquals(fromJson[OOO]("""{"value":5,"tag":"hello"}"""), OOO("hello", 5))

  test("normally-ordered input still reads"):
    assertEquals(fromJson[OOO]("""{"tag":"hi","value":1}"""), OOO("hi", 1))

  test("REGRESSION: write order stays declaration order (tag first)"):
    assertEquals(OOO("hi", 1).toJson[OOO], """{"tag":"hi","value":1}""")

  test("REGRESSION: declareSize unchanged by @outOfOrder"):
    val (_, sizes) = RecordingBackend.declaredSizes(OOO("hi", 1))
    assertEquals(sizes.head, 2)
