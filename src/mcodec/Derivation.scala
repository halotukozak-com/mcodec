package mcodec

import made.*
import made.annotation.optionalParam

import scala.annotation.tailrec

trait Derivation:
  this: MCodec.type =>
  transparent inline protected def derivedRec[T: Made.Of as m]: MCodec[T] =
    val deferred = new Deferred[T]
    given self: MCodec[T] = deferred
    val built = deriveDispatch[T](m)
    deferred.underlying = built
    built

  transparent inline private def deriveDispatch[T](m: Made.Of[T]): MCodec[T] = inline m match
    case pm: Made.ProductOf[T] => deriveProduct[T](pm)
    case sm: Made.SumOf[T] => deriveSum[T](sm)
    case tm: Made.TransparentOf[T] => deriveTransparent[T](tm)
    case gm: Made.SingletonOf[T] => deriveSingleton[T](gm)

  inline private def deriveProduct[T](m: Made.ProductOf[T]): MCodec[T] = ProductCodec[T](
    compiletime.constValue[m.Label],
    compiletime.constValueTuple[m.ElemLabels].toArrayOf[String],
    compiletime.summonAll[Tuple.Map[m.ElemTypes, MCodec]].toArrayOf[MCodec[Any]](using containsOnly.refl),
    m.elems
      .mapAs[MadeFieldElem][[e] =>> Option[Any]]([e <: MadeFieldElem] => elem => elem.default)
      .toArrayOf[Option[Any]],
    m.elems.hasAnnotations[optionalParam].toArrayOf[Boolean],
    isOptionFlags[m.ElemTypes].toArray,
    m.fromUnsafeArray,
    compiletime
      .constValueTuple[Tuple.Map[m.GeneratedElems, MadeElem.ExtractLabel]]
      .toArrayOf[String],
    m.generatedElems
      .mapAs[GeneratedMadeElem][[e] =>> T => Any]([e <: GeneratedMadeElem] =>
        elem => outer => elem.apply(outer.asInstanceOf[elem.OuterType]),
      )
      .toArrayOf[T => Any],
    compiletime
      .summonAll[Tuple.Map[Tuple.Map[m.GeneratedElems, MadeElem.ExtractOf], MCodec]]
      .toArrayOf[MCodec[Any]](using containsOnly.refl),
  )

  inline private def isOptionFlags[Elems <: Tuple]: List[Boolean] = inline compiletime.erasedValue[Elems] match
    case _: EmptyTuple => Nil
    case _: (Option[?] *: tail) => true :: isOptionFlags[tail]
    case _: (head *: tail) => false :: isOptionFlags[tail]

  inline private def deriveSum[T](m: Made.SumOf[T]): MCodec[T] =
    inline if m.hasAnnotation[mcodec.annotation.flatten] then
      FlatSumCodec[T](
        compiletime.constValue[m.Label],
        compiletime.constValueTuple[m.ElemLabels].toArrayOf[String],
        summonOrDeriveCases[m.ElemTypes].toArray,
        m.ordinal,
        m.getAnnotation[mcodec.annotation.flatten].fold("_case")(_.key),
        defaultCaseIdx(m.elems.hasAnnotations[mcodec.annotation.defaultCase]),
      )
    else
      NestedSumCodec[T](
        compiletime.constValue[m.Label],
        compiletime.constValueTuple[m.ElemLabels].toArrayOf[String],
        summonOrDeriveCases[m.ElemTypes].toArray,
        m.ordinal,
        defaultCaseIdx(m.elems.hasAnnotations[mcodec.annotation.defaultCase]),
      )

  // Compile-time guard (>1 @defaultCase is an error) + index selection.
  transparent inline private def defaultCaseIdx(inline flags: Tuple): Int = inline flags match
    case _: EmptyTuple => -1
    case fs: (head *: tail) =>
      inline if compiletime.constValue[head & Boolean] then
        rejectMoreDefaults(fs.tail)
        0
      else
        inline defaultCaseIdx(fs.tail) match
          case -1 => -1
          case n => n + 1

  inline private def rejectMoreDefaults(inline flags: Tuple): Unit = inline flags match
    case _: EmptyTuple => ()
    case fs: (head *: tail) =>
      inline if compiletime.constValue[head & Boolean] then compiletime.error("more than one @defaultCase in hierarchy")
      else rejectMoreDefaults(fs.tail)

  inline private def summonOrDeriveCases[Elems <: Tuple]: List[MCodec[Any]] =
    inline compiletime.erasedValue[Elems] match
      case _: EmptyTuple => Nil
      case _: (head *: tail) =>
        val c = compiletime.summonFrom:
          case given MCodec[`head`] => compiletime.summonInline[MCodec[head]]
          case _ => MCodec.derived[head]

        c.asInstanceOf[MCodec[Any]] :: summonOrDeriveCases[tail]

  inline private def deriveSingleton[T](m: Made.SingletonOf[T]): MCodec[T] = SingletonCodec[T](m.value)

  inline private def deriveTransparent[T](m: Made.TransparentOf[T]): MCodec[T] = TransparentCodec(
    m.wrap,
    m.unwrap,
    compiletime.summonInline[MCodec[m.ElemType]],
  )
