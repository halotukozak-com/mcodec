package mcodec

import made.*
import made.annotation.optionalParam

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
    case tm: Made.TransparentOf[T] => deriveTransparent[T](tm)
    case gm: Made.SingletonOf[T] => deriveSingleton[T](gm)

  inline def deriveProduct[T](m: Made.ProductOf[T]): MCodec[T] = ProductCodec[T](
    compiletime.constValue[m.Label],
    compiletime.constValueTuple[m.ElemLabels].toArrayOf[String],
    compiletime.summonAll[Tuple.Map[m.ElemTypes, MCodec]].toArrayOf[MCodec[Any]](using containsOnly.refl),
    m.elems
      .mapAs[MadeFieldElem][[e] =>> Option[Any]]([e <: MadeFieldElem] => elem => elem.default)
      .toArrayOf[Option[Any]],
    m.elems.hasAnnotations[optionalParam].toArrayOf[Boolean](using containsOnly.refl),
    isOptionFlags[m.ElemTypes].toArray,
    m.fromUnsafeArray,
  )

  inline def isOptionFlags[Elems <: Tuple]: List[Boolean] = inline compiletime.erasedValue[Elems] match
    case _: EmptyTuple => Nil
    case _: (Option[?] *: tail) => true :: isOptionFlags[tail]
    case _: (head *: tail) => false :: isOptionFlags[tail]

  inline def deriveSum[T](m: Made.SumOf[T]): MCodec[T] = NestedSumCodec[T](
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

  inline def deriveSingleton[T](m: Made.SingletonOf[T]): MCodec[T] = SingletonCodec[T](m.value)

  inline def deriveTransparent[T](m: Made.TransparentOf[T]): MCodec[T] = TransparentCodec(
    m.wrap,
    m.unwrap,
    compiletime.summonInline[MCodec[m.ElemType]],
  )
