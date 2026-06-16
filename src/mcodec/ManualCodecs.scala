package mcodec

import scala.reflect.ClassTag

trait ManualCodecs:
  def createSimple[T](r: SimpleInput => T, w: (SimpleOutput, T) => Unit): MCodec[T] = new SimpleCodec[T]:
    def readSimple(in: SimpleInput): T = r(in)
    def writeSimple(out: SimpleOutput, v: T): Unit = w(out, v)

  def createList[T](r: ListInput => T, w: (ListOutput, T) => Unit): MCodec[T] = new ListCodec[T]:
    def readList(in: ListInput): T = r(in)
    def writeList(out: ListOutput, v: T): Unit = w(out, v)

  def createObject[T](r: ObjectInput => T, w: (ObjectOutput, T) => Unit): MCodec[T] = new ObjectCodec[T]:
    def readObject(in: ObjectInput): T = r(in)
    def writeObject(out: ObjectOutput, v: T): Unit = w(out, v)

  def nonNullString[T](r: String => T, w: T => String): MCodec[T] =
    createSimple(in => r(in.readString()), (out, v) => out.writeString(w(v)))

  def fromKeyCodec[T](using kc: MKeyCodec[T]): MCodec[T] = nonNullString(kc.readKey, kc.writeKey)

  def subclassCodec[T, S](
    narrow: S => Option[T],
    widen: T => S,
  )(using parent: MCodec[S],
  ): MCodec[T] = MCodec.create(
    in => narrow(parent.read(in)).getOrElse(throw ReadFailure("value is not the expected subclass")),
    (out, t) => parent.write(out, widen(t)),
  )

  def subclassCodec[T <: S: ClassTag, S](using parent: MCodec[S]): MCodec[T] = MCodec.create(
    in =>
      parent.read(in) match
        case t: T => t
        case other => throw ReadFailure(s"$other is not an instance of ${summon[ClassTag[T]].runtimeClass}"),
    (out, t) => parent.write(out, t),
  )
