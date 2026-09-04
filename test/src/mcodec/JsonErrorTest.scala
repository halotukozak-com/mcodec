package halotukozak.mcodec

class JsonErrorTest extends munit.FunSuite, JsonConv:

  private def readInput[T: MCodec as codec](json: String): T =
    codec.read(new JsonInput(new JsonReader(json)))

  // POSITION (JSON-03): offending position reported as 1-based (at L:C).
  test("number-at-start error reports position (at 1:1)") {
    val rf = intercept[ReadFailure](readInput[Int]("abc"))
    assert(rf.getMessage.contains("(at "), s"missing position suffix: ${rf.getMessage}")
    assert(rf.getMessage.contains("(at 1:1)"), s"wrong position: ${rf.getMessage}")
  }

  test("error after newline reports position (at 2:3)") {
    val rf = intercept[ReadFailure](readInput[Boolean]("\n  xyz"))
    assert(rf.getMessage.contains("(at "), s"missing position suffix: ${rf.getMessage}")
    assert(rf.getMessage.contains("(at 2:3)"), s"wrong position: ${rf.getMessage}")
  }

  // TRAILING DATA (JSON-03): non-whitespace after a complete top-level value is rejected.
  // Driven through finishTopLevel(), added in 06-02 and routed via JsonConv.fromJson.
  test("trailing data after top-level value is rejected") {
    val rf = intercept[ReadFailure](fromJson[Int]("1 2"))
    assert(rf.getMessage.contains("trailing data"), s"expected trailing-data message: ${rf.getMessage}")
  }

  test("trailing whitespace only is allowed") {
    assertEquals(fromJson[Int]("1   "), 1)
  }

  // EOF / TRUNCATION (JSON-03): MUST surface as ReadFailure, NOT StringIndexOutOfBoundsException,
  // and MUST NOT hang. Today the unguarded charAt/substring sites leak a SIOOBE, so each
  // intercept[ReadFailure] below fails RED (MUnit reports the unexpected SIOOBE type).
  test("unterminated string -> ReadFailure (not SIOOBE)") {
    val rf = intercept[ReadFailure](readInput[String]("\"abc"))
    assert(rf.getMessage.contains("unterminated string"), s"got: ${rf.getMessage}")
  }

  test("truncated \\u escape -> ReadFailure (not SIOOBE)") {
    val rf = intercept[ReadFailure](readInput[String]("\"\\u12\""))
    assert(rf.getMessage.contains("truncated"), s"got: ${rf.getMessage}")
  }

  test("unterminated container -> ReadFailure (not SIOOBE)") {
    intercept[ReadFailure](readInput[List[Int]]("[1,2"))
  }

  test("empty input -> ReadFailure (not SIOOBE)") {
    intercept[ReadFailure](readInput[Int](""))
  }
