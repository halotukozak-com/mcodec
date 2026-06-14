package mcodec

import org.scalacheck.{Arbitrary, Gen}

class AdtDerivationTest extends RoundTrip:
  def backend = JsonBackend

  enum Shape derives MCodec:
    case Circle(radius: Double)
    case Rectangle(w: Double, h: Double)
    case Point

  private val finiteDouble: Gen[Double] = Arbitrary.arbitrary[Double].suchThat(d => java.lang.Double.isFinite(d))

  given Arbitrary[Shape] = Arbitrary(
    Gen.oneOf(
      Gen.const(Shape.Point),
      finiteDouble.map(Shape.Circle(_)),
      for w <- finiteDouble; h <- finiteDouble yield Shape.Rectangle(w, h),
    ),
  )

  roundTrip[Shape]("Shape (nested discriminator)")
