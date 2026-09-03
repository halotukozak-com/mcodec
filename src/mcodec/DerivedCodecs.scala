package halotukozak.mcodec

import halotukozak.made.NotExists

private val Absent: AnyRef = new AnyRef

/**
 * Open-addressed `String -> Int` field lookup: no boxing, no `Option`, immutable once built.
 * Replaces `labels.zipWithIndex.toMap` on the read hot path.
 */
private final class FieldLookup(names: Array[String]):
  private val mask =
    var c = 8
    while c < names.length * 2 do c <<= 1
    c - 1
  private val slots = new Array[Int](mask + 1) // 0 = empty; else (field index + 1)
  locally:
    var i = 0
    while i < names.length do
      var s = names(i).hashCode & mask
      while slots(s) != 0 do s = (s + 1) & mask
      slots(s) = i + 1
      i += 1

  /** Field index for `name`, or -1 if it is not a known field. */
  def indexOf(name: String): Int =
    var s = name.hashCode & mask
    var e = slots(s)
    while e != 0 do
      if names(e - 1) == name then return e - 1
      s = (s + 1) & mask
      e = slots(s)
    -1

final class ProductCodec[T](
  typeName: String,
  labels: Array[String],
  childCodecsByName: => Array[MCodec[Any]],
  defaults: Array[Any | NotExists],
  optionalFlags: Array[Boolean],
  isOption: Array[Boolean],
  transientDefaultFlags: Array[Boolean],
  outOfOrderFlags: Array[Boolean],
  fromArray: Array[Any] => T,
  genLabels: Array[String],
  genGetters: Array[T => Any],
  genCodecsByName: => Array[MCodec[Any]],
) extends ObjectCodec[T],
    SizedCodec[T]:
  private[mcodec] def bodyFieldNames: Array[String] = labels
  private lazy val childCodecs = childCodecsByName
  private lazy val genCodecs = genCodecsByName
  private val fields = new FieldLookup(labels)

  // Single source of truth for both counting and writing. Structural `==` is the
  // transient-default contract; Array-typed defaults compare by reference (rarely omitted).
  // `forced` (the IgnoreTransientDefaults marker) disables the transient-default omission.
  private def isOmitted(i: Int, p: Product, forced: Boolean): Boolean =
    ((optionalFlags(i) || isOption(i)) && (p.productElement(i) == None)) ||
      (!forced && transientDefaultFlags(i) && defaults(i) != null && defaults(i) == p.productElement(i))

  private[mcodec] def writtenFieldCount(value: T, forced: Boolean): Int =
    val p = value.asInstanceOf[Product]
    var omitted = 0
    var i = 0
    while i < labels.length do
      if isOmitted(i, p, forced) then omitted += 1
      i += 1
    (labels.length - omitted) + genLabels.length

  def sizeOf(value: T): Int = writtenFieldCount(value, false)

  private[mcodec] def writeFieldsOnly(out: ObjectOutput, value: T, forced: Boolean): Unit =
    val p = value.asInstanceOf[Product]
    var i = 0
    while i < labels.length do
      val v = p.productElement(i)
      if isOmitted(i, p, forced) then ()
      else childCodecs(i).write(out.writeField(labels(i)), v)
      i += 1
    var g = 0
    while g < genLabels.length do
      genCodecs(g).write(out.writeField(genLabels(g)), genGetters(g)(value))
      g += 1

  def writeObject(out: ObjectOutput, value: T): Unit =
    val forced = out.hasMarker(Marker.IgnoreTransientDefaults)
    out.declareSize(writtenFieldCount(value, forced))
    writeFieldsOnly(out, value, forced)

  def readObject(in: ObjectInput): T = try
    val n = labels.length
    // `Absent` sentinel marks a still-unfilled slot, so an explicitly-read `null`
    // (a `T | Null` field) is distinguishable from a missing field without a second array.
    val values = Array.fill[Any](n)(Absent)
    val deferred = scala.collection.mutable.ArrayBuffer.empty[(Int, CapturedValue)]
    while in.hasNext do
      val f = in.nextField()
      val idx = fields.indexOf(f.fieldName)
      if idx < 0 then f.skip()
      else if outOfOrderFlags(idx) then deferred += (idx -> CapturedValue.capture(f))
      else
        values(idx) = withSegment(PathSegment.Field(f.fieldName)):
          childCodecs(idx).read(f)
    deferred.foreach: (idx, cv) =>
      values(idx) = withSegment(PathSegment.Field(labels(idx))):
        childCodecs(idx).read(new CapturedInput(cv))
    var i = 0
    while i < n do
      if values(i).asInstanceOf[AnyRef] eq Absent then
        values(i) = defaults(i) match
          case NotExists =>
            if optionalFlags(i) || isOption(i) then None
            else throw new MissingField(s"missing field: ${labels(i)}")
          case d => d
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
      else throw new MissingCase(s"$typeName: expected single-field case wrapper, got empty object")
    else
      val f = in.nextField()
      val idx = caseNames.indexOf(f.fieldName)
      if idx < 0 then
        if defaultCaseIdx >= 0 then
          f.skip()
          defaultRead()
        else throw new UnknownCase(s"unknown case ${f.fieldName}")
      else
        val result =
          withSegment(PathSegment.Case(f.fieldName)):
            codecs(idx).read(f)
          .asInstanceOf[T]
        if in.hasNext then throw new NotSingleField(s"$typeName: expected single-field case wrapper, got extra fields")
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

  // `forced` (IgnoreTransientDefaults marker) propagates into the product body so its
  // `@transientDefault` fields are force-written and the declared size still matches.
  private def bodySize(value: T, forced: Boolean): Int =
    ordinalOf(value) match
      case idx =>
        codecs(idx) match
          case p: ProductCodec[Any @unchecked] => p.writtenFieldCount(value, forced)
          case s: SizedCodec[Any @unchecked] => s.sizeOf(value)
          case _ => 0

  def sizeOf(value: T): Int = 1 + bodySize(value, false)

  def writeObject(out: ObjectOutput, value: T): Unit =
    val idx = ordinalOf(value)
    val _ = guardChecked
    val forced = out.hasMarker(Marker.IgnoreTransientDefaults)
    out.declareSize(1 + bodySize(value, forced))
    out.writeField(caseFieldName).writeSimple().writeString(caseNames(idx))
    codecs(idx) match
      case p: ProductCodec[Any @unchecked] => p.writeFieldsOnly(out, value, forced)
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
      caseName match
        case null => throw new MissingCase(s"$typeName: missing discriminator field '$caseFieldName'")
        case cn => throw new UnknownCase(s"$typeName: unknown case $cn")
    val replay = new ReplayObjectInput(buf.result())
    withSegment(PathSegment.Case(caseNames(idx))):
      codecs(idx).asInstanceOf[ObjectCodec[Any]].readObject(replay).asInstanceOf[T]
  catch case rf: ReadFailure => throw rf.withRootType(typeName)

