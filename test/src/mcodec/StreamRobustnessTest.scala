package halotukozak.mcodec

import halotukozak.mcodec.MKeyCodec.given

class StreamRobustnessTest extends munit.FunSuite, JsonConv, CborConv:

  // A. Full consumption / trailing-byte rejection (BOTH backends).
  test("CBOR trailing bytes after top-level value rejected"):
    intercept[ReadFailure](fromCborHex[Int]("00FF"))

  test("JSON trailing data after top-level value rejected"):
    val rf = intercept[ReadFailure](fromJson[Int]("1 2"))
    assert(rf.getMessage.contains("trailing data"), s"got ${rf.getMessage}")

  test("JSON trailing whitespace is allowed"):
    assertEquals(fromJson[Int]("1   "), 1)

  // B. Indefinite / chunked CBOR accepted on read (read-only; write always emits definite).
  test("CBOR indefinite array accepted"):
    assertEquals(fromCborHex[List[Int]]("9F010203FF"), List(1, 2, 3))

  test("CBOR indefinite map accepted"):
    assertEquals(fromCborHex[Map[String, Int]]("BF616101616202616303FF"), Map("a" -> 1, "b" -> 2, "c" -> 3))

  test("CBOR chunked text string accepted"):
    assertEquals(fromCborHex[String]("7F626162626162626162FF"), "ababab")

  test("CBOR chunked byte string accepted"):
    assertEquals(fromCborHex[Bytes]("5F426162426162426162FF"), Bytes("ababab"))

  // C. Truncation / exhaustion detected -> ReadFailure, NOT SIOOBE/IndexOutOfBounds.
  test("CBOR truncated definite array -> ReadFailure (declares 3, supplies 1)"):
    val rf = intercept[ReadFailure](fromCborHex[List[Int]]("8301"))
    assert(rf.getMessage.contains("unexpected end of input"), s"got ${rf.getMessage}")

  test("CBOR truncated int header -> ReadFailure"):
    // 0x19 = uint16 header but no following 2 bytes
    intercept[ReadFailure](fromCborHex[Int]("19"))

  test("JSON unterminated container -> ReadFailure (not SIOOBE)"):
    intercept[ReadFailure](fromJson[List[Int]]("[1,2"))

  test("JSON empty input -> ReadFailure (not SIOOBE)"):
    intercept[ReadFailure](fromJson[Int](""))
