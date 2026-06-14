package mcodec

class JsonWriterTest extends munit.FunSuite:

  private def emit(f: JsonOutput => Unit): String =
    val sb = new java.lang.StringBuilder
    f(new JsonOutput(sb))
    sb.toString

  test("null") {
    assertEquals(emit(_.writeNull()), "null")
  }

  test("booleans") {
    assertEquals(emit(_.writeBoolean(true)), "true")
    assertEquals(emit(_.writeBoolean(false)), "false")
  }

  test("int and long") {
    assertEquals(emit(_.writeInt(1)), "1")
    assertEquals(emit(_.writeInt(-42)), "-42")
    assertEquals(emit(_.writeLong(Long.MaxValue)), "9223372036854775807")
  }

  test("bigint and bigdecimal as number literals") {
    assertEquals(emit(_.writeBigInt(BigInt("123456789012345678901234567890"))), "123456789012345678901234567890")
    assertEquals(emit(_.writeBigDecimal(BigDecimal("0.1"))), "0.1")
  }

  test("string escaping: quotes and backslash") {
    assertEquals(emit(_.writeString("a\"b")), "\"a\\\"b\"")
    assertEquals(emit(_.writeString("a\\b")), "\"a\\\\b\"")
  }

  test("string escaping: named control chars") {
    assertEquals(emit(_.writeString("\b\f\n\r\t")), "\"\\b\\f\\n\\r\\t\"")
  }

  test("string escaping: other control char uses \\u00XX lowercase") {
    assertEquals(emit(_.writeString(1.toChar.toString)), "\"\\u0001\"")
    assertEquals(emit(_.writeString(0x1f.toChar.toString)), "\"\\u001f\"")
  }

  test("forward slash is not escaped") {
    assertEquals(emit(_.writeString("a/b")), "\"a/b\"")
  }

  test("astral char (emoji) emitted verbatim inside quotes") {
    assertEquals(emit(_.writeString("😀")), "\"😀\"")
  }

  test("non-finite doubles are quoted") {
    assertEquals(emit(_.writeDouble(Double.NaN)), "\"" + Double.NaN.toString + "\"")
    assertEquals(emit(_.writeDouble(Double.PositiveInfinity)), "\"" + Double.PositiveInfinity.toString + "\"")
  }

  test("finite double emitted bare") {
    assertEquals(emit(_.writeDouble(1.5)), "1.5")
  }

  test("list of [1,2]") {
    val s = emit { out =>
      val lo = out.writeList()
      lo.writeElement().writeSimple().writeInt(1)
      lo.writeElement().writeSimple().writeInt(2)
      lo.finish()
    }
    assertEquals(s, "[1,2]")
  }

  test("empty list emits []") {
    val s = emit { out =>
      val lo = out.writeList()
      lo.finish()
    }
    assertEquals(s, "[]")
  }

  test("object {\"a\":1}") {
    val s = emit { out =>
      val oo = out.writeObject()
      oo.writeField("a").writeSimple().writeInt(1)
      oo.finish()
    }
    assertEquals(s, "{\"a\":1}")
  }

  test("empty object emits {}") {
    val s = emit { out =>
      val oo = out.writeObject()
      oo.finish()
    }
    assertEquals(s, "{}")
  }

  test("nested object with escaped key") {
    val s = emit { out =>
      val oo = out.writeObject()
      oo.writeField("a\"b").writeSimple().writeBoolean(true)
      oo.finish()
    }
    assertEquals(s, "{\"a\\\"b\":true}")
  }
