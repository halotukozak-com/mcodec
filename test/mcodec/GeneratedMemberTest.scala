package halotukozak.mcodec

import made.annotation.{generated, name}

case class WithGen(x: Int) derives MCodec:
  @generated def doubled: Int = x * 2

case class WithGenNamed(x: Int) derives MCodec:
  @generated @name("twice") def doubled: Int = x * 2

class GeneratedMemberTest extends munit.FunSuite, JsonConv:
  test("@generated member emitted on write"):
    assertEquals(WithGen(3).toJson, "{\"x\":3,\"doubled\":6}")

  test("@generated field skipped on read; constructor value round-trips"):
    assertEquals(fromJson[WithGen]("{\"x\":3,\"doubled\":6}"), WithGen(3))

  test("@generated absent on read is fine (not a constructor slot)"):
    assertEquals(fromJson[WithGen]("{\"x\":3}"), WithGen(3))

  test("@name overrides the generated label on write"):
    assert(WithGenNamed(3).toJson.contains("\"twice\":6"), WithGenNamed(3).toJson)
