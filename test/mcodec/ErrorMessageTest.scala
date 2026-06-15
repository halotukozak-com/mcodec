package mcodec

object ErrorMessageFixtures:
  case class Address(zip: Int) derives MCodec
  case class User(address: Address) derives MCodec

  case class P(x: Int) derives MCodec

  enum Shape derives MCodec:
    case Circle(radius: Int)
    case Square(side: Int)

class ErrorMessageTest extends munit.FunSuite, JsonConv:
  import ErrorMessageFixtures.*

  // FULL-LINE SHAPE (ERR-03): Failed to read <Type> at <path>: <reason> (at L:C)
  test("assembled top-level message shape") {
    val rf = intercept[ReadFailure](fromJson[User]("""{"address":{"zip":"abc"}}"""))
    val msg = rf.getMessage
    assert(msg.startsWith("Failed to read User at "), s"bad envelope: $msg")
    assert(msg.contains("address.zip:"), s"missing path+colon: $msg")
    assert(msg.contains("(at 1:"), s"missing position in top-level message: $msg")
    assert(msg.contains("number"), s"missing leaf reason: $msg")
  }

  // MISSING FIELD (ERR-03, GenCodec-mirrored): well-formed structure -> envelope + path but NO (at L:C).
  test("missing field message names field and type") {
    val rf = intercept[ReadFailure](fromJson[P]("{}"))
    val msg = rf.getMessage
    assert(msg.contains("Failed to read P"), s"missing root-type envelope: $msg")
    assert(msg.contains("x"), s"missing field name: $msg")
    assert(msg.contains("missing"), s"missing 'missing' wording: $msg")
    assert(!msg.contains("(at"), s"missing-field must not carry a position suffix: $msg")
  }

  // UNKNOWN CASE (ERR-03 wording)
  test("unknown case message names the discriminator") {
    val rf = intercept[ReadFailure](fromJson[Shape]("""{"Triangle":{}}"""))
    val msg = rf.getMessage
    assert(msg.contains("Failed to read Shape"), s"missing root-type envelope: $msg")
    assert(msg.contains("unknown case"), s"missing 'unknown case' wording: $msg")
    assert(msg.contains("Triangle"), s"missing discriminator: $msg")
  }
