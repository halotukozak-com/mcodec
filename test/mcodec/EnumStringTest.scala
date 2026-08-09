package mcodec

import mcodec.annotation.stringEnum

object EnumStringFixtures:
  @stringEnum enum Color derives MCodec:
    case Red
    case Green
    case Blue

  enum PlainColor derives MCodec:
    case Red
    case Green

class EnumStringTest extends munit.FunSuite, JsonConv:
  import EnumStringFixtures.*

  test("annotated all-singleton enum writes a bare string"):
    assertEquals((Color.Red: Color).toJson[Color], "\"Red\"")
    assertEquals((Color.Blue: Color).toJson[Color], "\"Blue\"")

  test("annotated enum round-trips from a bare string"):
    assertEquals(fromJson[Color]("\"Green\""), Color.Green)

  test("REGRESSION GUARD: un-annotated enum keeps v1 empty-object shape"):
    assertEquals((PlainColor.Red: PlainColor).toJson[PlainColor], """{"Red":{}}""")

  test("unknown string on read raises ReadFailure"):
    intercept[ReadFailure](fromJson[Color]("\"Purple\""))
