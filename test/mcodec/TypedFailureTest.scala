package mcodec

object TypedFailureFixtures:
  case class P(x: Int) derives MCodec
  case class Address(zip: Int) derives MCodec
  case class User(address: Address) derives MCodec
  enum Shape derives MCodec:
    case Circle(radius: Int)
    case Square(side: Int)

class TypedFailureTest extends munit.FunSuite, JsonConv:
  import TypedFailureFixtures.*

  test("missing field throws a pattern-matchable MissingField"):
    val rf = intercept[ReadFailure](fromJson[P]("{}"))
    assert(rf.isInstanceOf[MissingField], s"expected MissingField, got ${rf.getClass}")

  test("unknown case throws a pattern-matchable UnknownCase"):
    val rf = intercept[ReadFailure](fromJson[Shape]("""{"Triangle":{}}"""))
    assert(rf.isInstanceOf[UnknownCase], s"expected UnknownCase, got ${rf.getClass}")

  test("typed failure RETAINS dotted path after prepend"):
    val rf = intercept[ReadFailure](fromJson[User]("""{"address":{"zip":"abc"}}"""))
    assert(rf.getMessage.contains("address.zip"), s"path lost: ${rf.getMessage}")

  test("missing field still rendered with v1 envelope"):
    val rf = intercept[ReadFailure](fromJson[P]("{}"))
    assert(rf.getMessage.contains("Failed to read P"), rf.getMessage)
    assert(rf.getMessage.contains("missing"), rf.getMessage)
