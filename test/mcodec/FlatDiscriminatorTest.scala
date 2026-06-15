package mcodec

import mcodec.annotation.flatten
import org.scalacheck.{Arbitrary, Gen}

@flatten enum FlatShape derives MCodec:
  case Circle(radius: Double)
  case Rectangle(w: Double, h: Double)
  case Point

@flatten("type") enum TypedShape derives MCodec:
  case Square(side: Double)
  case Dot

@flatten("kind") enum Collide derives MCodec:
  case A(kind: String)

class FlatDiscriminatorTest extends RoundTrip(JsonBackend):

  private val finiteDouble: Gen[Double] = Arbitrary.arbitrary[Double].suchThat(d => java.lang.Double.isFinite(d))

  given Arbitrary[FlatShape] = Arbitrary(
    Gen.oneOf(
      Gen.const(FlatShape.Point),
      finiteDouble.map(FlatShape.Circle(_)),
      for
        w <- finiteDouble
        h <- finiteDouble
      yield FlatShape.Rectangle(w, h),
    ),
  )

  roundTrip[FlatShape]("FlatShape (flat discriminator)")

class FlatWireShapeTest extends munit.FunSuite, JsonConv:
  test("Circle flat-discriminator wire shape"):
    assertEquals((FlatShape.Circle(2.0): FlatShape).toJson, "{\"_case\":\"Circle\",\"radius\":2.0}")

  test("Rectangle flat-discriminator wire shape"):
    assertEquals((FlatShape.Rectangle(1.0, 2.0): FlatShape).toJson, "{\"_case\":\"Rectangle\",\"w\":1.0,\"h\":2.0}")

  test("singleton case -> discriminator only"):
    assertEquals((FlatShape.Point: FlatShape).toJson, "{\"_case\":\"Point\"}")

  test("@flatten(\"type\") overrides the discriminator key"):
    assertEquals((TypedShape.Square(3.0): TypedShape).toJson, "{\"type\":\"Square\",\"side\":3.0}")

  test("non-leading discriminator reads correctly"):
    assertEquals(
      MCodec[FlatShape].read(JsonBackend.input("{\"radius\":2.0,\"_case\":\"Circle\"}")),
      FlatShape.Circle(2.0),
    )

  test("unknown case (no defaultCase) raises ReadFailure"):
    intercept[ReadFailure](MCodec[FlatShape].read(JsonBackend.input("{\"_case\":\"Nope\"}")))

  test("flat discriminator key colliding with case field raises ReadFailure"):
    intercept[ReadFailure](MCodec[Collide].read(JsonBackend.input("{\"kind\":\"A\"}")))
