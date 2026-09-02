package bench.codecs

/**
 * Uniform string-in / string-out codec facade.
 *
 * Every library under test is adapted to this shape so the JMH matrix can be
 * generated mechanically (one benchmark class per model, library as `@Param`).
 *
 * The comparison axis is deliberately `A <-> String` for everyone. jsoniter-scala
 * and borer are byte-native and pay a small UTF-8 conversion tax here; their
 * byte-mode numbers are reported separately in the write-up.
 */
trait JsonCodec[A]:
  def encode(value: A): String
  def decode(json: String): A

object JsonCodec:
  /** Libraries in the matrix. `id` is what appears in JMH `@Param` and reports. */
  enum Lib(val id: String):
    case Mcodec extends Lib("mcodec")
    case Circe extends Lib("circe")
    case Jsoniter extends Lib("jsoniter")
    case Upickle extends Lib("upickle")
    case ZioJson extends Lib("zio-json")
    case Borer extends Lib("borer")
    case PlayJson extends Lib("play-json")

  object Lib:
    def fromId(s: String): Lib = values.find(_.id == s).getOrElse(sys.error(s"unknown lib: $s"))
    val allIds: Array[String] = values.map(_.id)
