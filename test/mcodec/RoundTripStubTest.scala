package mcodec

class RoundTripStubTest extends RoundTrip:
  given MCodec[Int] = MCodec.stub[Int](_.toString, _.toInt)
  roundTrip[Int]("Int")
