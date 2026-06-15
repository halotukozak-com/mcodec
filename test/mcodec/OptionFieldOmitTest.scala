package mcodec

class OptionFieldOmitTest extends munit.FunSuite, JsonConv:

  case class P(o: Option[Int]) derives MCodec

  test("bare Option field absent reads as None (STD-02 read)"):
    assertEquals(fromJson[P]("{}"), P(None))

  test("present Option field reads as Some"):
    assertEquals(fromJson[P]("{\"o\":3}"), P(Some(3)))

  test("present Option field writes value"):
    assertEquals(P(Some(3)).toJson, "{\"o\":3}")

  test("absent Option omitted on write"):
    assertEquals(P(None).toJson, "{}")
