package halotukozak.mcodec

import halotukozak.made.*

trait MCodec[T]:
  self =>

  def read(input: Input): T

  def write(output: Output, value: T): Unit

  inline final def transform[U](inline onWrite: U => T, inline onRead: T => U): MCodec[U] =
    MCodec.create(in => onRead(read(in)), (out, u) => write(out, onWrite(u)))

  final def transformed[U](onRead: T => U)(onWrite: U => T): MCodec[U] = new:
    def read(input: Input): U =
      try onRead(self.read(input))
      catch case scala.util.control.NonFatal(e) => throw ReadFailure("onRead conversion failed", e)
    def write(output: Output, value: U): Unit =
      val t = try onWrite(value)
      catch case scala.util.control.NonFatal(e) => throw WriteFailure("onWrite conversion failed", e)
      self.write(output, t)

  final def nullable: MCodec[T | Null] = new:
    def read(input: Input): T | Null =
      if input.readNull() then null else self.read(input)
    def write(output: Output, value: T | Null): Unit = value match
      case null => output.writeNull()
      case v => self.write(output, v)

object MCodec extends StdCodecs, ManualCodecs, JavaCodecs, Derivation, BsonCodecs:
  inline def apply[T: MCodec as c]: MCodec[T] = c

  transparent inline def derived[T](using m: Made.Of[T]): MCodec[T] = deriveDispatch[T](m)

  def read[T: MCodec as codec](input: Input): T = codec.read(input)
  def write[T: MCodec as codec](output: Output, value: T): Unit = codec.write(output, value)

  def create[T](readFun: Input => T, writeFun: (Output, T) => Unit): MCodec[T] = new:
    def read(input: Input): T = readFun(input)
    def write(output: Output, value: T): Unit = writeFun(output, value)

  def makeLazy[T](codec: => MCodec[T]): MCodec[T] = new:
    private lazy val c = codec
    def read(input: Input): T = c.read(input)
    def write(output: Output, value: T): Unit = c.write(output, value)

  /**
   * Force-write variant: writes every `@transientDefault` field even when equal to its default.
   * Implemented via the `IgnoreTransientDefaults` Output marker, so it works for ANY codec and
   * recurses into nested products. Read is unchanged.
   */
  def forceTransientDefaults[T](codec: MCodec[T]): MCodec[T] = new:
    def read(input: Input): T = codec.read(input)
    def write(output: Output, value: T): Unit =
      codec.write(output.withMarkers(Marker.IgnoreTransientDefaults), value)
