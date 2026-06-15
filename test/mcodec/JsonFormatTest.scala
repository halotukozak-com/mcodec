package mcodec

import mcodec.MKeyCodec.given

class JsonFormatTest extends munit.FunSuite, JsonConv:

  test("Either encodes to tagged single-field object"):
    assertEquals(toJson(Left(5): Either[Int, String]), "{\"Left\":5}")
    assertEquals(toJson(Right("x"): Either[Int, String]), "{\"Right\":\"x\"}")
    assertEquals(fromJson[Either[Int, String]]("{\"Left\":5}"), Left(5))
    assertEquals(fromJson[Either[Int, String]]("{\"Right\":\"x\"}"), Right("x"))

  test("Map with string key encodes to JSON object"):
    assertEquals(toJson(Map("a" -> 1)), "{\"a\":1}")
    assertEquals(fromJson[Map[String, Int]]("{\"a\":1}"), Map("a" -> 1))

  test("Map with non-string key encodes to array of pairs"):
    val s = toJson(Map(List(1) -> 2))
    assert(s.startsWith("["), s)
    assertEquals(s, "[[[1],2]]")
    assertEquals(fromJson[Map[List[Int], Int]](s), Map(List(1) -> 2))

  test("Option encodes to value or null"):
    assertEquals(toJson(None: Option[Int]), "null")
    assertEquals(toJson(Some(7): Option[Int]), "7")
    assertEquals(fromJson[Option[Int]]("null"), None)
    assertEquals(fromJson[Option[Int]]("7"), Some(7))

  test("empty collections encode to [] and {}"):
    assertEquals(toJson(List.empty[Int]), "[]")
    assertEquals(toJson(Map.empty[String, Int]), "{}")
    assertEquals(fromJson[List[Int]]("[]"), Nil)
    assertEquals(fromJson[Map[String, Int]]("{}"), Map.empty[String, Int])

  test("tuple encodes to JSON array"):
    assertEquals(toJson((1, "a")), "[1,\"a\"]")
    assertEquals(fromJson[(Int, String)]("[1,\"a\"]"), (1, "a"))
    assertEquals(toJson((1, "a", true)), "[1,\"a\",true]")
    assertEquals(fromJson[(Int, String, Boolean)]("[1,\"a\",true]"), (1, "a", true))

  test("string escaping is correct end-to-end"):
    assertEquals(toJson("a\"b\n"), "\"a\\\"b\\n\"")
    assertEquals(fromJson[String]("\"a\\\"b\\n\""), "a\"b\n")
    assertEquals(fromJson[String](toJson("😀")), "😀")

  test("Int encodes without a fractional part"):
    assertEquals(toJson(1), "1")
    assertEquals(fromJson[Int]("1"), 1)

  test("Long precision survives full JSON text round-trip"):
    assertEquals(toJson(Long.MaxValue), "9223372036854775807")
    assertEquals(fromJson[Long](toJson(Long.MaxValue)), Long.MaxValue)
    assertEquals(fromJson[Long](toJson(9007199254740993L)), 9007199254740993L)

  test("BigDecimal precision survives full JSON text round-trip"):
    assertEquals(toJson(BigDecimal("0.1")), "0.1")
    assertEquals(fromJson[BigDecimal](toJson(BigDecimal("0.1"))), BigDecimal("0.1"))

  test("huge BigInt survives full JSON text round-trip"):
    val big = BigInt("123456789012345678901234567890")
    assertEquals(toJson(big), "123456789012345678901234567890")
    assertEquals(fromJson[BigInt](toJson(big)), big)
