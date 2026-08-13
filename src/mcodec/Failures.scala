package halotukozak.mcodec

enum PathSegment:
  case Field(name: String)
  case Index(i: Int)
  case Key(key: String)
  case Case(name: String)

object PathSegment:
  def render(path: List[PathSegment]): String =
    val sb = new java.lang.StringBuilder
    var first = true
    path.foreach:
      case Field(n) =>
        if !first then sb.append('.')
        sb.append(n)
        first = false
      case Case(n) =>
        if !first then sb.append('.')
        sb.append(n)
        first = false
      case Index(i) => sb.append('[').append(i).append(']')
      case Key(k) => sb.append('[').append(k).append(']')
    sb.toString

class ReadFailure protected (
  val reason: String,
  val path: List[PathSegment],
  val rootType: String | Null,
  cause: Throwable | Null,
) extends RuntimeException(ReadFailure.render(reason, path, rootType), cause):

  def this(msg: String) = this(msg, Nil, null, null)
  def this(msg: String, cause: Throwable | Null) = this(msg, Nil, null, cause)

  // Subtype-preserving reconstruction: typed subclasses override to return their own
  // type so prepend/withRootType keep the dynamic subtype.
  protected def rebuild(
    reason: String,
    path: List[PathSegment],
    rootType: String | Null,
    cause: Throwable | Null,
  ): ReadFailure =
    new ReadFailure(reason, path, rootType, cause)

  def prepend(seg: PathSegment): ReadFailure =
    val keptCause = if cause == null then this else cause
    val newPath = seg :: path
    // A plain (untyped) failure adopts the location-typed subclass of the segment wrapping it;
    // a failure that already carries a semantic/location type keeps it.
    if getClass eq classOf[ReadFailure] then ReadFailure.bySegment(seg, reason, newPath, rootType, keptCause)
    else rebuild(reason, newPath, rootType, keptCause)

  def withRootType(t: String): ReadFailure =
    rebuild(reason, path, t, cause)

  override def fillInStackTrace(): Throwable =
    if cause == null then super.fillInStackTrace() else this

object ReadFailure:
  def render(reason: String, path: List[PathSegment], rootType: String | Null): String =
    val rt = rootType match
      case null => "value"
      case t => t
    if path.isEmpty then s"Failed to read $rt: $reason"
    else s"Failed to read $rt at ${PathSegment.render(path)}: $reason"

  private def bySegment(
    seg: PathSegment,
    reason: String,
    path: List[PathSegment],
    rootType: String | Null,
    cause: Throwable | Null,
  ): ReadFailure = seg match
    case _: PathSegment.Field => new FieldReadFailed(reason, path, rootType, cause)
    case _: PathSegment.Index => new ListElementReadFailed(reason, path, rootType, cause)
    case _: PathSegment.Key => new MapFieldReadFailed(reason, path, rootType, cause)
    case _: PathSegment.Case => new CaseReadFailed(reason, path, rootType, cause)

final class MissingField protected (
  reason: String,
  path: List[PathSegment],
  rootType: String | Null,
  cause: Throwable | Null,
) extends ReadFailure(reason, path, rootType, cause):
  def this(msg: String) = this(msg, Nil, null, null)
  override protected def rebuild(
    reason: String,
    path: List[PathSegment],
    rootType: String | Null,
    cause: Throwable | Null,
  ): ReadFailure =
    new MissingField(reason, path, rootType, cause)

final class UnknownCase protected (
  reason: String,
  path: List[PathSegment],
  rootType: String | Null,
  cause: Throwable | Null,
) extends ReadFailure(reason, path, rootType, cause):
  def this(msg: String) = this(msg, Nil, null, null)
  override protected def rebuild(
    reason: String,
    path: List[PathSegment],
    rootType: String | Null,
    cause: Throwable | Null,
  ): ReadFailure =
    new UnknownCase(reason, path, rootType, cause)

final class CaseReadFailed private[mcodec] (
  reason: String,
  path: List[PathSegment],
  rootType: String | Null,
  cause: Throwable | Null,
) extends ReadFailure(reason, path, rootType, cause):
  def this(msg: String) = this(msg, Nil, null, null)
  def this(msg: String, cause: Throwable | Null) = this(msg, Nil, null, cause)
  override protected def rebuild(
    reason: String,
    path: List[PathSegment],
    rootType: String | Null,
    cause: Throwable | Null,
  ): ReadFailure =
    new CaseReadFailed(reason, path, rootType, cause)

// No discriminator / empty wrapper where a case was required.
final class MissingCase protected (
  reason: String,
  path: List[PathSegment],
  rootType: String | Null,
  cause: Throwable | Null,
) extends ReadFailure(reason, path, rootType, cause):
  def this(msg: String) = this(msg, Nil, null, null)
  override protected def rebuild(
    reason: String,
    path: List[PathSegment],
    rootType: String | Null,
    cause: Throwable | Null,
  ): ReadFailure =
    new MissingCase(reason, path, rootType, cause)

// Discriminated-sum wrapper object had more than the single expected field.
final class NotSingleField protected (
  reason: String,
  path: List[PathSegment],
  rootType: String | Null,
  cause: Throwable | Null,
) extends ReadFailure(reason, path, rootType, cause):
  def this(msg: String) = this(msg, Nil, null, null)
  override protected def rebuild(
    reason: String,
    path: List[PathSegment],
    rootType: String | Null,
    cause: Throwable | Null,
  ): ReadFailure =
    new NotSingleField(reason, path, rootType, cause)

// Location-typed failures produced when a plain failure is wrapped by a path segment
// (Field/Index/Key). Case-segment wrapping produces `CaseReadFailed` above.
final class FieldReadFailed private[mcodec] (
  reason: String,
  path: List[PathSegment],
  rootType: String | Null,
  cause: Throwable | Null,
) extends ReadFailure(reason, path, rootType, cause):
  def this(msg: String) = this(msg, Nil, null, null)
  override protected def rebuild(
    reason: String,
    path: List[PathSegment],
    rootType: String | Null,
    cause: Throwable | Null,
  ): ReadFailure =
    new FieldReadFailed(reason, path, rootType, cause)

final class ListElementReadFailed private[mcodec] (
  reason: String,
  path: List[PathSegment],
  rootType: String | Null,
  cause: Throwable | Null,
) extends ReadFailure(reason, path, rootType, cause):
  def this(msg: String) = this(msg, Nil, null, null)
  override protected def rebuild(
    reason: String,
    path: List[PathSegment],
    rootType: String | Null,
    cause: Throwable | Null,
  ): ReadFailure =
    new ListElementReadFailed(reason, path, rootType, cause)

final class MapFieldReadFailed private[mcodec] (
  reason: String,
  path: List[PathSegment],
  rootType: String | Null,
  cause: Throwable | Null,
) extends ReadFailure(reason, path, rootType, cause):
  def this(msg: String) = this(msg, Nil, null, null)
  override protected def rebuild(
    reason: String,
    path: List[PathSegment],
    rootType: String | Null,
    cause: Throwable | Null,
  ): ReadFailure =
    new MapFieldReadFailed(reason, path, rootType, cause)

inline def withSegment[A](seg: PathSegment)(inline body: A): A =
  try body
  catch case rf: ReadFailure => throw rf.prepend(seg)

class WriteFailure(msg: String, cause: Throwable | Null) extends RuntimeException(msg, cause):
  def this(msg: String) = this(msg, null)
