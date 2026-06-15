package mcodec

import mcodec.MKeyCodec.given

class JsonFormatTest extends munit.FunSuite, JsonConv:

  test("Either encodes to tagged single-field object"):
    assertEquals((Left(5): Either[Int, String]).toJson, "{\"Left\":5}")
    assertEquals((Right("x"): Either[Int, String]).toJson, "{\"Right\":\"x\"}")
    assertEquals(fromJson[Either[Int, String]]("{\"Left\":5}"), Left(5))
    assertEquals(fromJson[Either[Int, String]]("{\"Right\":\"x\"}"), Right("x"))

  test("Map with string key encodes to JSON object"):
    assertEquals(Map("a" -> 1).toJson, "{\"a\":1}")
    assertEquals(fromJson[Map[String, Int]]("{\"a\":1}"), Map("a" -> 1))

  test("Map with non-string key encodes to array of pairs"):
    val s = Map(List(1) -> 2).toJson
    assert(s.startsWith("["), s)
    assertEquals(s, "[[[1],2]]")
    assertEquals(fromJson[Map[List[Int], Int]](s), Map(List(1) -> 2))

  test("Option encodes to value or null"):
    assertEquals((None: Option[Int]).toJson, "null")
    assertEquals((Some(7): Option[Int]).toJson, "7")
    assertEquals(fromJson[Option[Int]]("null"), None)
    assertEquals(fromJson[Option[Int]]("7"), Some(7))

  test("empty collections encode to [] and {}"):
    assertEquals(List.empty[Int].toJson, "[]")
    assertEquals(Map.empty[String, Int].toJson, "{}")
    assertEquals(fromJson[List[Int]]("[]"), Nil)
    assertEquals(fromJson[Map[String, Int]]("{}"), Map.empty[String, Int])

  test("tuple encodes to JSON array"):
    assertEquals((1, "a").toJson, "[1,\"a\"]")
    assertEquals(fromJson[(Int, String)]("[1,\"a\"]"), (1, "a"))
    assertEquals((1, "a", true).toJson, "[1,\"a\",true]")
    assertEquals(fromJson[(Int, String, Boolean)]("[1,\"a\",true]"), (1, "a", true))

  test("string escaping is correct end-to-end"):
    assertEquals("a\"b\n".toJson, "\"a\\\"b\\n\"")
    assertEquals(fromJson[String]("\"a\\\"b\\n\""), "a\"b\n")
    assertEquals(fromJson[String]("😀".toJson), "😀")

  test("Int encodes without a fractional part"):
    assertEquals(1.toJson, "1")
    assertEquals(fromJson[Int]("1"), 1)

  test("Long precision survives full JSON text round-trip"):
    assertEquals(Long.MaxValue.toJson, "9223372036854775807")
    assertEquals(fromJson[Long](Long.MaxValue.toJson), Long.MaxValue)
    assertEquals(fromJson[Long](9007199254740993L.toJson), 9007199254740993L)

  test("BigDecimal precision survives full JSON text round-trip"):
    assertEquals(BigDecimal("0.1").toJson, "0.1")
    assertEquals(fromJson[BigDecimal](BigDecimal("0.1").toJson), BigDecimal("0.1"))

  test("huge BigInt survives full JSON text round-trip"):
    val big = BigInt("123456789012345678901234567890")
    assertEquals(big.toJson, "123456789012345678901234567890")
    assertEquals(fromJson[BigInt](big.toJson), big)
