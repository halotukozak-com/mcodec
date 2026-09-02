package bench.codecs

import bench.models.*
import io.circe.*
import io.circe.syntax.*
import io.circe.parser.decode as circeDecode
import io.circe.generic.semiauto.*

/** circe 0.14.x with `circe-generic` semi-auto derivation — the ecosystem default. */
object CirceCodecs:

  private given Codec[GeoPoint] = deriveCodec
  private given Codec[Address] = deriveCodec
  private given Codec[Employee] = deriveCodec
  private given Codec[Department] = deriveCodec
  private given Codec[Company] = deriveCodec

  private given Codec[Position] = deriveCodec
  private given Codec[Geometry] = deriveCodec
  private given Codec[Feature] = deriveCodec
  private given Codec[FeatureCollection] = deriveCodec

  private given Codec[Event] = deriveCodec
  private given Codec[Batch] = deriveCodec

  private given Codec[Primitives] = deriveCodec

  private def codec[A](using e: Encoder[A], d: Decoder[A]): JsonCodec[A] = new JsonCodec[A]:
    def encode(value: A): String = value.asJson.noSpaces
    def decode(json: String): A = circeDecode[A](json).fold(throw _, identity)

  val primitives: JsonCodec[Primitives] = codec
  val company: JsonCodec[Company] = codec
  val featureCollection: JsonCodec[FeatureCollection] = codec
  val batch: JsonCodec[Batch] = codec
