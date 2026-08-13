package halotukozak.mcodec

import made.*
import made.annotation.name

class MirrorAdapterTest extends munit.FunSuite:
  case class User(@name("user_name") name: String, age: Int = 18)

  test("Made.derived resolves in-project and the enriched mirror is readable"):
    val m = Made.derived[User]
    val (nameElem, ageElem) = m.elems
    // Made 0.1.1: `Label` is a type member, read via constValue.
    assertEquals(compiletime.constValue[nameElem.Label], "user_name")
    assertEquals(nameElem.default, None)
    assertEquals(ageElem.default, Some(18))
