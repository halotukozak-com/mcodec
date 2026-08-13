package halotukozak.mcodec

import org.scalacheck.{Arbitrary, Gen}

class CborRoundTripTest extends RoundTrip(CborBackend):
  given Arbitrary[java.util.UUID] = Arbitrary(Gen.uuid)
  given Arbitrary[Double] = Arbitrary(Arbitrary.arbitrary[Double].suchThat(java.lang.Double.isFinite))
  given Arbitrary[Float] = Arbitrary(Arbitrary.arbitrary[Float].suchThat(java.lang.Float.isFinite))
  // FlatShape Arbitrary — copy from FlatDiscriminatorTest (that given is scoped to its own suite):
  given Arbitrary[FlatShape] = Arbitrary(
    Gen.oneOf(
      Gen.const(FlatShape.Point),
      Arbitrary.arbitrary[Double].suchThat(java.lang.Double.isFinite).map(FlatShape.Circle(_)),
      for
        w <- Arbitrary.arbitrary[Double].suchThat(java.lang.Double.isFinite)
        h <- Arbitrary.arbitrary[Double].suchThat(java.lang.Double.isFinite)
      yield FlatShape.Rectangle(w, h),
    ),
  )

  roundTrip[Int]("int")
  roundTrip[Long]("long")
  roundTrip[Double]("double")
  roundTrip[Float]("float")
  roundTrip[Byte]("byte")
  roundTrip[Short]("short")
  roundTrip[Boolean]("boolean")
  roundTrip[String]("string")
  roundTrip[BigInt]("bigint")
  roundTrip[BigDecimal]("bigdecimal")
  roundTrip[List[Int]]("list")
  roundTrip[Vector[String]]("vector")
  roundTrip[Map[String, Int]]("map-object")
  roundTrip[Map[Int, String]]("map-pairs")
  roundTrip[FlatShape]("flat-sum") // exercises peekKind/capture generalization (Plan 03)
