package mcodec

class JsonReaderTest extends munit.FunSuite:

  private def read[T](json: String)(f: JsonInput => T): T =
    f(new JsonInput(new JsonReader(json)))

  test("int and long parse") {
    assertEquals(read("1")(_.readInt()), 1)
    assertEquals(read("-42")(_.readInt()), -42)
    assertEquals(read("9223372036854775807")(_.readLong()), Long.MaxValue)
  }

  test("long > 2^53 parses exactly (not via Double)") {
    assertEquals(read("9007199254740993")(_.readLong()), 9007199254740993L)
  }

  test("bigdecimal from lexeme is exact") {
    assertEquals(read("0.1")(_.readBigDecimal()), BigDecimal("0.1"))
    assertEquals(read("1.00")(_.readBigDecimal()), BigDecimal("1.00"))
  }

  test("huge bigint parses exactly") {
    val big = "123456789012345678901234567890123456789"
    assertEquals(read(big)(_.readBigInt()), BigInt(big))
  }

  test("double parses") {
    assertEquals(read("1.5")(_.readDouble()), 1.5)
    assertEquals(read("1e3")(_.readDouble()), 1000.0)
  }

  test("boolean and string parse") {
    assertEquals(read("true")(_.readBoolean()), true)
    assertEquals(read("false")(_.readBoolean()), false)
    assertEquals(read("\"hello\"")(_.readString()), "hello")
  }

  test("string unescaping including \\uXXXX") {
    assertEquals(read("\"a\\\"b\\\\c\\n\"")(_.readString()), "a\"b\\c\n")
    assertEquals(read("\"\\u0041\"")(_.readString()), "A")
  }

  test("emoji string (astral via surrogate pair) round-trips") {
    assertEquals(read("\"😀\"")(_.readString()), "😀")
    assertEquals(read("\"\\ud83d\\ude00\"")(_.readString()), "😀")
  }

  test("readNull on null consumes and returns true") {
    assertEquals(read("null")(_.readNull()), true)
  }

  test("readNull on value returns false and leaves cursor") {
    val in = new JsonInput(new JsonReader("5"))
    assertEquals(in.readNull(), false)
    assertEquals(in.readInt(), 5)
  }

  test("leading whitespace tolerated") {
    assertEquals(read("   42")(_.readInt()), 42)
  }

  test("skip scalar then read next via reader") {
    val r = new JsonReader("[1,2]")
    val li = new JsonInput(r).readList()
    assert(li.hasNext)
    li.nextElement().skip()
    assert(li.hasNext)
    assertEquals(li.nextElement().asInstanceOf[JsonInput].readInt(), 2)
  }

  test("skip nested object keeps cursor synced") {
    val r = new JsonReader("[{\"a\":1,\"b\":[1,2]},7]")
    val li = new JsonInput(r).readList()
    assert(li.hasNext)
    li.nextElement().skip()
    assert(li.hasNext)
    assertEquals(li.nextElement().asInstanceOf[JsonInput].readInt(), 7)
  }

  test("skip nested array keeps cursor synced") {
    val r = new JsonReader("[[2,3],4]")
    val li = new JsonInput(r).readList()
    assert(li.hasNext)
    li.nextElement().skip()
    assert(li.hasNext)
    assertEquals(li.nextElement().asInstanceOf[JsonInput].readInt(), 4)
  }

  test("list iteration over [1,2,3]") {
    val li = new JsonInput(new JsonReader("[1,2,3]")).readList()
    val b = List.newBuilder[Int]
    while li.hasNext do b += li.nextElement().asInstanceOf[JsonInput].readInt()
    assertEquals(b.result(), List(1, 2, 3))
  }

  test("empty list iterates zero") {
    val li = new JsonInput(new JsonReader("[]")).readList()
    assertEquals(li.hasNext, false)
  }

  test("object iteration over {\"a\":1,\"b\":2}") {
    val oi = new JsonInput(new JsonReader("{\"a\":1,\"b\":2}")).readObject()
    val b = List.newBuilder[(String, Int)]
    while oi.hasNext do
      val f = oi.nextField()
      b += f.fieldName -> f.asInstanceOf[JsonInput].readInt()
    assertEquals(b.result(), List("a" -> 1, "b" -> 2))
  }

  test("empty object iterates zero") {
    val oi = new JsonInput(new JsonReader("{}")).readObject()
    assertEquals(oi.hasNext, false)
  }

  test("nested: object with a list value") {
    val oi = new JsonInput(new JsonReader("{\"k\":[1,2]}")).readObject()
    assert(oi.hasNext)
    val f = oi.nextField()
    assertEquals(f.fieldName, "k")
    val li = f.readList()
    val b = List.newBuilder[Int]
    while li.hasNext do b += li.nextElement().asInstanceOf[JsonInput].readInt()
    assertEquals(b.result(), List(1, 2))
    assertEquals(oi.hasNext, false)
  }

  test("partial read then skipRemaining lands past closer") {
    val r = new JsonReader("[{\"a\":1,\"b\":2,\"c\":3},9]")
    val li = new JsonInput(r).readList()
    assert(li.hasNext)
    val oi = li.nextElement().readObject()
    assert(oi.hasNext)
    val f = oi.nextField()
    assertEquals(f.fieldName, "a")
    assertEquals(f.asInstanceOf[JsonInput].readInt(), 1)
    oi.skipRemaining()
    assert(li.hasNext)
    assertEquals(li.nextElement().asInstanceOf[JsonInput].readInt(), 9)
  }

  test("getNextNamedField ordered scan") {
    val oi = new JsonInput(new JsonReader("{\"a\":1}")).readObject()
    assert(oi.hasNext)
    val f = oi.getNextNamedField("a")
    assertEquals(f.fieldName, "a")
    assertEquals(f.asInstanceOf[JsonInput].readInt(), 1)
  }
