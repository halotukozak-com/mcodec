package bench.codecs

import bench.models.*
import zio.json.{JsonCodec as ZCodec, *}

/** zio-json 1.x — macro derivation via `DeriveJsonCodec.gen`. */
object ZioJsonCodecs:

  private given ZCodec[GeoPoint] = DeriveJsonCodec.gen
  private given ZCodec[Address] = DeriveJsonCodec.gen
  private given ZCodec[Employee] = DeriveJsonCodec.gen
  private given ZCodec[Department] = DeriveJsonCodec.gen
  private given ZCodec[Company] = DeriveJsonCodec.gen

  private given ZCodec[Position] = DeriveJsonCodec.gen
  private given ZCodec[Geometry] = DeriveJsonCodec.gen
  private given ZCodec[Feature] = DeriveJsonCodec.gen
  private given ZCodec[FeatureCollection] = DeriveJsonCodec.gen

  private given ZCodec[Event] = DeriveJsonCodec.gen
  private given ZCodec[Batch] = DeriveJsonCodec.gen

  private given ZCodec[Primitives] = DeriveJsonCodec.gen

  private def codec[A](using c: ZCodec[A]): JsonCodec[A] = new JsonCodec[A]:
    def encode(value: A): String = value.toJson
    def decode(json: String): A = json.fromJson[A].fold(sys.error, identity)

  val primitives: JsonCodec[Primitives] = codec
  val company: JsonCodec[Company] = codec
  val featureCollection: JsonCodec[FeatureCollection] = codec
  val batch: JsonCodec[Batch] = codec
