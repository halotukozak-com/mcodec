package mcodec

import made.*

trait MCodec[T]:
  def read(input: Input): T

  def write(output: Output, value: T): Unit

  inline final def transform[U](inline onWrite: U => T, inline onRead: T => U): MCodec[U] =
    MCodec.create(in => onRead(read(in)), (out, u) => write(out, onWrite(u)))

object MCodec extends StdCodecs, Derivation:
  inline def apply[T: MCodec as c]: MCodec[T] = c

  transparent inline def derived[T](using m: Made.Of[T]): MCodec[T] = derivedRec[T]

  def read[T: MCodec as codec](input: Input): T = codec.read(input)
  def write[T: MCodec as codec](output: Output, value: T): Unit = codec.write(output, value)

  def create[T](readFun: Input => T, writeFun: (Output, T) => Unit): MCodec[T] = new:
    def read(input: Input): T = readFun(input)
    def write(output: Output, value: T): Unit = writeFun(output, value)

  def makeLazy[T](codec: => MCodec[T]): MCodec[T] = new:
    private lazy val c = codec
    def read(input: Input): T = c.read(input)
    def write(output: Output, value: T): Unit = c.write(output, value)
