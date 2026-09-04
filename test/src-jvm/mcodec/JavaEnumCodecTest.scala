package halotukozak.mcodec

import halotukozak.mcodec.MValue.*

import java.util as ju

class JavaEnumCodecTest extends RoundTrip(InMemoryBackend), JsonConv:

  private def emit[T: MCodec as c](value: T): MValue =
    val (out, harvest) = InMemoryBackend.output()
    c.write(out, value)
    harvest()

  test("Java enum (TimeUnit) round-trips as its name"):
    assertEquals(
      MCodec[ju.concurrent.TimeUnit].read(InMemoryBackend.input(emit(ju.concurrent.TimeUnit.SECONDS))),
      ju.concurrent.TimeUnit.SECONDS,
    )
    assertEquals(emit(ju.concurrent.TimeUnit.SECONDS), MString("SECONDS"))
    assertEquals(ju.concurrent.TimeUnit.SECONDS.toJson[ju.concurrent.TimeUnit], "\"SECONDS\"")
