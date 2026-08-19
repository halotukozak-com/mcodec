package halotukozak.mcodec

import org.scalacheck.{Arbitrary, Gen}
import halotukozak.mcodec.MKeyCodec.given

// Only object-shaped root types are exercised here: BSON requires a document at the top level
// (see BsonFormatTest "BS1"), unlike JSON/CBOR which admit any T at the root.
class BsonRoundTripTest extends RoundTrip(BsonBackend):
  given Arbitrary[Point] = Arbitrary(for
    x <- Arbitrary.arbitrary[Int]
    y <- Arbitrary.arbitrary[Int]
  yield Point(x, y))

  given Arbitrary[Double] = Arbitrary(Arbitrary.arbitrary[Double].suchThat(java.lang.Double.isFinite))

  // FlatShape Arbitrary — copy from FlatDiscriminatorTest (that given is scoped to its own suite).
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

  given objectIdWrapArb: Arbitrary[BsonWrap[ObjectId]] = Arbitrary(
    Gen.containerOfN[Array, Byte](12, Arbitrary.arbitrary[Byte]).map(bytes => BsonWrap(ObjectId(bytes))),
  )
  given regexWrapArb: Arbitrary[BsonWrap[BsonRegex]] = Arbitrary(for
    p <- Gen.alphaNumStr
    o <- Gen.alphaStr
  yield BsonWrap(BsonRegex(p, o)))
  given jsCodeWrapArb: Arbitrary[BsonWrap[JsCode]] = Arbitrary(Arbitrary.arbitrary[String].map(s => BsonWrap(JsCode(s))))

  roundTrip[Point]("case class (Point)")
  roundTrip[Map[String, Int]]("map-object")
  roundTrip[FlatShape]("flat-sum (exercises peekKind/capture generalization)")
  roundTrip[BsonWrap[ObjectId]]("ObjectId (wrapped, native tag)")
  roundTrip[BsonWrap[BsonRegex]]("BsonRegex (wrapped, native tag)")
  roundTrip[BsonWrap[JsCode]]("JsCode (wrapped, native tag)")
