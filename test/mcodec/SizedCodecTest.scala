package mcodec

import halotukozak.made.annotation.{generated, optionalParam}
import mcodec.annotation.transientDefault

case class TDSized(@transientDefault x: Int = 7, keep: String) derives MCodec

case class AllPresent(a: Int, b: String, c: Boolean) derives MCodec

case class WithBareOption(x: Int, o: Option[Int]) derives MCodec

case class WithOptParam(x: Int, @optionalParam o: Option[Int]) derives MCodec

case class WithGenerated(x: Int) derives MCodec:
  @generated def doubled: Int = x * 2

case class WithDefault(x: Int = 7) derives MCodec

class SizedCodecTest extends munit.FunSuite, JsonConv:

  private def declaredOf[T: MCodec](v: T): Int =
    val (_, sizes) = RecordingBackend.declaredSizes(v)
    assertEquals(sizes.length, 1, s"expected exactly one declareSize, got $sizes")
    sizes.head

  private def writtenFieldCount(mv: MValue): Int = mv match
    case MValue.MObj(fs) => fs.length
    case MValue.MList(xs) => xs.length
    case other => fail(s"expected object/list, got $other")

  test("product declares exact field count for all-present case"):
    assertEquals(declaredOf(AllPresent(1, "y", true)), 3)

  test("product omits bare None from the declared count"):
    assertEquals(declaredOf(WithBareOption(1, None)), 1)
    assertEquals(declaredOf(WithBareOption(1, Some(2))), 2)

  test("product omits optional-param None from the declared count"):
    assertEquals(declaredOf(WithOptParam(1, None)), 1)
    assertEquals(declaredOf(WithOptParam(1, Some(2))), 2)

  test("generated members are always counted"):
    assertEquals(declaredOf(WithGenerated(3)), 2)

  test("defaulted field is written and counted"):
    assertEquals(declaredOf(WithDefault()), 1)
    assertEquals(declaredOf(WithDefault(9)), 1)

  test("declared count equals fields actually written"):
    def check[T: MCodec](v: T): Unit =
      val (mv, sizes) = RecordingBackend.declaredSizes(v)
      assertEquals(sizes.length, 1, s"expected exactly one declareSize, got $sizes")
      assertEquals(sizes.head, writtenFieldCount(mv))
    check(AllPresent(1, "y", true))
    check(WithBareOption(1, None))
    check(WithBareOption(1, Some(2)))
    check(WithOptParam(1, None))
    check(WithOptParam(1, Some(2)))
    check(WithGenerated(3))
    check(WithDefault())

  test("flat-sum declares discriminator plus body and only once"):
    assertEquals(declaredOf[FlatShape](FlatShape.Circle(2.0)), 2)
    assertEquals(declaredOf[FlatShape](FlatShape.Rectangle(1.0, 2.0)), 3)
    assertEquals(declaredOf[FlatShape](FlatShape.Point), 1)

  test("collection declares its size"):
    assertEquals(declaredOf(List(1, 2, 3)), 3)
    assertEquals(declaredOf(Vector.empty[Int]), 0)
    assertEquals(declaredOf(Set(1, 2)), 2)

  test("object map declares its entry count"):
    assertEquals(declaredOf(Map("a" -> 1, "b" -> 2)), 2)

  test("@transientDefault omitted field not counted; non-default counted"):
    assertEquals(declaredOf(TDSized(7, "a")), 1)
    assertEquals(declaredOf(TDSized(9, "a")), 2)

  test("force-write marker counts the forced field"):
    val (_, sizes) = RecordingBackend.declaredSizes(TDSized(7, "a"))(
      using MCodec.forceTransientDefaults(MCodec[TDSized]),
    )
    assertEquals(sizes.head, 2)
