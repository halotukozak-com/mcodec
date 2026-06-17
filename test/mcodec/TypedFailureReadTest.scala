package mcodec

object TypedFailureReadFixtures:
  case class P(n: Int) derives MCodec
  case class Wrap(p: P) derives MCodec
  case class Items(xs: List[Int]) derives MCodec
  case class MapHolder(m: Map[String, Int]) derives MCodec
  sealed trait Shape derives MCodec
  case class Circle(r: Int) extends Shape
  case class Square(side: Int) extends Shape

class TypedFailureReadTest extends munit.FunSuite, JsonConv:
  import TypedFailureReadFixtures.*

  test("missing field → MissingField"):
    intercept[MissingField](fromJson[P]("{}"))

  test("field value type mismatch → FieldReadFailed (with path)"):
    val e = intercept[FieldReadFailed](fromJson[P]("""{"n":"x"}"""))
    assert(e.getMessage.contains("n"), e.getMessage)

  test("FieldReadFailed is a ReadFailure"):
    assert(intercept[ReadFailure](fromJson[P]("""{"n":"x"}""")).isInstanceOf[FieldReadFailed])

  test("nested field path preserved on FieldReadFailed"):
    val e = intercept[FieldReadFailed](fromJson[Wrap]("""{"p":{"n":"x"}}"""))
    assert(e.getMessage.contains("p.n"), e.getMessage)

  test("list element type mismatch → ListElementReadFailed"):
    intercept[ListElementReadFailed](fromJson[Items]("""{"xs":[1,"x"]}"""))

  test("map value type mismatch → MapFieldReadFailed"):
    intercept[MapFieldReadFailed](fromJson[MapHolder]("""{"m":{"a":"x"}}"""))

  test("unknown case → UnknownCase"):
    intercept[UnknownCase](fromJson[Shape]("""{"Triangle":{}}"""))

  test("empty object for sum → MissingCase"):
    intercept[MissingCase](fromJson[Shape]("{}"))

  test("sum wrapper with extra fields → NotSingleField"):
    intercept[NotSingleField](fromJson[Shape]("""{"Circle":{"r":1},"Square":{"side":2}}"""))

  test("case body not an object → CaseReadFailed"):
    intercept[CaseReadFailed](fromJson[Shape]("""{"Circle":42}"""))
