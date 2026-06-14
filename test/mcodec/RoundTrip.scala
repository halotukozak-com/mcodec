package mcodec

import munit.ScalaCheckSuite
import org.scalacheck.Arbitrary
import org.scalacheck.Prop.forAll

trait RoundTrip extends ScalaCheckSuite:
  def backend: Backend

  def roundTrip[T: Arbitrary](name: String)(using codec: MCodec[T]): Unit =
    property(s"round-trip: $name"):
      forAll: (x: T) =>
        val b = backend
        val (out, harvest) = b.output()
        codec.write(out, x)
        codec.read(b.input(harvest())) == x
