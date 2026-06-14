package mcodec

import mcodec.MKeyCodec.given
import org.scalacheck.{Arbitrary, Gen}

class JsonRoundTripTest extends RoundTrip:
  def backend: Backend = JsonBackend

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

  roundTrip[Option[Int]]("option")
  roundTrip[Either[Int, String]]("either")
  roundTrip[(Int, String)]("tuple2")

  roundTrip[List[Int]]("list")
  roundTrip[Vector[String]]("vector")
  roundTrip[Seq[Int]]("seq")
  roundTrip[Set[Int]]("set")
  roundTrip[Map[String, Int]]("map-object")
  roundTrip[Map[Int, String]]("map-pairs")
