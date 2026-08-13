package halotukozak.mcodec

import halotukozak.mcodec.annotation.{defaultCase, flatten}
import scala.compiletime.testing.typeCheckErrors

enum NestedCmd derives MCodec:
  case Start(n: Int)
  @defaultCase case Unknown

@flatten enum FlatCmd derives MCodec:
  case Go(n: Int)
  @defaultCase case Fallback

class DefaultCaseTest extends munit.FunSuite, JsonConv:
  test("nested absent discriminator falls back to @defaultCase"):
    assertEquals(MCodec[NestedCmd].read(JsonBackend.input("{}")), NestedCmd.Unknown)

  test("nested unknown discriminator falls back to @defaultCase"):
    assertEquals(MCodec[NestedCmd].read(JsonBackend.input("{\"Nope\":{}}")), NestedCmd.Unknown)

  test("flat absent discriminator falls back to @defaultCase"):
    assertEquals(MCodec[FlatCmd].read(JsonBackend.input("{}")), FlatCmd.Fallback)

  test("flat unknown discriminator falls back to @defaultCase"):
    assertEquals(MCodec[FlatCmd].read(JsonBackend.input("{\"_case\":\"Nope\"}")), FlatCmd.Fallback)

  test("non-default nested case still round-trips"):
    assertEquals(fromJson[NestedCmd](NestedCmd.Start(5).toJson[NestedCmd]), NestedCmd.Start(5))

  test("non-default flat case still round-trips"):
    assertEquals(fromJson[FlatCmd](FlatCmd.Go(7).toJson[FlatCmd]), FlatCmd.Go(7))

  test("more than one @defaultCase is rejected at compile time"):
    val errs = typeCheckErrors("""
      import halotukozak.mcodec.*
      import halotukozak.mcodec.annotation.{flatten, defaultCase}
      @flatten enum TwoDefaults derives MCodec:
        @defaultCase case A
        @defaultCase case B
    """)
    assert(errs.nonEmpty, "expected a compile error for >1 @defaultCase")
