package mcodec

import halotukozak.made.annotation.whenAbsent

class DefaultsReadTest extends munit.FunSuite, JsonConv:

  case class C1(a: Int, b: Int = 7) derives MCodec
  case class C2(@whenAbsent(99) x: Int) derives MCodec
  case class C3(@whenAbsent(99) x: Int = 7) derives MCodec
  case class C4(tags: Option[List[String]] = Some(Nil)) derives MCodec

  test("constructor default applied on absent field"):
    assertEquals(fromJson[C1]("{\"a\":1}"), C1(1, 7))

  test("@whenAbsent applied on absent field"):
    assertEquals(fromJson[C2]("{}"), C2(99))

  test("@whenAbsent beats constructor default"):
    assertEquals(fromJson[C3]("{}"), C3(99))

  test("present default wins over Option-omit"):
    assertEquals(fromJson[C4]("{}"), C4(Some(Nil)))

  test("present value still read (sanity)"):
    assertEquals(fromJson[C1]("{\"a\":1,\"b\":2}"), C1(1, 2))
