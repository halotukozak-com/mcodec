package mcodec

import halotukozak.made.annotation.transparent

class TransparentAnnotTest extends munit.FunSuite, JsonConv:
  @transparent case class Email(value: String) derives MCodec

  test("@transparent serializes as the bare wrapped value (no envelope)"):
    assertEquals(Email("a@b.com").toJson, "\"a@b.com\"")

  test("@transparent round-trips"):
    assertEquals(MCodec[Email].read(JsonBackend.input("\"a@b.com\"")), Email("a@b.com"))
