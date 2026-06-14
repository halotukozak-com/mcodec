package mcodec

trait MCodec[T]:
  def read(input: Input): T

  def write(output: Output, value: T): Unit

  final inline def transform[U](inline onWrite: U => T, inline onRead: T => U): MCodec[U] =
    MCodec.create(in => onRead(read(in)), (out, u) => write(out, onWrite(u)))

object MCodec:
  inline def apply[T](using c: MCodec[T]): MCodec[T] = c

  def create[T](readFun: Input => T, writeFun: (Output, T) => Unit): MCodec[T] = new MCodec[T]:
    def read(input: Input): T = readFun(input)

    def write(output: Output, value: T): Unit = writeFun(output, value)

  def makeLazy[T](codec: => MCodec[T]): MCodec[T] = new MCodec[T]:
    private lazy val c = codec

    def read(input: Input): T = c.read(input)

    def write(output: Output, value: T): Unit = c.write(output, value)

// Phase 4 adds: inline def derived[T](using Made.Of[T]): MCodec[T]
