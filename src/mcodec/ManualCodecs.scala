package mcodec

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
