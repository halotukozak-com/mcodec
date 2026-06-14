package mcodec

import munit.ScalaCheckSuite
import org.scalacheck.Arbitrary
import org.scalacheck.Prop.forAll

trait RoundTrip extends ScalaCheckSuite:
  def roundTrip[T: Arbitrary](name: String)(using codec: MCodec[T]): Unit =
    property(s"round-trip: $name"):
      forAll: (x: T) =>
        codec.decode(codec.encode(x)) == x
