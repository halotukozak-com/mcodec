package mcodec

import made.*

import scala.annotation.publicInBinary

private[mcodec] final class Deferred[T] extends MCodec[T]:
  private[mcodec] var underlying: MCodec[T] | Null = compiletime.uninitialized
  def read(in: Input): T = underlying match
    case null => throw ReadFailure("deferred codec used before initialization")
    case u => u.read(in)
  def write(out: Output, v: T): Unit = underlying match
    case null => throw WriteFailure("deferred codec used before initialization")
    case u => u.write(out, v)

trait Derivation:
  transparent inline def derivedRec[T: Made.Of as m]: MCodec[T] =
    val deferred = new Deferred[T]
    given self: MCodec[T] = deferred
    val built = deriveDispatch[T](m)
    deferred.underlying = built
    built

  transparent inline def deriveDispatch[T](m: Made.Of[T]): MCodec[T] = inline m match
    case pm: Made.ProductOf[T] => deriveProduct[T](pm)
    case sm: Made.SumOf[T] => deriveSum[T](sm)
    case tm: Made.TransparentOf[T] => deriveTransparent[T](tm, compiletime.summonInline[MCodec[tm.ElemType]])
    case gm: Made.SingletonOf[T] => deriveSingleton[T](gm)

  inline def deriveProduct[T](m: Made.ProductOf[T]): MCodec[T] = mkProductCodec[T](
    compiletime.constValueTuple[m.ElemLabels].toArrayOf[String],
    compiletime.summonAll[Tuple.Map[m.ElemTypes, MCodec]].toArrayOf[MCodec[Any]](using containsOnly.refl),
    m.fromUnsafeArray,
  )

  @publicInBinary private[Derivation] def mkProductCodec[T](
    labels: Array[String],
    childCodecsByName: => Array[MCodec[Any]],
    fromArray: Array[Any] => T,
  ): ObjectCodec[T] = new:
    private lazy val childCodecs = childCodecsByName
    private val byName = labels.zipWithIndex.toMap
    def writeObject(out: ObjectOutput, value: T): Unit =
      out.declareSize(labels.length)
      val p = value.asInstanceOf[Product]
      var i = 0
      while i < labels.length do
        childCodecs(i).write(out.writeField(labels(i)), p.productElement(i))
        i += 1
    def readObject(in: ObjectInput): T =
      val values = new Array[Any](labels.length)
      val seen = new Array[Boolean](labels.length)
      while in.hasNext do
        val f = in.nextField()
        byName.get(f.fieldName) match
          case Some(idx) =>
            values(idx) = childCodecs(idx).read(f)
            seen(idx) = true
          case None => f.skip()
      var i = 0
      while i < labels.length do
        if !seen(i) then throw ReadFailure(s"missing field: ${labels(i)}")
        i += 1
      fromArray(values)

  inline def deriveSum[T](m: Made.SumOf[T]): MCodec[T] = mkNestedSumCodec[T](
    compiletime.constValue[m.Label],
    compiletime.constValueTuple[m.ElemLabels].toArrayOf[String],
    summonOrDeriveCases[m.ElemTypes].toArray,
    m.ordinal,
  )

  inline def summonOrDeriveCases[Elems <: Tuple]: List[MCodec[Any]] = inline compiletime.erasedValue[Elems] match
    case _: EmptyTuple => Nil
    case _: (head *: tail) =>
      val c = compiletime.summonFrom:
        case given MCodec[`head`] => compiletime.summonInline[MCodec[head]]
        case _ => MCodec.derived[head]

      c.asInstanceOf[MCodec[Any]] :: summonOrDeriveCases[tail]

  @publicInBinary private[Derivation] def mkNestedSumCodec[T](
    typeName: String,
    caseNames: Array[String],
    caseCodecs: => Array[MCodec[Any]],
    ordinalOf: T => Int,
  ): ObjectCodec[T] = new:
    // ADT-04 flat-mode reuse hook: nested mode is structurally collision-free
    // (discriminator is the wrapper key), so this is vacuous now but guards Phase 7 flat mode.
    if caseNames.distinct.length != caseNames.length then
      throw ReadFailure(s"$typeName: duplicate case discriminator names: ${caseNames.mkString(", ")}")
    private lazy val codecs = caseCodecs
    def writeObject(out: ObjectOutput, value: T): Unit =
      val idx = ordinalOf(value)
      out.declareSize(1)
      codecs(idx).write(out.writeField(caseNames(idx)), value)
    def readObject(in: ObjectInput): T =
      if !in.hasNext then throw ReadFailure(s"$typeName: expected single-field case wrapper, got empty object")
      val f = in.nextField()
      val idx = caseNames.indexOf(f.fieldName)
      if idx < 0 then throw ReadFailure(s"$typeName: unknown case ${f.fieldName}")
      val result = codecs(idx).read(f).asInstanceOf[T]
      if in.hasNext then throw ReadFailure(s"$typeName: expected single-field case wrapper, got extra fields")
      result

  inline def deriveSingleton[T](m: Made.SingletonOf[T]): MCodec[T] =
    mkSingletonCodec[T](m.value)

  @publicInBinary private[Derivation] def mkSingletonCodec[T](value: T): ObjectCodec[T] = new:
    def writeObject(out: ObjectOutput, v: T): Unit = ()
    def readObject(in: ObjectInput): T = value

  inline def deriveTransparent[T](m: Made.TransparentOf[T], inner: MCodec[m.ElemType]): MCodec[T] = MCodec.create[T](
    in => m.wrap(inner.read(in)),
    (out, v) => inner.write(out, m.unwrap(v)),
  )
