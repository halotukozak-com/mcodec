package halotukozak.mcodec

class RecursiveDerivationTest extends munit.FunSuite:
  case class Tree(value: Int, children: List[Tree]) derives MCodec

  case class A(b: Option[B]) derives MCodec
  case class B(a: Option[A]) derives MCodec

  enum IntList derives MCodec:
    case Empty
    case Cons(head: Int, tail: IntList)

  enum Json derives MCodec:
    case JNull
    case JBool(b: Boolean)
    case JNum(n: Double)
    case JArr(items: List[Json])
    case JObj(fields: Map[String, Json])

  case class Chain(value: Int, next: Option[Chain]) derives MCodec

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

  test("recursive enum (linked list) round-trips"):
    import IntList.*
    val v: IntList = Cons(1, Cons(2, Cons(3, Empty)))
    assertEquals(rt(v), v)

  test("recursive enum through List and Map (JSON-like) round-trips"):
    import Json.*
    val v: Json = JObj(
      Map(
        "a" -> JNum(1.0),
        "b" -> JArr(List(JBool(true), JNull, JNum(2.5))),
        "c" -> JObj(Map("nested" -> JArr(List(JNum(3.0))))),
      ),
    )
    assertEquals(rt(v), v)

  test("self-recursive product through Option round-trips"):
    val v = Chain(1, Some(Chain(2, Some(Chain(3, None)))))
    assertEquals(rt(v), v)
