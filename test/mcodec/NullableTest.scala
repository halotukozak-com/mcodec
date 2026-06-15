package mcodec

class NullableTest extends munit.FunSuite, JsonConv:
  given MCodec[String | Null] = MCodec[String].nullable

  test("nullable writes explicit wire-null for null"):
    assertEquals(null.toJson[String | Null], "null")

  test("nullable delegates write for non-null"):
    assertEquals("x".toJson[String | Null], "\"x\"")

  test("nullable reads wire-null as null"):
    assertEquals(fromJson[String | Null]("null"), null)

  test("nullable delegates read for non-null"):
    assertEquals(fromJson[String | Null]("\"x\""), "x")
