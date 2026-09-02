package bench.codecs

import bench.models.*
import halotukozak.mcodec.*

/** mcodec (this repo) — JSON backend via `Json.read` / `Json.write`. */
object McodecCodecs:

  private given MCodec[GeoPoint] = MCodec.derived
  private given MCodec[Address] = MCodec.derived
  private given MCodec[Employee] = MCodec.derived
  private given MCodec[Department] = MCodec.derived
  private given MCodec[Company] = MCodec.derived

  private given MCodec[Position] = MCodec.derived
  private given MCodec[Geometry] = MCodec.derived
  private given MCodec[Feature] = MCodec.derived
  private given MCodec[FeatureCollection] = MCodec.derived

  private given MCodec[Event] = MCodec.derived
  private given MCodec[Batch] = MCodec.derived

  private given MCodec[Primitives] = MCodec.derived

  private def codec[A](using c: MCodec[A]): JsonCodec[A] = new JsonCodec[A]:
    def encode(value: A): String = Json.write(value)
    def decode(json: String): A = Json.read[A](json)

  val primitives: JsonCodec[Primitives] = codec
  val company: JsonCodec[Company] = codec
  val featureCollection: JsonCodec[FeatureCollection] = codec
  val batch: JsonCodec[Batch] = codec
