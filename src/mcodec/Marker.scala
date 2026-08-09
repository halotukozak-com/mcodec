package mcodec

trait Marker

object Marker:
  /**
   * Force-write marker: when present on the Output, `@transientDefault` omission is disabled so
   * every defaulted field is written. GenCodec `IgnoreTransientDefaultMarker` analogue.
   */
  case object IgnoreTransientDefaults extends Marker

extension (out: Output)
  def withMarkers(markers: Marker*): Output =
    if markers.isEmpty then out else MarkedOutput(out, markers.toSet)

private final class MarkedOutput(underlying: Output, markers: Set[Marker]) extends Output:
  def writeNull(): Unit = underlying.writeNull()
  def writeSimple(): SimpleOutput = underlying.writeSimple()
  def writeList(): ListOutput = MarkedListOutput(underlying.writeList(), markers)
  def writeObject(): ObjectOutput = MarkedObjectOutput(underlying.writeObject(), markers)
  override def hasMarker(marker: Marker): Boolean = markers.contains(marker) || underlying.hasMarker(marker)

private final class MarkedListOutput(underlying: ListOutput, markers: Set[Marker]) extends ListOutput:
  def writeElement(): Output = MarkedOutput(underlying.writeElement(), markers)
  def finish(): Unit = underlying.finish()
  override def declareSize(size: Int): Unit = underlying.declareSize(size)
  override def knownSize: Int = underlying.knownSize
  override def hasMarker(marker: Marker): Boolean = markers.contains(marker) || underlying.hasMarker(marker)

private final class MarkedObjectOutput(underlying: ObjectOutput, markers: Set[Marker]) extends ObjectOutput:
  def writeField(key: String): Output = MarkedOutput(underlying.writeField(key), markers)
  def finish(): Unit = underlying.finish()
  override def declareSize(size: Int): Unit = underlying.declareSize(size)
  override def knownSize: Int = underlying.knownSize
  override def hasMarker(marker: Marker): Boolean = markers.contains(marker) || underlying.hasMarker(marker)
