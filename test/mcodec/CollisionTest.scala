package halotukozak.mcodec

import scala.compiletime.testing.typeCheckErrors

class CollisionTest extends munit.FunSuite:
  test("duplicate @name on sibling fields is rejected at compile time"):
    val errs = typeCheckErrors("""
      import halotukozak.mcodec.*
      import made.annotation.name
      case class Bad(@name("x") a: Int, @name("x") b: Int) derives MCodec
    """)
    assert(errs.nonEmpty, "expected a compile error for duplicate @name")
