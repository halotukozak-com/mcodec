package mcodec

import mcodec.annotation.transientDefault

object TransientDefaultFixtures:
  case class TD(@transientDefault x: Int = 7, keep: String) derives MCodec
  case class TDList(@transientDefault xs: List[Int] = Nil) derives MCodec
  case class TDOpt(@transientDefault o: Option[Int] = None) derives MCodec

// EQUALITY CAVEAT: omission is decided by structural `==` against the declared
// default, so collection/Option defaults must compare equal (Nil == Nil, None == None).
class TransientDefaultTest extends munit.FunSuite, JsonConv:
  import TransientDefaultFixtures.*

  test("field equal to default is OMITTED"):
    assertEquals(TD(7, "a").toJson[TD], """{"keep":"a"}""")

  test("field differing from default is WRITTEN"):
    assertEquals(TD(9, "a").toJson[TD], """{"x":9,"keep":"a"}""")

  test("collection field equal to default (structural ==) is OMITTED"):
    assertEquals(TDList(Nil).toJson[TDList], "{}")
    assertEquals(TDList(List(1)).toJson[TDList], """{"xs":[1]}""")

  test("Option field default None omitted once (no double-omit)"):
    assertEquals(TDOpt(None).toJson[TDOpt], "{}")
    assertEquals(TDOpt(Some(3)).toJson[TDOpt], """{"o":3}""")
