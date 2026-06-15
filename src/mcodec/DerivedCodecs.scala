package mcodec

final class Deferred[T] extends MCodec[T]:
  private[mcodec] var underlying: MCodec[T] | Null = compiletime.uninitialized

  def read(in: Input): T = underlying match
    case null => throw ReadFailure("deferred codec used before initialization")
    case u => u.read(in)

  def write(out: Output, v: T): Unit = underlying match
    case null => throw WriteFailure("deferred codec used before initialization")
    case u => u.write(out, v)

final class ProductCodec[T](
  typeName: String,
  labels: Array[String],
  childCodecsByName: => Array[MCodec[Any]],
  defaults: Array[Option[Any]],
  optionalFlags: Array[Boolean],
  isOption: Array[Boolean],
  fromArray: Array[Any] => T,
) extends ObjectCodec[T]:
  private lazy val childCodecs = childCodecsByName
  private val byName = labels.zipWithIndex.toMap
  def writeObject(out: ObjectOutput, value: T): Unit =
    out.declareSize(labels.length)
    val p = value.asInstanceOf[Product]
    var i = 0
    while i < labels.length do
      val v = p.productElement(i)
      if (optionalFlags(i) || isOption(i)) && (v == None) then ()
      else childCodecs(i).write(out.writeField(labels(i)), v)
      i += 1
  def readObject(in: ObjectInput): T = try
    val values = new Array[Any](labels.length)
    val seen = new Array[Boolean](labels.length)
    while in.hasNext do
      val f = in.nextField()
      byName.get(f.fieldName) match
        case Some(idx) =>
          values(idx) = withSegment(PathSegment.Field(f.fieldName)):
            childCodecs(idx).read(f)
          seen(idx) = true
        case None => f.skip()
    var i = 0
    while i < labels.length do
      if !seen(i) then
        values(i) = defaults(i) match
          case Some(d) => d
          case None =>
            if optionalFlags(i) || isOption(i) then None
            else throw ReadFailure(s"missing field: ${labels(i)}")
      i += 1
    fromArray(values)
  catch case rf: ReadFailure => throw rf.withRootType(typeName)

final class NestedSumCodec[T](
  typeName: String,
  caseNames: Array[String],
  caseCodecs: => Array[MCodec[Any]],
  ordinalOf: T => Int,
) extends ObjectCodec[T]:
  // ADT-04 flat-mode reuse hook: nested mode is structurally collision-free
  // (discriminator is the wrapper key), so this is vacuous now but guards Phase 7 flat mode.
  if caseNames.distinct.length != caseNames.length then
    throw ReadFailure(s"$typeName: duplicate case discriminator names: ${caseNames.mkString(", ")}")
  private lazy val codecs = caseCodecs
  def writeObject(out: ObjectOutput, value: T): Unit =
    val idx = ordinalOf(value)
    out.declareSize(1)
    codecs(idx).write(out.writeField(caseNames(idx)), value)
  def readObject(in: ObjectInput): T = try
    if !in.hasNext then throw ReadFailure(s"$typeName: expected single-field case wrapper, got empty object")
    val f = in.nextField()
    val idx = caseNames.indexOf(f.fieldName)
    if idx < 0 then throw ReadFailure(s"unknown case ${f.fieldName}")
    val result =
      withSegment(PathSegment.Case(f.fieldName)):
        codecs(idx).read(f)
      .asInstanceOf[T]
    if in.hasNext then throw ReadFailure(s"$typeName: expected single-field case wrapper, got extra fields")
    result
  catch case rf: ReadFailure => throw rf.withRootType(typeName)

final class SingletonCodec[T](value: T) extends ObjectCodec[T]:
  def writeObject(out: ObjectOutput, v: T): Unit = ()
  def readObject(in: ObjectInput): T = value

final class TransparentCodec[T, E](wrap: E => T, unwrap: T => E, inner: MCodec[E]) extends MCodec[T]:
  def read(in: Input): T = wrap(inner.read(in))
  def write(out: Output, v: T): Unit = inner.write(out, unwrap(v))
