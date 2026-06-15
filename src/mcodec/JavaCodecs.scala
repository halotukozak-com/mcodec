package mcodec

import java.lang as jl
import java.math as jm
import java.util as ju
import scala.jdk.CollectionConverters.*
import scala.reflect.{classTag, ClassTag}

trait JavaCodecs:
  this: MCodec.type =>

  given jBooleanCodec: MCodec[jl.Boolean | Null] =
    createSimple(
      in => jl.Boolean.valueOf(in.readBoolean()),
      (out, v: jl.Boolean) => out.writeBoolean(v.booleanValue),
    ).nullable
  given jCharacterCodec: MCodec[jl.Character | Null] =
    createSimple(
      in => jl.Character.valueOf(in.readChar()),
      (out, v: jl.Character) => out.writeChar(v.charValue),
    ).nullable
  given jByteCodec: MCodec[jl.Byte | Null] =
    createSimple(in => jl.Byte.valueOf(in.readByte()), (out, v: jl.Byte) => out.writeByte(v.byteValue)).nullable
  given jShortCodec: MCodec[jl.Short | Null] =
    createSimple(in => jl.Short.valueOf(in.readShort()), (out, v: jl.Short) => out.writeShort(v.shortValue)).nullable
  given jIntegerCodec: MCodec[jl.Integer | Null] =
    createSimple(in => jl.Integer.valueOf(in.readInt()), (out, v: jl.Integer) => out.writeInt(v.intValue)).nullable
  given jLongCodec: MCodec[jl.Long | Null] =
    createSimple(in => jl.Long.valueOf(in.readLong()), (out, v: jl.Long) => out.writeLong(v.longValue)).nullable
  given jFloatCodec: MCodec[jl.Float | Null] =
    createSimple(in => jl.Float.valueOf(in.readFloat()), (out, v: jl.Float) => out.writeFloat(v.floatValue)).nullable
  given jDoubleCodec: MCodec[jl.Double | Null] =
    createSimple(
      in => jl.Double.valueOf(in.readDouble()),
      (out, v: jl.Double) => out.writeDouble(v.doubleValue),
    ).nullable
  given jBigIntegerCodec: MCodec[jm.BigInteger | Null] =
    createSimple(in => in.readBigInt().bigInteger, (out, v: jm.BigInteger) => out.writeBigInt(BigInt(v))).nullable
  given jBigDecimalCodec: MCodec[jm.BigDecimal | Null] =
    createSimple(
      in => in.readBigDecimal().bigDecimal,
      (out, v: jm.BigDecimal) => out.writeBigDecimal(BigDecimal(v)),
    ).nullable

  trait JFactory[A, C]:
    def newBuilder: JBuilder[A, C]

  trait JBuilder[A, C]:
    def add(a: A): Unit
    def result(): C

  given arrayListFactory: [A] => JFactory[A, ju.ArrayList[A]] = new:
    def newBuilder = new JBuilder[A, ju.ArrayList[A]]:
      private val c = new ju.ArrayList[A]()
      def add(a: A): Unit = c.add(a)
      def result(): ju.ArrayList[A] = c

  given linkedListFactory: [A] => JFactory[A, ju.LinkedList[A]] = new:
    def newBuilder = new JBuilder[A, ju.LinkedList[A]]:
      private val c = new ju.LinkedList[A]()
      def add(a: A): Unit = c.add(a)
      def result(): ju.LinkedList[A] = c

  given hashSetFactory: [A] => JFactory[A, ju.HashSet[A]] = new:
    def newBuilder = new JBuilder[A, ju.HashSet[A]]:
      private val c = new ju.HashSet[A]()
      def add(a: A): Unit = c.add(a)
      def result(): ju.HashSet[A] = c

  given linkedHashSetFactory: [A] => JFactory[A, ju.LinkedHashSet[A]] = new:
    def newBuilder = new JBuilder[A, ju.LinkedHashSet[A]]:
      private val c = new ju.LinkedHashSet[A]()
      def add(a: A): Unit = c.add(a)
      def result(): ju.LinkedHashSet[A] = c

  given treeSetFactory: [A] => JFactory[A, ju.TreeSet[A]] = new:
    def newBuilder = new JBuilder[A, ju.TreeSet[A]]:
      private val c = new ju.TreeSet[A]()
      def add(a: A): Unit = c.add(a)
      def result(): ju.TreeSet[A] = c

  given jCollectionCodec: [A: MCodec, C <: ju.Collection[A]] => (fac: JFactory[A, C]) => ListCodec[C]:
    def writeList(out: ListOutput, value: C): Unit =
      value.asScala.foreach(MCodec.write(out.writeElement(), _))
    def readList(in: ListInput): C =
      val b = fac.newBuilder
      var idx = 0
      while in.hasNext do
        b.add(withSegment(PathSegment.Index(idx))(MCodec.read[A](in.nextElement())))
        idx += 1
      b.result()

  trait JMapFactory[K, V, M]:
    def newBuilder: JMapBuilder[K, V, M]

  trait JMapBuilder[K, V, M]:
    def put(k: K, v: V): Unit
    def result(): M

  given hashMapFactory: [K, V] => JMapFactory[K, V, ju.HashMap[K, V]] = new:
    def newBuilder = new JMapBuilder[K, V, ju.HashMap[K, V]]:
      private val m = new ju.HashMap[K, V]()
      def put(k: K, v: V): Unit = m.put(k, v)
      def result(): ju.HashMap[K, V] = m

  given linkedHashMapFactory: [K, V] => JMapFactory[K, V, ju.LinkedHashMap[K, V]] = new:
    def newBuilder = new JMapBuilder[K, V, ju.LinkedHashMap[K, V]]:
      private val m = new ju.LinkedHashMap[K, V]()
      def put(k: K, v: V): Unit = m.put(k, v)
      def result(): ju.LinkedHashMap[K, V] = m

  given treeMapFactory: [K, V] => JMapFactory[K, V, ju.TreeMap[K, V]] = new:
    def newBuilder = new JMapBuilder[K, V, ju.TreeMap[K, V]]:
      private val m = new ju.TreeMap[K, V]()
      def put(k: K, v: V): Unit = m.put(k, v)
      def result(): ju.TreeMap[K, V] = m

  given jMapCodec: [K: MKeyCodec as kc, V: MCodec, M <: ju.Map[K, V]] => (fac: JMapFactory[K, V, M]) => ObjectCodec[M]:
    def writeObject(out: ObjectOutput, m: M): Unit =
      m.asScala.foreach((k, v) => MCodec.write(out.writeField(kc.writeKey(k)), v))
    def readObject(in: ObjectInput): M =
      val b = fac.newBuilder
      while in.hasNext do
        val f = in.nextField()
        b.put(kc.readKey(f.fieldName), withSegment(PathSegment.Key(f.fieldName))(MCodec.read[V](f)))
      b.result()

  // The Class[E] cast is the standard reflect idiom for Java enum reflection, not a null cast.
  given jEnumCodec: [E <: jl.Enum[E]: ClassTag] => MCodec[E] = nonNullString(
    name => jl.Enum.valueOf(classTag[E].runtimeClass.asInstanceOf[Class[E]], name),
    _.name.nn,
  )
