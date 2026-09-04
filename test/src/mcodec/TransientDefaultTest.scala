package halotukozak.mcodec

import halotukozak.mcodec.annotation.transientDefault

object TransientDefaultFixtures:
  case class TD(@transientDefault x: Int = 7, keep: String) derives MCodec
  case class TDList(@transientDefault xs: List[Int] = Nil) derives MCodec
  case class TDOpt(@transientDefault o: Option[Int] = None) derives MCodec
  case class Leaf(@transientDefault n: Int = 0) derives MCodec
  case class Nest(leaf: Leaf, @transientDefault label: String = "d") derives MCodec

// EQUALITY CAVEAT: omission is decided by structural `==` against the declared
// default, so collection/Option defaults must compare equal (Nil == Nil, None == None).
class TransientDefaultTest extends munit.FunSuite, JsonConv:
  import TransientDefaultFixtures.*

  test("field equal to default is OMITTED"):
    assertEquals(TD(7, "a").toJson[TD], """{"keep":"a"}""")

  test("field differing from default is WRITTEN"):
    assertEquals(TD(9, "a").toJson[TD], """{"x":9,"keep":"a"}""")

  test("forceTransientDefaults marker forces the defaulted field in"):
    val forced = MCodec.forceTransientDefaults(MCodec[TD])
    assertEquals(Json.write(TD(7, "a"))(using forced), """{"x":7,"keep":"a"}""")

  test("forceTransientDefaults recurses into nested products"):
    // default write omits both leaf.n (==0) and label (=="d")
    assertEquals(Nest(Leaf(0), "d").toJson[Nest], """{"leaf":{}}""")
    val forced = MCodec.forceTransientDefaults(MCodec[Nest])
    assertEquals(Json.write(Nest(Leaf(0), "d"))(using forced), """{"leaf":{"n":0},"label":"d"}""")

  test("collection field equal to default (structural ==) is OMITTED"):
    assertEquals(TDList(Nil).toJson[TDList], "{}")
    assertEquals(TDList(List(1)).toJson[TDList], """{"xs":[1]}""")

  test("Option field default None omitted once (no double-omit)"):
    assertEquals(TDOpt(None).toJson[TDOpt], "{}")
    assertEquals(TDOpt(Some(3)).toJson[TDOpt], """{"o":3}""")
