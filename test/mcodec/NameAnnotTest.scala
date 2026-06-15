package mcodec

import made.annotation.name

class NameAnnotTest extends munit.FunSuite, JsonConv:
  case class User(@name("user_name") name: String, age: Int) derives MCodec

  enum Shape derives MCodec:
    @name("circ") case Circle(radius: Double)

  test("@name overrides a field label on the wire"):
    assertEquals(User("bob", 30).toJson, "{\"user_name\":\"bob\",\"age\":30}")

  test("@name overrides a case label in the ADT wrapper"):
    assertEquals(Shape.Circle(2.0).toJson[Shape], "{\"circ\":{\"radius\":2.0}}")

  test("@name field round-trips"):
    assertEquals(MCodec[User].read(JsonBackend.input(User("bob", 30).toJson)), User("bob", 30))
