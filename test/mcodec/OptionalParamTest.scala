package halotukozak.mcodec

import made.annotation.optionalParam

class OptionalParamTest extends munit.FunSuite, JsonConv:

  case class O1(@optionalParam o: Option[Int]) derives MCodec

  test("@optionalParam reads absent as None"):
    assertEquals(fromJson[O1]("{}"), O1(None))

  test("@optionalParam reads present as Some"):
    assertEquals(fromJson[O1]("{\"o\":5}"), O1(Some(5)))

  test("@optionalParam omits None on write"):
    assertEquals(O1(None).toJson, "{}")

  test("@optionalParam writes present value"):
    assertEquals(O1(Some(5)).toJson, "{\"o\":5}")
