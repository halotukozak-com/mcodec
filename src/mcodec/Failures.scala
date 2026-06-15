package mcodec

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

class ReadFailure private (
  val reason: String,
  val path: List[PathSegment],
  val rootType: String | Null,
  cause: Throwable | Null,
) extends RuntimeException(ReadFailure.render(reason, path, rootType), cause):

  def this(msg: String) = this(msg, Nil, null, null)
  def this(msg: String, cause: Throwable | Null) = this(msg, Nil, null, cause)

  def prepend(seg: PathSegment): ReadFailure =
    val keptCause = if cause == null then this else cause
    new ReadFailure(reason, seg :: path, rootType, keptCause)

  def withRootType(t: String): ReadFailure =
    new ReadFailure(reason, path, t, cause)

  override def fillInStackTrace(): Throwable =
    if cause == null then super.fillInStackTrace() else this

object ReadFailure:
  def render(reason: String, path: List[PathSegment], rootType: String | Null): String =
    val rt = rootType match
      case null => "value"
      case t => t
    if path.isEmpty then s"Failed to read $rt: $reason"
    else s"Failed to read $rt at ${PathSegment.render(path)}: $reason"

inline def withSegment[A](seg: PathSegment)(inline body: A): A =
  try body
  catch case rf: ReadFailure => throw rf.prepend(seg)

class WriteFailure(msg: String, cause: Throwable | Null) extends RuntimeException(msg, cause):
  def this(msg: String) = this(msg, null)
