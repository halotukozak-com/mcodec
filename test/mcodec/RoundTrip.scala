package mcodec

import munit.ScalaCheckSuite
import org.scalacheck.Arbitrary
import org.scalacheck.Prop.forAll

open class RoundTrip(backend: => Backend) extends ScalaCheckSuite:

  def roundTrip[T: {Arbitrary, MCodec as codec}](name: String): Unit = property(s"round-trip: $name"):
    forAll: (x: T) =>
      val b = backend
      val (out, harvest) = b.output()
      codec.write(out, x)
      codec.read(b.input(harvest())) == x
