package mcodec

object MarkerFixtures:
  object MarkerA extends Marker
  object MarkerB extends Marker

class MarkerTest extends munit.FunSuite:
  import MarkerFixtures.*

  private def freshOut: Output = new InMemoryOutput(_ => ())

  test("plain output reports no markers"):
    assert(!freshOut.hasMarker(MarkerA))

  test("withMarkers reports attached markers, not others"):
    val out = freshOut.withMarkers(MarkerA)
    assert(out.hasMarker(MarkerA))
    assert(!out.hasMarker(MarkerB))

  test("withMarkers with no markers returns output unchanged"):
    val out = freshOut
    assertEquals(out.withMarkers(), out)

  test("markers propagate recursively into nested object fields"):
    val out = freshOut.withMarkers(MarkerA)
    val obj = out.writeObject()
    assert(obj.hasMarker(MarkerA))
    val field = obj.writeField("x")
    assert(field.hasMarker(MarkerA))
    val nested = field.writeObject().writeField("y")
    assert(nested.hasMarker(MarkerA))

  test("markers propagate into list elements"):
    val out = freshOut.withMarkers(MarkerA)
    val lst = out.writeList()
    assert(lst.hasMarker(MarkerA))
    assert(lst.writeElement().hasMarker(MarkerA))