final class EnumStringCodec[T](
  typeName: String,
  caseNames: Array[String],
  caseCodecs: => Array[MCodec[Any]],
  ordinalOf: T => Int,
) extends SimpleCodec[T]:
  private lazy val values: Array[Any] = caseCodecs.map:
    case s: SingletonCodec[?] => s.value
    case _ => throw ReadFailure(s"$typeName: @stringEnum requires every case to be a singleton")
  def writeSimple(out: SimpleOutput, value: T): Unit =
    out.writeString(caseNames(ordinalOf(value)))
  def readSimple(in: SimpleInput): T =
    val s = in.readString()
    val idx = caseNames.indexOf(s)
    if idx < 0 then throw ReadFailure(s"$typeName: unknown case $s")
    else values(idx).asInstanceOf[T]

final class SingletonCodec[T](val value: T) extends ObjectCodec[T], SizedCodec[T]:
  private[mcodec] def bodyFieldNames: Array[String] = Array.empty
  def sizeOf(value: T): Int = 0
  def writeObject(out: ObjectOutput, v: T): Unit = ()
  def readObject(in: ObjectInput): T = value

final class TransparentCodec[T, E](wrap: E => T, unwrap: T => E, inner: MCodec[E]) extends MCodec[T]:
  def read(in: Input): T = wrap(inner.read(in))
  def write(out: Output, v: T): Unit = inner.write(out, unwrap(v))
