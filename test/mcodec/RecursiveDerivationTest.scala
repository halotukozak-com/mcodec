package halotukozak.mcodec

class RecursiveDerivationTest extends munit.FunSuite:
  case class Tree(value: Int, children: List[Tree]) derives MCodec

  case class A(b: Option[B]) derives MCodec
  case class B(a: Option[A]) derives MCodec

  private def rt[T: MCodec as codec](x: T): T =
    val (out, harvest) = JsonBackend.output()
    codec.write(out, x)
    codec.read(JsonBackend.input(harvest()))

  test("recursive Tree round-trips without StackOverflow"):
    val t = Tree(1, List(Tree(2, Nil), Tree(3, List(Tree(4, Nil)))))
    assertEquals(rt(t), t)

  test("mutually-recursive A/B round-trip without StackOverflow"):
    val v = A(Some(B(Some(A(None)))))
    assertEquals(rt(v), v)
