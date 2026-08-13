package halotukozak.mcodec

class DerivationCacheTest extends munit.FunSuite:
  case class Person(name: String, age: Int) derives MCodec

  test("derived MCodec is a single cached given (no per-use re-derivation)"):
    val a = summon[MCodec[Person]]
    val b = summon[MCodec[Person]]
    assert(a eq b, "expected the same cached instance from two summons")
