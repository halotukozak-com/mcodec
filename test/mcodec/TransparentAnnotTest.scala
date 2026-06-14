package mcodec

import made.annotation.transparent

class TransparentAnnotTest extends munit.FunSuite:
  @transparent case class Email(value: String) derives MCodec

  private def toJson[T: MCodec as codec](x: T): String =
    val (out, harvest) = JsonBackend.output()
    codec.write(out, x)
    harvest()

  test("@transparent serializes as the bare wrapped value (no envelope)"):
    assertEquals(toJson(Email("a@b.com")), "\"a@b.com\"")

  test("@transparent round-trips"):
    assertEquals(MCodec[Email].read(JsonBackend.input("\"a@b.com\"")), Email("a@b.com"))
