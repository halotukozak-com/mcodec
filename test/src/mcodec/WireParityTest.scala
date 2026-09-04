package halotukozak.mcodec

import halotukozak.mcodec.MKeyCodec.given

final case class WireCase[T](value: T, json: String, cbor: String)(using val codec: MCodec[T])

object ParityFixtures:
  case class Point(x: Int, y: Int) derives MCodec
  enum Color derives MCodec:
    case Red, Green, Blue

  val table: List[WireCase[?]] = List(
    WireCase(0, "0", "00"),
    WireCase(1, "1", "01"),
    WireCase(-1, "-1", "20"),
    WireCase("ab", "\"ab\"", "626162"),
    WireCase(true, "true", "F5"),
    WireCase(false, "false", "F4"),
    WireCase(List(1, 2, 3), "[1,2,3]", "83010203"),
    WireCase(Map("a" -> 1, "b" -> 2, "c" -> 3), "{\"a\":1,\"b\":2,\"c\":3}", "A3616101616202616303"),
    WireCase(("foo", 42, 3.14), "[\"foo\",42,3.14]", "8363666F6F182AFB40091EB851EB851F"),
    WireCase(Point(1, 2), "{\"x\":1,\"y\":2}", "A2617801617902"),
    WireCase[Color](Color.Red, "{\"Red\":{}}", "A163526564BFFF"),
  )

class WireParityTest extends munit.FunSuite, JsonConv, CborConv:
  import ParityFixtures.*

  private def wireJson[T](wc: WireCase[T]): String = wc.value.toJson[T](using wc.codec)
  private def wireCbor[T](wc: WireCase[T]): String = wc.value.toCborHex[T](using wc.codec)
  private def roundJson[T](wc: WireCase[T]): T = fromJson[T](wc.json)(using wc.codec)
  private def roundCbor[T](wc: WireCase[T]): T = fromCborHex[T](wc.cbor)(using wc.codec)

  table.zipWithIndex.foreach:
    case (wc, i) =>
      test(s"json wire [$i]")(assertEquals(wireJson(wc), wc.json))
      test(s"cbor wire [$i]")(assertEquals(wireCbor(wc), wc.cbor))
      test(s"round-trip json [$i]")(assertEquals(roundJson(wc), wc.value))
      test(s"round-trip cbor [$i]")(assertEquals(roundCbor(wc), wc.value))
