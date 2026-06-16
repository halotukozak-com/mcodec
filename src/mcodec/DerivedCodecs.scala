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
  genLabels: Array[String],
  genGetters: Array[T => Any],
  genCodecsByName: => Array[MCodec[Any]],
) extends ObjectCodec[T],
    SizedCodec[T]:
  private[mcodec] def bodyFieldNames: Array[String] = labels
  private lazy val childCodecs = childCodecsByName
  private lazy val genCodecs = genCodecsByName
  private val byName = labels.zipWithIndex.toMap

  // Single source of truth for both counting and writing. A transient-default
  // clause slots in here additively without re-shaping the predicate.
  private def isOmitted(i: Int, p: Product): Boolean = (optionalFlags(i) || isOption(i)) && (p.productElement(i) == None)

  def sizeOf(value: T): Int =
    val p = value.asInstanceOf[Product]
    var omitted = 0
    var i = 0
    while i < labels.length do
      if isOmitted(i, p) then omitted += 1
      i += 1
    (labels.length - omitted) + genLabels.length

  private[mcodec] def writeFieldsOnly(out: ObjectOutput, value: T): Unit =
    val p = value.asInstanceOf[Product]
    var i = 0
    while i < labels.length do
      val v = p.productElement(i)
      if isOmitted(i, p) then ()
      else childCodecs(i).write(out.writeField(labels(i)), v)
      i += 1
    var g = 0
    while g < genLabels.length do
      genCodecs(g).write(out.writeField(genLabels(g)), genGetters(g)(value))
      g += 1

  def writeObject(out: ObjectOutput, value: T): Unit =
    out.declareSize(sizeOf(value))
    writeFieldsOnly(out, value)
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
  defaultCaseIdx: Int,
) extends ObjectCodec[T],
    SizedCodec[T]:
  // ADT-04 flat-mode reuse hook: nested mode is structurally collision-free
  // (discriminator is the wrapper key), so this is vacuous now but guards flat sum mode.
  if caseNames.distinct.length != caseNames.length then
    throw ReadFailure(s"$typeName: duplicate case discriminator names: ${caseNames.mkString(", ")}")
  private lazy val codecs = caseCodecs
  def sizeOf(value: T): Int = 1
  def writeObject(out: ObjectOutput, value: T): Unit =
    val idx = ordinalOf(value)
    out.declareSize(sizeOf(value))
    codecs(idx).write(out.writeField(caseNames(idx)), value)
  def readObject(in: ObjectInput): T = try
    if !in.hasNext then
      if defaultCaseIdx >= 0 then defaultRead()
      else throw ReadFailure(s"$typeName: expected single-field case wrapper, got empty object")
    else
      val f = in.nextField()
      val idx = caseNames.indexOf(f.fieldName)
      if idx < 0 then
        if defaultCaseIdx >= 0 then
          f.skip()
          defaultRead()
        else throw ReadFailure(s"unknown case ${f.fieldName}")
      else
        val result =
          withSegment(PathSegment.Case(f.fieldName)):
            codecs(idx).read(f)
          .asInstanceOf[T]
        if in.hasNext then throw ReadFailure(s"$typeName: expected single-field case wrapper, got extra fields")
        result
  catch case rf: ReadFailure => throw rf.withRootType(typeName)

  private def defaultRead(): T = codecs(defaultCaseIdx) match
    case s: SingletonCodec[?] => s.value.asInstanceOf[T]
    case _ =>
      throw ReadFailure(s"$typeName: @defaultCase fallback on absent/unknown discriminator requires a singleton case")

final class FlatSumCodec[T](
  typeName: String,
  caseNames: Array[String],
  caseCodecs: => Array[MCodec[Any]],
  ordinalOf: T => Int,
  caseFieldName: String,
  defaultCaseIdx: Int,
) extends ObjectCodec[T],
    SizedCodec[T]:
  if caseNames.distinct.length != caseNames.length then
    throw ReadFailure(s"$typeName: duplicate case discriminator names: ${caseNames.mkString(", ")}")
  private lazy val codecs = caseCodecs
  // ADT-04 LIVE: the flat discriminator key must not collide with any case body field name.
  private lazy val guardChecked =
    var i = 0
    while i < codecs.length do
      val fields = bodyFieldNames(codecs(i))
      if fields.contains(caseFieldName) then
        throw ReadFailure(
          s"$typeName: flat discriminator key '$caseFieldName' collides with field of case ${caseNames(i)}",
        )
      i += 1
    true

  private def bodyFieldNames(codec: MCodec[Any]): Array[String] = codec match
    case p: ProductCodec[?] => p.bodyFieldNames
    case s: SingletonCodec[?] => s.bodyFieldNames
    case _ => Array.empty

  def sizeOf(value: T): Int =
    val idx = ordinalOf(value)
    1 +
      (codecs(idx) match
        case s: SizedCodec[Any @unchecked] => s.sizeOf(value)
        case _ => 0)

  def writeObject(out: ObjectOutput, value: T): Unit =
    val idx = ordinalOf(value)
    val _ = guardChecked
    out.declareSize(sizeOf(value))
    out.writeField(caseFieldName).writeSimple().writeString(caseNames(idx))
    codecs(idx) match
      case p: ProductCodec[Any @unchecked] => p.writeFieldsOnly(out, value)
      case _: SingletonCodec[Any @unchecked] => ()
      case other => other.asInstanceOf[ObjectCodec[Any]].writeObject(out, value)

  def readObject(in: ObjectInput): T = try
    val _ = guardChecked
    val buf = Vector.newBuilder[(String, CapturedValue)]
    var caseName: String | Null = null
    while in.hasNext do
      val f = in.nextField()
      if f.fieldName == caseFieldName then caseName = f.readSimple().readString()
      else buf += (f.fieldName -> CapturedValue.capture(f))
    val idx = caseName match
      case null => defaultCaseIdx
      case cn =>
        val i = caseNames.indexOf(cn)
        if i < 0 then defaultCaseIdx else i
    if idx < 0 then
      throw ReadFailure(
        caseName match
          case null => s"$typeName: missing discriminator field '$caseFieldName'"
          case cn => s"$typeName: unknown case $cn",
      )
    val replay = new ReplayObjectInput(buf.result())
    withSegment(PathSegment.Case(caseNames(idx))):
      codecs(idx).asInstanceOf[ObjectCodec[Any]].readObject(replay).asInstanceOf[T]
  catch case rf: ReadFailure => throw rf.withRootType(typeName)

final class SingletonCodec[T](val value: T) extends ObjectCodec[T], SizedCodec[T]:
  private[mcodec] def bodyFieldNames: Array[String] = Array.empty
  def sizeOf(value: T): Int = 0
  def writeObject(out: ObjectOutput, v: T): Unit = ()
  def readObject(in: ObjectInput): T = value

final class TransparentCodec[T, E](wrap: E => T, unwrap: T => E, inner: MCodec[E]) extends MCodec[T]:
  def read(in: Input): T = wrap(inner.read(in))
  def write(out: Output, v: T): Unit = inner.write(out, unwrap(v))
