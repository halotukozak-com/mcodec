package mcodec

class AdtWireShapeTest extends munit.FunSuite, JsonConv:
  enum Shape derives MCodec:
    case Circle(radius: Double)
    case Rectangle(w: Double, h: Double)
    case Point

  test("Circle nested-discriminator wire shape"):
    assertEquals(Shape.Circle(2.0).toJson[Shape], "{\"Circle\":{\"radius\":2.0}}")

  test("Rectangle nested-discriminator wire shape"):
    assertEquals(Shape.Rectangle(1.0, 2.0).toJson[Shape], "{\"Rectangle\":{\"w\":1.0,\"h\":2.0}}")

  test("case object -> empty nested object"):
    assertEquals(Shape.Point.toJson[Shape], "{\"Point\":{}}")

  test("unknown case name on read raises ReadFailure"):
    intercept[ReadFailure](MCodec[Shape].read(JsonBackend.input("{\"Nope\":{}}")))

  test("empty object on read raises ReadFailure"):
    intercept[ReadFailure](MCodec[Shape].read(JsonBackend.input("{}")))
