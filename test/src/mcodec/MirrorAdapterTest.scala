package halotukozak.mcodec

import halotukozak.made.*
import halotukozak.made.annotation.name

class MirrorAdapterTest extends munit.FunSuite:
  case class User(@name("user_name") name: String, age: Int = 18)

  test("Made.derived resolves in-project and the enriched mirror is readable"):
    val m = Made.derived[User]
    val (nameElem, ageElem) = m.elems
    assertEquals(compiletime.constValue[nameElem.Label], "user_name")
    assertEquals[Any, Any](nameElem.default, NotExists)
    assertEquals[Any, Any](ageElem.default, 18)
