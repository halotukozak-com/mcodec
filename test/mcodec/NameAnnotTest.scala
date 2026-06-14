package mcodec

import made.annotation.name

class NameAnnotTest extends munit.FunSuite:
  case class User(@name("user_name") name: String, age: Int) derives MCodec

  enum Shape derives MCodec:
    @name("circ") case Circle(radius: Double)

  private def toJson[T: MCodec as codec](x: T): String =
    val (out, harvest) = JsonBackend.output()
    codec.write(out, x)
    harvest()

  test("@name overrides a field label on the wire"):
    assertEquals(toJson(User("bob", 30)), "{\"user_name\":\"bob\",\"age\":30}")

  test("@name overrides a case label in the ADT wrapper"):
    assertEquals(toJson[Shape](Shape.Circle(2.0)), "{\"circ\":{\"radius\":2.0}}")

  test("@name field round-trips"):
    assertEquals(MCodec[User].read(JsonBackend.input(toJson(User("bob", 30)))), User("bob", 30))
