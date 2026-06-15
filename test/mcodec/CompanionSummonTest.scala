package mcodec

object Wrapped:
  case class Tagged(raw: String)
  object Tagged:
    given MCodec[Tagged] = MCodec[String].transformed(Tagged(_))(_.raw)

class CompanionSummonTest extends munit.FunSuite, JsonConv:
  test("companion-defined given summoned with no import (MANUAL-04)"):
    summon[MCodec[Wrapped.Tagged]]
    assertEquals(fromJson[Wrapped.Tagged](toJson(Wrapped.Tagged("x"))), Wrapped.Tagged("x"))
