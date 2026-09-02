package bench.codecs

import bench.models.*
import io.bullet.borer.{Codec as BCodec, Json}
import io.bullet.borer.derivation.MapBasedCodecs.*

/**
 * borer 1.x — streaming `Input`/`Output` design, the closest architectural
 * analogue to mcodec. Byte-native; `toUtf8String` / `getBytes` pay a UTF-8 tax here.
 */
object BorerCodecs:

  private given BCodec[GeoPoint] = deriveCodec
  private given BCodec[Address] = deriveCodec
  private given BCodec[Employee] = deriveCodec
  private given BCodec[Department] = deriveCodec
  private given BCodec[Company] = deriveCodec

  private given BCodec[Position] = deriveCodec
  private given BCodec[Geometry] = deriveAllCodecs
  private given BCodec[Feature] = deriveCodec
  private given BCodec[FeatureCollection] = deriveCodec

  private given BCodec[Event] = deriveCodec
  private given BCodec[Batch] = deriveCodec

  private given BCodec[Primitives] = deriveCodec

  private def codec[A](using c: BCodec[A]): JsonCodec[A] = new JsonCodec[A]:
    def encode(value: A): String = Json.encode(value).toUtf8String
    def decode(json: String): A = Json.decode(json.getBytes("UTF-8")).to[A].value

  val primitives: JsonCodec[Primitives] = codec
  val company: JsonCodec[Company] = codec
  val featureCollection: JsonCodec[FeatureCollection] = codec
  val batch: JsonCodec[Batch] = codec
