package mcodec

class AdtWireShapeTest extends munit.FunSuite:
  enum Shape derives MCodec:
    case Circle(radius: Double)
    case Rectangle(w: Double, h: Double)
    case Point

  private def toJson[T: MCodec as codec](x: T): String =
    val (out, harvest) = JsonBackend.output()
    codec.write(out, x)
    harvest()

  test("Circle nested-discriminator wire shape"):
    assertEquals(toJson[Shape](Shape.Circle(2.0)), "{\"Circle\":{\"radius\":2.0}}")

  test("Rectangle nested-discriminator wire shape"):
    assertEquals(toJson[Shape](Shape.Rectangle(1.0, 2.0)), "{\"Rectangle\":{\"w\":1.0,\"h\":2.0}}")

  test("case object -> empty nested object"):
    assertEquals(toJson[Shape](Shape.Point), "{\"Point\":{}}")

  test("unknown case name on read raises ReadFailure"):
    intercept[ReadFailure](MCodec[Shape].read(JsonBackend.input("{\"Nope\":{}}")))

  test("empty object on read raises ReadFailure"):
    intercept[ReadFailure](MCodec[Shape].read(JsonBackend.input("{}")))
