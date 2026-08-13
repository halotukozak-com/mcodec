package halotukozak.mcodec

class RoundTripStubTest extends RoundTrip(InMemoryBackend):

  given MCodec[Int] = new SimpleCodec[Int]:
    def readSimple(in: SimpleInput): Int = in.readInt()
    def writeSimple(out: SimpleOutput, v: Int): Unit = out.writeInt(v)

  roundTrip[Int]("Int")
