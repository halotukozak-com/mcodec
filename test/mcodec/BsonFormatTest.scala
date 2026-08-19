package mcodec

import mcodec.MKeyCodec.given

case class BsonWrap[T](v: T) derives MCodec
case class Point(x: Int, y: Int) derives MCodec

class BsonFormatTest extends munit.FunSuite, BsonConv, JsonConv:
  given MCodec[Int | Null] = MCodec[Int].nullable

  // ---- BS1: a document is the only legal top-level shape ----
  test("BS1 scalar/list at the true top level is rejected on write"):
    intercept[WriteFailure](0.toBsonHex[Int])
    intercept[WriteFailure](List(1, 2, 3).toBsonHex[List[Int]])
    intercept[WriteFailure]("hi".toBsonHex[String])

  test("BS1 scalar/list at the true top level is rejected on read"):
    intercept[ReadFailure](fromBsonHex[Int]("01000000"))
    intercept[ReadFailure](fromBsonHex[List[Int]]("01000000"))

  test("BS1 an object-shaped value writes and reads as a top-level document"):
    assertEquals(fromBsonHex[Point](Point(1, 2).toBsonHex[Point]), Point(1, 2))

  // ---- BS2: exact wire fidelity (hand-verified byte layout) ----
  test("BS2 two-Int32-field document"):
    assertEquals(Point(1, 2).toBsonHex[Point], "13000000107800010000001079000200000000")

  test("BS2 single-string-field document (object-or-pairs Map via MKeyCodec)"):
    assertEquals(Map("a" -> "hi").toBsonHex[Map[String, String]], "0F0000000261000300000068690000")

  // ---- BS3: primitive type tags, round-tripped through a wrapping document ----
  test("BS3 null"):
    val wrapped = BsonWrap[Int | Null](null)
    assertEquals(fromBsonHex[BsonWrap[Int | Null]](wrapped.toBsonHex[BsonWrap[Int | Null]]), wrapped)

  test("BS3 boolean"):
    assertEquals(fromBsonHex[BsonWrap[Boolean]](BsonWrap(true).toBsonHex[BsonWrap[Boolean]]), BsonWrap(true))
    assertEquals(fromBsonHex[BsonWrap[Boolean]](BsonWrap(false).toBsonHex[BsonWrap[Boolean]]), BsonWrap(false))

  test("BS3 int32/int64/double"):
    assertEquals(fromBsonHex[BsonWrap[Int]](BsonWrap(Int.MinValue).toBsonHex[BsonWrap[Int]]), BsonWrap(Int.MinValue))
    assertEquals(fromBsonHex[BsonWrap[Long]](BsonWrap(Long.MaxValue).toBsonHex[BsonWrap[Long]]), BsonWrap(Long.MaxValue))
    assertEquals(fromBsonHex[BsonWrap[Double]](BsonWrap(3.14).toBsonHex[BsonWrap[Double]]), BsonWrap(3.14))

  test("BS3 BigInt/BigDecimal fall back to a BSON string"):
    val big = BigInt("123243534645675672342")
    assertEquals(fromBsonHex[BsonWrap[BigInt]](BsonWrap(big).toBsonHex[BsonWrap[BigInt]]), BsonWrap(big))
    val bigD = BigDecimal("3.14159265358979")
    assertEquals(fromBsonHex[BsonWrap[BigDecimal]](BsonWrap(bigD).toBsonHex[BsonWrap[BigDecimal]]), BsonWrap(bigD))

  test("BS3 binary (native BSON binary, subtype 0)"):
    val b = new Bytes(Array[Byte](1, 2, 3, -1, 0))
    assertEquals(fromBsonHex[BsonWrap[Bytes]](BsonWrap(b).toBsonHex[BsonWrap[Bytes]]), BsonWrap(b))

  test("BS3 timestamp (UTC datetime, epoch millis)"):
    val d = new java.util.Date(1547044282001L)
    assertEquals(fromBsonHex[BsonWrap[java.util.Date]](BsonWrap(d).toBsonHex[BsonWrap[java.util.Date]]), BsonWrap(d))

  test("BS3 nested document and array"):
    assertEquals(
      fromBsonHex[BsonWrap[Point]](BsonWrap(Point(3, 4)).toBsonHex[BsonWrap[Point]]),
      BsonWrap(Point(3, 4)),
    )
    assertEquals(
      fromBsonHex[BsonWrap[List[Int]]](BsonWrap(List(1, 2, 3)).toBsonHex[BsonWrap[List[Int]]]),
      BsonWrap(List(1, 2, 3)),
    )

  // ---- BS4: extended native types ----
  test("BS4 ObjectId round-trips natively"):
    val oid = ObjectId.fromHex("507f1f77bcf86cd799439011")
    assertEquals(fromBsonHex[BsonWrap[ObjectId]](BsonWrap(oid).toBsonHex[BsonWrap[ObjectId]]), BsonWrap(oid))

  test("BS4 ObjectId falls back to a 24-char hex string on JSON"):
    val oid = ObjectId.fromHex("507f1f77bcf86cd799439011")
    assertEquals(oid.toJson[ObjectId], "\"507f1f77bcf86cd799439011\"")
    assertEquals(fromJson[ObjectId]("\"507f1f77bcf86cd799439011\""), oid)

  test("BS4 BsonRegex round-trips natively"):
    val re = BsonRegex("^a.*z$", "i")
    assertEquals(fromBsonHex[BsonWrap[BsonRegex]](BsonWrap(re).toBsonHex[BsonWrap[BsonRegex]]), BsonWrap(re))

  test("BS4 BsonRegex falls back to a two-field object on JSON"):
    val re = BsonRegex("^a.*z$", "i")
    assertEquals(re.toJson[BsonRegex], "{\"pattern\":\"^a.*z$\",\"options\":\"i\"}")
    assertEquals(fromJson[BsonRegex]("{\"pattern\":\"^a.*z$\",\"options\":\"i\"}"), re)

  test("BS4 JsCode round-trips natively and falls back to a plain string"):
    val code = JsCode("function() { return 1; }")
    assertEquals(fromBsonHex[BsonWrap[JsCode]](BsonWrap(code).toBsonHex[BsonWrap[JsCode]]), BsonWrap(code))
    assertEquals(code.toJson[JsCode], "\"function() { return 1; }\"")

  test("BS4 MinKey/MaxKey round-trip natively and fall back to literal strings"):
    assertEquals(fromBsonHex[BsonWrap[MinKey.type]](BsonWrap(MinKey).toBsonHex[BsonWrap[MinKey.type]]), BsonWrap(MinKey))
    assertEquals(fromBsonHex[BsonWrap[MaxKey.type]](BsonWrap(MaxKey).toBsonHex[BsonWrap[MaxKey.type]]), BsonWrap(MaxKey))
    assertEquals(MinKey.toJson[MinKey.type], "\"MinKey\"")
    assertEquals(MaxKey.toJson[MaxKey.type], "\"MaxKey\"")

  // ---- BS5: robustness ----
  test("BS5 trailing bytes after a top-level document raise ReadFailure"):
    intercept[ReadFailure](fromBsonHex[Point](Point(1, 2).toBsonHex[Point] + "00"))

  test("BS5 truncated input raises ReadFailure"):
    intercept[ReadFailure](fromBsonHex[Point]("0500"))

  test("BS5 declared-length mismatch raises ReadFailure"):
    // same Point(1,2) document with a corrupted length header (declares 20 instead of 19 bytes)
    intercept[ReadFailure](fromBsonHex[Point]("14000000107800010000001079000200000000"))
