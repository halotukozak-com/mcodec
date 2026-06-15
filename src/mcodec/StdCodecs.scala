package mcodec

import java.util as ju
import scala.collection.Factory
import scala.collection.immutable.HashSet
import scala.reflect.ClassTag

trait StdCodecs:
  given intCodec: SimpleCodec[Int] = new:
    def readSimple(in: SimpleInput): Int = in.readInt()
    def writeSimple(out: SimpleOutput, v: Int): Unit = out.writeInt(v)

  given longCodec: SimpleCodec[Long] = new:
    def readSimple(in: SimpleInput): Long = in.readLong()
    def writeSimple(out: SimpleOutput, v: Long): Unit = out.writeLong(v)

  given doubleCodec: SimpleCodec[Double] = new:
    def readSimple(in: SimpleInput): Double = in.readDouble()
    def writeSimple(out: SimpleOutput, v: Double): Unit = out.writeDouble(v)

  given booleanCodec: SimpleCodec[Boolean] = new:
    def readSimple(in: SimpleInput): Boolean = in.readBoolean()
    def writeSimple(out: SimpleOutput, v: Boolean): Unit = out.writeBoolean(v)

  given stringCodec: SimpleCodec[String] = new:
    def readSimple(in: SimpleInput): String = in.readString()
    def writeSimple(out: SimpleOutput, v: String): Unit = out.writeString(v)

  given bigIntCodec: SimpleCodec[BigInt] = new:
    def readSimple(in: SimpleInput): BigInt = in.readBigInt()
    def writeSimple(out: SimpleOutput, v: BigInt): Unit = out.writeBigInt(v)

  given bigDecimalCodec: SimpleCodec[BigDecimal] = new:
    def readSimple(in: SimpleInput): BigDecimal = in.readBigDecimal()
    def writeSimple(out: SimpleOutput, v: BigDecimal): Unit = out.writeBigDecimal(v)

  given uuidCodec: SimpleCodec[ju.UUID] = new:
    def readSimple(in: SimpleInput): ju.UUID =
      try ju.UUID.fromString(in.readString())
      catch case e: IllegalArgumentException => throw ReadFailure("invalid UUID", e)
    def writeSimple(out: SimpleOutput, v: ju.UUID): Unit = out.writeString(v.toString)

  given optionCodec: [T: MCodec] => MCodec[Option[T]] = new:
    def write(out: Output, value: Option[T]): Unit = value match
      case Some(x) => MCodec.write(out, x)
      case None => out.writeNull()
    def read(in: Input): Option[T] =
      if in.readNull() then None else Some(MCodec.read[T](in))

  given eitherCodec: [L: MCodec, R: MCodec] => ObjectCodec[Either[L, R]] = new:
    def writeObject(out: ObjectOutput, v: Either[L, R]): Unit = v match
      case Left(l) => MCodec.write(out.writeField("Left"), l)
      case Right(r) => MCodec.write(out.writeField("Right"), r)
    def readObject(in: ObjectInput): Either[L, R] =
      if !in.hasNext then throw ReadFailure("expected Left/Right field, got empty object")
      val f = in.nextField()
      f.fieldName match
        case "Left" => Left(withSegment(PathSegment.Case("Left"))(MCodec.read[L](f)))
        case "Right" => Right(withSegment(PathSegment.Case("Right"))(MCodec.read[R](f)))
        case other => throw ReadFailure(s"expected Left/Right, got field: $other")

  inline given tupleCodec: [T <: Tuple] => ListCodec[T] =
    mkTupleCodec(compiletime.summonAll[Tuple.Map[T, MCodec]].toList.asInstanceOf[List[MCodec[Any]]])

  protected def mkTupleCodec[T <: Tuple](codecs: List[MCodec[Any]]): ListCodec[T] = new:
    def writeList(out: ListOutput, value: T): Unit =
      out.declareSize(codecs.size)
      val it = value.productIterator
      codecs.foreach(c => c.write(out.writeElement(), it.next()))
    def readList(in: ListInput): T =
      var idx = 0
      val elems = codecs.map: c =>
        if !in.hasNext then throw ReadFailure("expected tuple element")
        val r = withSegment(PathSegment.Index(idx))(c.read(in.nextElement()))
        idx += 1
        r
      Tuple.fromArray(elems.toArray[Any]).asInstanceOf[T]

  private def collCodec[C[X] <: Iterable[X], T: MCodec](using fac: Factory[T, C[T]]): ListCodec[C[T]] = new:
    def writeList(out: ListOutput, value: C[T]): Unit =
      value.foreach(MCodec.write(out.writeElement(), _))
    def readList(in: ListInput): C[T] =
      val b = fac.newBuilder
      var idx = 0
      while in.hasNext do
        b += withSegment(PathSegment.Index(idx))(MCodec.read[T](in.nextElement()))
        idx += 1
      b.result()

  given seqCodec: [C[X] <: collection.Seq[X], T: MCodec] => Factory[T, C[T]] => ListCodec[C[T]] = collCodec[C, T]
  given setCodec: [C[X] <: collection.Set[X], T: MCodec] => Factory[T, C[T]] => ListCodec[C[T]] = collCodec[C, T]
  given listCodec: [T: MCodec] => ListCodec[List[T]] = collCodec[List, T]
  given vectorCodec: [T: MCodec] => ListCodec[Vector[T]] = collCodec[Vector, T]
  given indexedSeqCodec: [T: MCodec] => ListCodec[IndexedSeq[T]] = collCodec[IndexedSeq, T]
  given hashSetCodec: [T: MCodec] => ListCodec[HashSet[T]] = collCodec[HashSet, T]

  given arrayCodec: [T: {ClassTag, MCodec}] => ListCodec[Array[T]] = new:
    def writeList(out: ListOutput, value: Array[T]): Unit =
      var i = 0
      while i < value.length do
        MCodec.write(out.writeElement(), value(i))
        i += 1
    def readList(in: ListInput): Array[T] =
      val b = Array.newBuilder[T]
      var idx = 0
      while in.hasNext do
        b += withSegment(PathSegment.Index(idx))(MCodec.read[T](in.nextElement()))
        idx += 1
      b.result()

  protected def objectMapCodec[K: MKeyCodec as keyCodec, V: MCodec]: ObjectCodec[Map[K, V]] = new:
    def writeObject(out: ObjectOutput, m: Map[K, V]): Unit = m.foreach: (key, value) =>
      MCodec.write(out.writeField(keyCodec.writeKey(key)), value)
    def readObject(in: ObjectInput): Map[K, V] =
      val b = Map.newBuilder[K, V]
      while in.hasNext do
        val f = in.nextField()
        b += keyCodec.readKey(f.fieldName) -> withSegment(PathSegment.Key(f.fieldName))(MCodec.read[V](f))
      b.result()

  protected def pairsMapCodec[K: MCodec, V: MCodec]: ListCodec[Map[K, V]] = new:
    def writeList(out: ListOutput, m: Map[K, V]): Unit = m.foreach: (key, value) =>
      val pair = out.writeElement().writeList()
      MCodec.write(pair.writeElement(), key)
      MCodec.write(pair.writeElement(), value)
      pair.finish()
    def readList(in: ListInput): Map[K, V] =
      val b = Map.newBuilder[K, V]
      var entryIdx = 0
      while in.hasNext do
        val pair = in.nextElement().readList()
        if !pair.hasNext then throw ReadFailure("expected map entry key")
        val key = withSegment(PathSegment.Index(entryIdx))(MCodec.read[K](pair.nextElement()))
        if !pair.hasNext then throw ReadFailure("expected map entry value")
        val value = withSegment(PathSegment.Index(entryIdx))(MCodec.read[V](pair.nextElement()))
        pair.skipRemaining()
        b += key -> value
        entryIdx += 1
      b.result()

  inline given mapCodec: [K: MCodec, V: MCodec] => MCodec[Map[K, V]] =
    scala.compiletime.summonFrom:
      case given MKeyCodec[K] => objectMapCodec
      case _ => pairsMapCodec
