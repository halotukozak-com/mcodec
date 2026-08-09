package mcodec

object ForSealedEnumFixtures:
  // Un-annotated: its derived `given` is the default nested-object codec.
  // `MCodec.forSealedEnum` is the EXPLICIT opt-in (GenCodec parity), not the annotation.
  enum Suit derives MCodec:
    case Hearts
    case Spades
    case Clubs

class ForSealedEnumTest extends munit.FunSuite, JsonConv:
  import ForSealedEnumFixtures.*

  given suitAsString: MCodec[Suit] = MCodec.forSealedEnum[Suit]

  test("forSealedEnum writes a bare string (no annotation needed)"):
    assertEquals(Json.write(Suit.Hearts: Suit), "\"Hearts\"")
    assertEquals(Json.write(Suit.Clubs: Suit), "\"Clubs\"")

  test("forSealedEnum round-trips from a bare string"):
    assertEquals(Json.read[Suit]("\"Spades\""), Suit.Spades)

  test("forSealedEnum: unknown string raises ReadFailure"):
    intercept[ReadFailure](Json.read[Suit]("\"Diamonds\""))
