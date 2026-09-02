package bench.codecs

import bench.models.*
import play.api.libs.json.*

/**
 * play-json 2.10 (Scala 3) — `Json.format` macro.
 *
 * The Scala 3 macro covers case classes but not sealed hierarchies, so play-json
 * is absent from the [[Geometry]] row of the matrix (reported as "n/a").
 */
object PlayJsonCodecs:

  private given Format[GeoPoint] = Json.format
  private given Format[Address] = Json.format
  private given Format[Employee] = Json.format
  private given Format[Department] = Json.format
  private given Format[Company] = Json.format

  private given Format[Event] = Json.format
  private given Format[Batch] = Json.format

  private given Format[Primitives] = Json.format

  private def codec[A](using f: Format[A]): JsonCodec[A] = new JsonCodec[A]:
    def encode(value: A): String = Json.stringify(Json.toJson(value))
    def decode(json: String): A = Json.parse(json).as[A]

  val primitives: JsonCodec[Primitives] = codec
  val company: JsonCodec[Company] = codec
  val batch: JsonCodec[Batch] = codec
