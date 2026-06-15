package mcodec

class NullableTest extends munit.FunSuite, JsonConv:
  given MCodec[String | Null] = MCodec[String].nullable

  test("nullable writes explicit wire-null for null"):
    assertEquals(toJson[String | Null](null), "null")

  test("nullable delegates write for non-null"):
    assertEquals(toJson[String | Null]("x"), "\"x\"")

  test("nullable reads wire-null as null"):
    assertEquals(fromJson[String | Null]("null"), null)

  test("nullable delegates read for non-null"):
    assertEquals(fromJson[String | Null]("\"x\""), "x")
