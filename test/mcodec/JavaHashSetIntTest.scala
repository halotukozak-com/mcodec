//> using target.platform jvm scala-js

package halotukozak.mcodec

import halotukozak.mcodec.MValue.*

import java.util as ju

// Passes on JVM and Scala.js; crashes the Scala Native runtime with
// scala.scalanative.runtime.UndefinedBehaviorError, which looks like a
// scala-native javalib limitation for java.util.HashSet[Int], not a bug here.
// See https://github.com/halotukozak-com/mcodec/issues/23
class JavaHashSetIntTest extends RoundTrip(InMemoryBackend), JsonConv:

  private def emit[T: MCodec as c](value: T): MValue =
    val (out, harvest) = InMemoryBackend.output()
    c.write(out, value)
    harvest()

  test("java.util.HashSet round-trips"):
    val hs = ju.HashSet[Int]()
    hs.add(1); hs.add(2)
    assertEquals(MCodec[ju.HashSet[Int]].read(InMemoryBackend.input(emit(hs))), hs)
