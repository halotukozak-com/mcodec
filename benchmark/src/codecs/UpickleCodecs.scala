package bench.codecs

import bench.models.*
import upickle.default.*

/**
 * uPickle 4.x — `ReadWriter` is a single read+write type class, the closest API
 * analogue to mcodec's `MCodec`.
 */
object UpickleCodecs:

  private given ReadWriter[GeoPoint] = macroRW
  private given ReadWriter[Address] = macroRW
  private given ReadWriter[Employee] = macroRW
  private given ReadWriter[Department] = macroRW
  private given ReadWriter[Company] = macroRW

  private given ReadWriter[Position] = macroRW
  private given ReadWriter[Geometry.Point] = macroRW
  private given ReadWriter[Geometry.MultiPoint] = macroRW
  private given ReadWriter[Geometry.LineString] = macroRW
  private given ReadWriter[Geometry.MultiLineString] = macroRW
  private given ReadWriter[Geometry.Polygon] = macroRW
  private given ReadWriter[Geometry.MultiPolygon] = macroRW
  private given ReadWriter[Geometry.GeometryCollection] = macroRW
  private given ReadWriter[Geometry] = macroRW
  private given ReadWriter[Feature] = macroRW
  private given ReadWriter[FeatureCollection] = macroRW

  private given ReadWriter[Event] = macroRW
  private given ReadWriter[Batch] = macroRW

  private given ReadWriter[Primitives] = macroRW

  private def codec[A](using rw: ReadWriter[A]): JsonCodec[A] = new JsonCodec[A]:
    def encode(value: A): String = write(value)
    def decode(json: String): A = read[A](json)

  val primitives: JsonCodec[Primitives] = codec
  val company: JsonCodec[Company] = codec
  val featureCollection: JsonCodec[FeatureCollection] = codec
  val batch: JsonCodec[Batch] = codec
