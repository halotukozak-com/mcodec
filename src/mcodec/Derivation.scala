package halotukozak.mcodec

import halotukozak.made.*
import halotukozak.made.annotation.optionalParam
import halotukozak.mcodec.annotation.{outOfOrder, transientDefault}
import halotukozak.commons.*
import scala.annotation.nowarn

trait Derivation:
  this: MCodec.type =>
  inline def deriveDispatch[T](m: Made.Of[T]): MCodec[T] = inline m match
    case pm: Made.ProductOf[T] => deriveProduct[T](pm)
    case sm: Made.SumOf[T] => deriveSum[T](sm)
    case tm: Made.TransparentOf[T] => deriveTransparent[T](tm)
    case gm: Made.SingletonOf[T] => deriveSingleton[T](gm)

  inline def deriveProduct[T](m: Made.ProductOf[T]): MCodec[T] =
    val (optionalFlags, transientDefaultFlags, outOfOrderFlags) =
      m.elems.hasAnnotationsByType[(optionalParam, transientDefault, outOfOrder)]
    ProductCodec[T](
      compiletime.constValue[m.Label],
      compiletime.constValueTuple[m.ElemLabels].toArrayOf[String],
      compiletime.summonAll[Tuple.Map[m.ElemTypes, MCodec]].toArrayOf[MCodec[Any]](using containsOnly.refl),
      m.elems
        .mapAs[MadeFieldElem][[e] =>> Any | NotExists]([e <: MadeFieldElem] => elem => elem.default)
        .toArrayOf[Any | NotExists],
      optionalFlags.toArray.map(_.asInstanceOf[Boolean]),
      isOptionFlags[m.ElemTypes].toArray,
      transientDefaultFlags.toArray.map(_.asInstanceOf[Boolean]),
      outOfOrderFlags.toArray.map(_.asInstanceOf[Boolean]),
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

  inline def isOptionFlags[Elems <: Tuple]: List[Boolean] = inline compiletime.erasedValue[Elems] match
    case _: EmptyTuple => Nil
    case _: (Option[?] *: tail) => true :: isOptionFlags[tail]
    case _: (head *: tail) => false :: isOptionFlags[tail]

  inline def deriveSum[T](m: Made.SumOf[T]): MCodec[T] =
    inline if m.hasAnnotation[halotukozak.mcodec.annotation.stringEnum] then
      EnumStringCodec[T](
        compiletime.constValue[m.Label],
        compiletime.constValueTuple[m.ElemLabels].toArrayOf[String],
        summonOrDeriveCases[m.ElemTypes].toArray,
        m.ordinal,
      )
    else inline if m.hasAnnotation[halotukozak.mcodec.annotation.flatten] then
      val caseKey = inline m.getAnnotation[halotukozak.mcodec.annotation.flatten] match
        case f: halotukozak.mcodec.annotation.flatten => f.key
        case _ => "_case"
      FlatSumCodec[T](
        compiletime.constValue[m.Label],
        compiletime.constValueTuple[m.ElemLabels].toArrayOf[String],
        summonOrDeriveCases[m.ElemTypes].toArray,
        m.ordinal,
        caseKey,
        defaultCaseIdx(m.elems.hasAnnotations[halotukozak.mcodec.annotation.defaultCase]),
      )
    else
      NestedSumCodec[T](
        compiletime.constValue[m.Label],
        compiletime.constValueTuple[m.ElemLabels].toArrayOf[String],
        summonOrDeriveCases[m.ElemTypes].toArray,
        m.ordinal,
        defaultCaseIdx(m.elems.hasAnnotations[halotukozak.mcodec.annotation.defaultCase]),
      )

  inline def forSealedEnum[T: Made.Of as m]: MCodec[T] = inline m match
    case sm: Made.SumOf[T] =>
      val caseNames = compiletime.constValueTuple[sm.ElemLabels].toArrayOf[String]
      val values = summonOrDeriveCases[sm.ElemTypes].toArray.map:
        case s: SingletonCodec[?] => s.value
        case _ => throw ReadFailure(s"forSealedEnum requires every case to be a singleton")
      val ordinal = sm.ordinal
      fromKeyCodec[T](
        using MKeyCodec.create(
          v => caseNames(ordinal(v)),
          s =>
            val idx = caseNames.indexOf(s)
            if idx < 0 then throw ReadFailure(s"unknown case: $s")
            else values(idx).asInstanceOf[T],
        ),
      )
    case _ => compiletime.error("forSealedEnum requires a sealed trait / enum of singleton cases")

  // Compile-time guard (>1 @defaultCase is an error) + index selection.
  transparent inline def defaultCaseIdx(inline flags: Tuple): Int = inline flags match
    case _: EmptyTuple => -1
    case fs: (head *: tail) =>
      inline if compiletime.constValue[head & Boolean] then
        rejectMoreDefaults(fs.tail)
        0
      else
        inline defaultCaseIdx(fs.tail) match
          case -1 => -1
          case n => n + 1

  inline def rejectMoreDefaults(inline flags: Tuple): Unit = inline flags match
    case _: EmptyTuple => ()
    case fs: (head *: tail) =>
      inline if compiletime.constValue[head & Boolean] then compiletime.error("more than one @defaultCase in hierarchy")
      else rejectMoreDefaults(fs.tail)

  inline def summonOrDeriveCases[Elems <: Tuple]: List[MCodec[Any]] =
    inline compiletime.erasedValue[Elems] match
      case _: EmptyTuple => Nil
      case _: (head *: tail) =>
        val c = compiletime.summonFrom:
          case given MCodec[`head`] => compiletime.summonInline[MCodec[head]]
          case _ => MCodec.derived[head]
        : @nowarn("msg=unused pattern variable")

        c.asInstanceOf[MCodec[Any]] :: summonOrDeriveCases[tail]

  inline def deriveSingleton[T](m: Made.SingletonOf[T]): MCodec[T] = SingletonCodec[T](m.value)

  inline def deriveTransparent[T](m: Made.TransparentOf[T]): MCodec[T] = TransparentCodec(
    m.wrap,
    m.unwrap,
    compiletime.summonInline[MCodec[m.ElemType]],
  )
