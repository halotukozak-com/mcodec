package halotukozak.mcodec

import halotukozak.mcodec.MKeyCodec.given

class CborFormatTest extends munit.FunSuite, CborConv:

  // ---- B1: null / bool ----
  test("B1 null and booleans"):
    assertEquals(null.toCborHex[Null], "F6")
    assertEquals(true.toCborHex[Boolean], "F5")
    assertEquals(false.toCborHex[Boolean], "F4")

  // ---- B2: integers (width minimization, major 0/1) ----
  test("B2 small integers"):
    assertEquals(0.toCborHex, "00")
    assertEquals(1.toCborHex, "01")
    assertEquals((-1).toCborHex, "20")

  test("B2 byte width"):
    assertEquals(127.toByte.toCborHex[Byte], "187F")
    assertEquals(Byte.MinValue.toCborHex[Byte], "387F")

  test("B2 short width"):
    assertEquals(Short.MaxValue.toCborHex[Short], "197FFF")
    assertEquals(Short.MinValue.toCborHex[Short], "397FFF")

  test("B2 int width"):
    assertEquals(Int.MaxValue.toCborHex, "1A7FFFFFFF")
    assertEquals(Int.MinValue.toCborHex, "3A7FFFFFFF")

  test("B2 long width"):
    assertEquals(Long.MaxValue.toCborHex, "1B7FFFFFFFFFFFFFFF")
    assertEquals(Long.MinValue.toCborHex, "3B7FFFFFFFFFFFFFFF")

  // ---- B3: floats / doubles (half / single / double selection) ----
  test("B3 whole-number float/double collapse to int"):
    assertEquals(0.0f.toCborHex[Float], "00")
    assertEquals(0.0.toCborHex[Double], "00")

  test("B3 half-precision"):
    assertEquals(2.5f.toCborHex[Float], "F94100")
    assertEquals(2.5.toCborHex[Double], "F94100")

  test("B3 infinities collapse to half"):
    assertEquals(Float.NegativeInfinity.toCborHex[Float], "F9FC00")
    assertEquals(Double.NegativeInfinity.toCborHex[Double], "F9FC00")
    assertEquals(Float.PositiveInfinity.toCborHex[Float], "F97C00")
    assertEquals(Double.PositiveInfinity.toCborHex[Double], "F97C00")

  test("B3 single-precision"):
    assertEquals(2.7f.toCborHex[Float], "FA402CCCCD")
    assertEquals(3.14f.toCborHex[Float], "FA4048F5C3")
    assertEquals(Float.MinValue.toCborHex[Float], "FAFF7FFFFF")
    assertEquals(Float.MaxValue.toCborHex[Float], "FA7F7FFFFF")

  test("B3 double-precision"):
    assertEquals(2.7.toCborHex[Double], "FB400599999999999A")
    assertEquals(3.14.toCborHex[Double], "FB40091EB851EB851F")
    assertEquals(Double.MinValue.toCborHex[Double], "FBFFEFFFFFFFFFFFFF")
    assertEquals(Double.MaxValue.toCborHex[Double], "FB7FEFFFFFFFFFFFFF")
    assertEquals(Double.MinPositiveValue.toCborHex[Double], "FB0000000000000001")

  // ---- B4: strings (major 3) vs byte-strings (major 2) ----
  test("B4 strings"):
    assertEquals("".toCborHex[String], "60")
    assertEquals("ab".toCborHex[String], "626162")
    assertEquals("ąć".toCborHex[String], "64C485C487")
    assert(("a" * 30).toCborHex[String].startsWith("781E"))

  test("B4 byte-strings"):
    assertEquals(Bytes("").toCborHex[Bytes], "40")
    assertEquals(Bytes("ab").toCborHex[Bytes], "426162")
    assertEquals(Bytes("ąć").toCborHex[Bytes], "44C485C487")
    assertEquals((new Bytes(Array[Byte](-1, 0, 1))).toCborHex[Bytes], "43FF0001")

  // ---- B5: bignum + timestamp tags ----
  test("B5 bignum tags 2/3"):
    assertEquals(BigInt("123243534645675672342").toCborHex[BigInt], "C24906AE58FDC9BB0BC316")
    assertEquals(BigInt("-123243534645675672342").toCborHex[BigInt], "C34906AE58FDC9BB0BC315")

  test("B5 bigdecimal tag 4"):
    assertEquals(
      BigDecimal("123228383982485398493e38493485").toCborHex[BigDecimal],
      "C4821A024B5D2DC24906AE232A57117A63DD",
    )

  test("B5 timestamp tag 1 (int seconds)"):
    assertEquals(new java.util.Date(1547044282000L).toCborHex[java.util.Date], "C11A5C3605BA")

  test("B5 timestamp tag 1 (double seconds)"):
    assertEquals(new java.util.Date(1547044282001L).toCborHex[java.util.Date], "C1FB41D70D816E801062")
    assertEquals(new java.util.Date(1547044282500L).toCborHex[java.util.Date], "C1FB41D70D816EA00000")

  // ---- B6: collections / maps (definite vs indefinite — declareSize proof) ----
  test("B6 definite arrays"):
    assertEquals(Vector(1, 2, 3).toCborHex[Vector[Int]], "83010203")
    assertEquals(List(1, 2, 3).toCborHex[List[Int]], "83010203")
    assertEquals(("foo", 42, 3.14).toCborHex[(String, Int, Double)], "8363666F6F182AFB40091EB851EB851F")

  test("B6 definite map"):
    assertEquals(Map("a" -> 1, "b" -> 2, "c" -> 3).toCborHex[Map[String, Int]], "A3616101616202616303")

  // ---- read-side robustness (Pitfall 6: indefinite / chunked acceptance) ----
  test("read indefinite array"):
    assertEquals(fromCborHex[List[Int]]("9F010203FF"), List(1, 2, 3))

  test("read indefinite map"):
    assertEquals(fromCborHex[Map[String, Int]]("BF616101616202616303FF"), Map("a" -> 1, "b" -> 2, "c" -> 3))

  test("read chunked text string"):
    assertEquals(fromCborHex[String]("7F626162626162626162FF"), "ababab")

  test("read chunked byte string"):
    assertEquals(fromCborHex[Bytes]("5F426162426162426162FF"), Bytes("ababab"))

  // ---- stream-exhaustion (Pitfall 7: no trailing bytes after top-level read) ----
  test("trailing bytes after top-level value raise ReadFailure"):
    intercept[ReadFailure](fromCborHex[Int]("00FF"))
