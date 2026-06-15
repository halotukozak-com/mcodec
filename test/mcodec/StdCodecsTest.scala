package mcodec

import mcodec.MKeyCodec.given
import mcodec.MValue.*
import org.scalacheck.{Arbitrary, Gen}

class StdCodecsTest extends RoundTrip(InMemoryBackend), JsonConv:
  given Arbitrary[java.util.UUID] = Arbitrary(Gen.uuid)
  given Arbitrary[Double] = Arbitrary(Arbitrary.arbitrary[Double].suchThat(d => java.lang.Double.isFinite(d)))

  roundTrip[Int]("int")
  roundTrip[Long]("long")
  roundTrip[Double]("double")
  roundTrip[Boolean]("boolean")
  roundTrip[String]("string")
  roundTrip[BigInt]("bigint")
  roundTrip[BigDecimal]("bigdecimal")
  roundTrip[java.util.UUID]("uuid")

  roundTrip[Option[Int]]("option-int")
  roundTrip[Option[String]]("option-string")
  roundTrip[Either[Int, String]]("either")
  roundTrip[(Int, String)]("tuple2")

  roundTrip[List[Int]]("list-int")
  roundTrip[Vector[String]]("vector-string")
  roundTrip[Seq[Int]]("seq-int")
  roundTrip[Set[Int]]("set-int")
  roundTrip[Map[String, Int]]("map-string-int")
  roundTrip[Map[Int, String]]("map-int-string")

  private def emit[T: MCodec as c](value: T): MValue =
    val (out, harvest) = InMemoryBackend.output()
    c.write(out, value)
    harvest()

  test("Left writes single-field object"):
    assertEquals(emit[Either[Int, String]](Left(5)), MObj(Vector("Left" -> MInt(5))))

  test("Right writes single-field object"):
    assertEquals(emit[Either[Int, String]](Right("hi")), MObj(Vector("Right" -> MString("hi"))))

  test("Either rejects unexpected field name"):
    intercept[ReadFailure] {
      MCodec[Either[Int, String]].read(new InMemoryInput(MObj(Vector("Nope" -> MInt(1)))))
    }

  test("None writes wire-null, Some writes value"):
    assertEquals(emit[Option[Int]](None), MNull)
    assertEquals(emit[Option[Int]](Some(3)), MInt(3))

  test("tuple2 writes positional list"):
    assertEquals(emit[(Int, String)]((1, "a")), MList(Vector(MInt(1), MString("a"))))

  test("Array round-trips by element equality"):
    val arr = Array(1, 2, 3)
    val read = MCodec[Array[Int]].read(InMemoryBackend.input(emit(arr)))
    assert(read.sameElements(arr))

  test("Map[String, Int] writes as object (object branch)"):
    assertEquals(emit(Map("a" -> 1)), MObj(Vector("a" -> MInt(1))))

  test("Map with non-string key writes as array-of-pairs (pairs branch)"):
    val captured = emit(Map(List(1) -> 2))
    captured match
      case MList(items) =>
        assertEquals(items, Vector(MList(Vector(MList(Vector(MInt(1))), MInt(2)))))
      case other => fail(s"expected MList of pairs, got $other")

  test("Set dedups duplicate elements"):
    val read = MCodec[Set[Int]].read(InMemoryBackend.input(MList(Vector(MInt(1), MInt(1), MInt(2)))))
    assertEquals(read, Set(1, 2))

  test("empty List round-trips to empty"):
    val read = MCodec[List[Int]].read(InMemoryBackend.input(emit(List.empty[Int])))
    assertEquals(read, List.empty[Int])

  test("MKeyCodec[Int] round-trips key text"):
    assertEquals(MKeyCodec[Int].writeKey(7), "7")
    assertEquals(MKeyCodec[Int].readKey("7"), 7)

  test("MKeyCodec.create builds a working instance"):
    val k = MKeyCodec.create[Long](_.toString, _.toLong)
    assertEquals(k.writeKey(9L), "9")
    assertEquals(k.readKey("9"), 9L)

  test("uuidCodec rejects invalid string on read"):
    intercept[ReadFailure] {
      MCodec[java.util.UUID].read(new InMemoryInput(MString("not-a-uuid")))
    }

  test("MANUAL-05 Map[Int, String] round-trips through JSON with string keys"):
    assertEquals(Map(1 -> "a").toJson, "{\"1\":\"a\"}")
    assertEquals(fromJson[Map[Int, String]]("{\"1\":\"a\"}"), Map(1 -> "a"))
