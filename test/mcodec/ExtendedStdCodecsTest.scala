package mcodec

import mcodec.MValue.*
import org.scalacheck.{Arbitrary, Gen}

import java.lang as jl
import java.util as ju
import scala.jdk.CollectionConverters.*

class ExtendedStdCodecsTest extends RoundTrip(InMemoryBackend), JsonConv:

  private def emit[T: MCodec as c](value: T): MValue =
    val (out, harvest) = InMemoryBackend.output()
    c.write(out, value)
    harvest()

  // exact-equality round-trip needs finite Floats (mirror StdCodecsTest Double filter)
  given Arbitrary[Float] = Arbitrary(Arbitrary.arbitrary[Float].suchThat(f => java.lang.Float.isFinite(f)))

  // ===== Byte / Short / Char / Float =====
  roundTrip[Byte]("byte")
  roundTrip[Short]("short")
  roundTrip[Char]("char")
  roundTrip[Float]("float")

  test("Byte/Short wire shape is a bare number; Char is a 1-char string"):
    assertEquals(emit[Byte](5.toByte), MInt(5))
    assertEquals(emit[Short](5.toShort), MInt(5))
    assertEquals(emit[Char]('a'), MString("a"))

  test("JSON wire shapes (Char-as-string, Byte bare number)"):
    assertEquals('a'.toJson[Char], "\"a\"")
    assertEquals(5.toByte.toJson[Byte], "5")

  test("Byte/Short overflow rejected via the codec path"):
    intercept[ReadFailure](MCodec[Byte].read(new InMemoryInput(MInt(300))))
    intercept[ReadFailure](MCodec[Short].read(new InMemoryInput(MInt(40000))))

  // ===== java.util.Date (epoch-millis encoding — NO ISO parsing) =====
  given Arbitrary[java.util.Date] = Arbitrary(Arbitrary.arbitrary[Long].map(new java.util.Date(_)))
  roundTrip[java.util.Date]("date")

  test("Date wire shape is bare epoch-millis (writeTimestamp -> writeLong -> MLong)"):
    // epoch-millis encoding — NO ISO parsing
    assertEquals(emit[java.util.Date](new java.util.Date(0L)), MLong(0L))
    assertEquals(new java.util.Date(1750000000000L).toJson[java.util.Date], "1750000000000")

  test("Date rejects a non-numeric wire value (type-mismatch, NOT ISO validation)"):
    // type-mismatch rejection path (readTimestamp -> readLong on a string MValue), not ISO validation
    intercept[ReadFailure](MCodec[java.util.Date].read(new InMemoryInput(MString("not-a-date"))))

  // ===== Array[Byte] / binary (base64 in JSON) =====
  test("Array[Byte] round-trips by element equality"):
    for b <- Seq(Array.empty[Byte], Array[Byte](1, 2, 3), Array[Byte](0, -1, 127, -128)) do
      val read = MCodec[Array[Byte]].read(InMemoryBackend.input(emit(b)))
      assert(read.sameElements(b))

  test("Array[Byte] JSON wire shape is a quoted base64 string"):
    val s = Array[Byte](0, 1, 2).toJson[Array[Byte]]
    assert(s.startsWith("\"") && s.endsWith("\""), s"expected quoted base64, got $s")
    val decoded = java.util.Base64.getDecoder.nn.decode(s.substring(1, s.length - 1)).nn
    assert(decoded.sameElements(Array[Byte](0, 1, 2)))

  // ===== Symbol =====
  given Arbitrary[Symbol] = Arbitrary(Arbitrary.arbitrary[String].map(Symbol(_)))
  roundTrip[Symbol]("symbol")

  test("Symbol JSON wire shape is a quoted string"):
    assertEquals(Symbol("hi").toJson[Symbol], "\"hi\"")

  // ===== degenerate Unit / Null / Void / Nothing =====
  test("Unit writes wire-null and reads back ()"):
    assertEquals(emit[Unit](()), MNull)
    assertEquals(().toJson[Unit], "null")
    assertEquals(fromJson[Unit]("null"), ())

  test("reading a non-null for Unit raises ReadFailure"):
    intercept[ReadFailure](fromJson[Unit]("5"))

  test("Null and Void write wire-null"):
    assertEquals(emit[Null](null), MNull)
    assertEquals(null.toJson[Null], "null")
    assertEquals(emit[Void | Null](null), MNull)

  test("Nothing read throws (cannot inhabit Nothing)"):
    intercept[ReadFailure](fromJson[Nothing]("null"))

  // ===== Java boxed types (all 10) =====
  test("boxed types round-trip via emit/read (value equality)"):
    assertEquals(
      MCodec[jl.Boolean | Null].read(InMemoryBackend.input(emit[jl.Boolean | Null](jl.Boolean.valueOf(true)))),
      jl.Boolean.valueOf(true),
    )
    assertEquals(
      MCodec[jl.Character | Null].read(InMemoryBackend.input(emit[jl.Character | Null](jl.Character.valueOf('a')))),
      jl.Character.valueOf('a'),
    )
    assertEquals(
      MCodec[jl.Byte | Null].read(InMemoryBackend.input(emit[jl.Byte | Null](jl.Byte.valueOf(3.toByte)))),
      jl.Byte.valueOf(3.toByte),
    )
    assertEquals(
      MCodec[jl.Short | Null].read(InMemoryBackend.input(emit[jl.Short | Null](jl.Short.valueOf(4.toShort)))),
      jl.Short.valueOf(4.toShort),
    )
    assertEquals(
      MCodec[jl.Integer | Null].read(InMemoryBackend.input(emit[jl.Integer | Null](jl.Integer.valueOf(7)))),
      jl.Integer.valueOf(7),
    )
    assertEquals(
      MCodec[jl.Long | Null].read(InMemoryBackend.input(emit[jl.Long | Null](jl.Long.valueOf(8L)))),
      jl.Long.valueOf(8L),
    )
    assertEquals(
      MCodec[jl.Float | Null].read(InMemoryBackend.input(emit[jl.Float | Null](jl.Float.valueOf(1.5f)))),
      jl.Float.valueOf(1.5f),
    )
    assertEquals(
      MCodec[jl.Double | Null].read(InMemoryBackend.input(emit[jl.Double | Null](jl.Double.valueOf(2.5)))),
      jl.Double.valueOf(2.5),
    )
    assertEquals(
      MCodec[java.math.BigInteger | Null]
        .read(InMemoryBackend.input(emit[java.math.BigInteger | Null](java.math.BigInteger.valueOf(9L)))),
      java.math.BigInteger.valueOf(9L),
    )
    assertEquals(
      MCodec[java.math.BigDecimal | Null]
        .read(InMemoryBackend.input(emit[java.math.BigDecimal | Null](java.math.BigDecimal.valueOf(1.25)))),
      java.math.BigDecimal.valueOf(1.25),
    )

  test("boxed Integer wire shape is a bare number"):
    assertEquals(emit[jl.Integer | Null](jl.Integer.valueOf(7)), MInt(7))

  test("boxed types are nullable (wire-null <-> JVM null)"):
    assertEquals(MCodec[jl.Integer | Null].read(InMemoryBackend.input(MNull)), null)
    assertEquals(emit[jl.Integer | Null](null), MNull)

  // ===== Java collections / maps / enums via JFactory =====
  test("Java enum (TimeUnit) round-trips as its name"):
    assertEquals(
      MCodec[ju.concurrent.TimeUnit].read(InMemoryBackend.input(emit(ju.concurrent.TimeUnit.SECONDS))),
      ju.concurrent.TimeUnit.SECONDS,
    )
    assertEquals(emit(ju.concurrent.TimeUnit.SECONDS), MString("SECONDS"))
    assertEquals(ju.concurrent.TimeUnit.SECONDS.toJson[ju.concurrent.TimeUnit], "\"SECONDS\"")

  test("java.util.ArrayList round-trips as a list"):
    val al = ju.ArrayList[Int]()
    al.add(1); al.add(2)
    assertEquals(MCodec[ju.ArrayList[Int]].read(InMemoryBackend.input(emit(al))), al)
    assertEquals(emit(al), MList(Vector(MInt(1), MInt(2))))

  test("java.util.HashSet round-trips"):
    val hs = ju.HashSet[Int]()
    hs.add(1); hs.add(2)
    assertEquals(MCodec[ju.HashSet[Int]].read(InMemoryBackend.input(emit(hs))), hs)

  test("java.util.HashMap round-trips as an object"):
    val hm = ju.HashMap[String, Int]()
    hm.put("a", 1)
    assertEquals(hm.toJson[ju.HashMap[String, Int]], "{\"a\":1}")
    assertEquals(MCodec[ju.HashMap[String, Int]].read(InMemoryBackend.input(emit(hm))), hm)

  test("Java collection/map codecs resolve (resolution check)"):
    assert(MCodec[ju.ArrayList[Int]] != null)
    assert(MCodec[ju.HashMap[String, Int]] != null)

  // ===== regression: Scala enums keep empty-object singleton wire shape (v1 lock) =====
  // Expected shape copied from AdtWireShapeTest ("case object -> empty nested object" => {"Point":{}}).
  enum Color derives MCodec:
    case Red, Green

  test("regression: Scala enum keeps empty-object singleton wire shape (jEnumCodec must not bleed in)"):
    assertEquals(emit[Color](Color.Red), MObj(Vector("Red" -> MObj(Vector()))))
