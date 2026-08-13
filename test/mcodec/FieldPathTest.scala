package halotukozak.mcodec

object FieldPathFixtures:
  case class Address(zip: Int) derives MCodec
  case class User(address: Address) derives MCodec
  case class Root(user: User) derives MCodec

  case class Item(name: Int) derives MCodec
  case class Bag(items: List[Item]) derives MCodec

  case class Money(amount: Int) derives MCodec
  case class Wallet(prices: Map[String, Money]) derives MCodec

  enum Shape derives MCodec:
    case Circle(radius: Int)
    case Square(side: Int)
  case class Drawing(shape: Shape) derives MCodec

class FieldPathTest extends munit.FunSuite, JsonConv:
  import FieldPathFixtures.*

  // Nested product -> user.address.zip
  test("nested product field path") {
    val rf = intercept[ReadFailure](fromJson[Root]("""{"user":{"address":{"zip":"abc"}}}"""))
    assert(rf.getMessage.contains("user.address.zip"), s"got: ${rf.getMessage}")
  }

  // List element -> items[3].name (4th element, index 3, has the bad value)
  test("list element field path") {
    val json = """{"items":[{"name":1},{"name":2},{"name":3},{"name":"x"}]}"""
    val rf = intercept[ReadFailure](fromJson[Bag](json))
    assert(rf.getMessage.contains("items[3].name"), s"got: ${rf.getMessage}")
  }

  // Map value -> prices[usd].amount
  test("map value field path") {
    val rf = intercept[ReadFailure](fromJson[Wallet]("""{"prices":{"usd":{"amount":"x"}}}"""))
    assert(rf.getMessage.contains("prices[usd].amount"), s"got: ${rf.getMessage}")
  }

  // ADT case -> shape.Circle.radius
  test("adt case field path") {
    val rf = intercept[ReadFailure](fromJson[Drawing]("""{"shape":{"Circle":{"radius":"x"}}}"""))
    assert(rf.getMessage.contains("shape.Circle.radius"), s"got: ${rf.getMessage}")
  }

  // Cause preservation: the leaf number-parse ReadFailure survives the prepend chain.
  test("leaf cause preserved through prepend chain") {
    val rf = intercept[ReadFailure](fromJson[Root]("""{"user":{"address":{"zip":"abc"}}}"""))
    assert(rf.getCause != null, "leaf cause was lost")
  }
