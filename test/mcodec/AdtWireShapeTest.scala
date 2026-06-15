package mcodec

class AdtWireShapeTest extends munit.FunSuite, JsonConv:
  enum Shape derives MCodec:
    case Circle(radius: Double)
    case Rectangle(w: Double, h: Double)
    case Point

  test("Circle nested-discriminator wire shape"):
    assertEquals((Shape.Circle(2.0): Shape).toJson, "{\"Circle\":{\"radius\":2.0}}")

  test("Rectangle nested-discriminator wire shape"):
    assertEquals((Shape.Rectangle(1.0, 2.0): Shape).toJson, "{\"Rectangle\":{\"w\":1.0,\"h\":2.0}}")

  test("case object -> empty nested object"):
    assertEquals((Shape.Point: Shape).toJson, "{\"Point\":{}}")

  test("unknown case name on read raises ReadFailure"):
    intercept[ReadFailure](MCodec[Shape].read(JsonBackend.input("{\"Nope\":{}}")))

  test("empty object on read raises ReadFailure"):
    intercept[ReadFailure](MCodec[Shape].read(JsonBackend.input("{}")))
