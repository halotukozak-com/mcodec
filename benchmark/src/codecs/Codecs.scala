package bench.codecs

import bench.models.*
import bench.codecs.JsonCodec.Lib
import bench.codecs.JsonCodec.Lib.*

/**
 * Central lookup: `(library id, model) -> JsonCodec`.
 *
 * play-json has no Scala 3 sealed-hierarchy macro, so it is absent from the
 * `FeatureCollection` (Geometry) row — see the `@Param` arrays in `SerdeBench`.
 */
object Codecs:

  def primitives(lib: String): JsonCodec[Primitives] = Lib.fromId(lib) match
    case Mcodec => McodecCodecs.primitives
    case Circe => CirceCodecs.primitives
    case Jsoniter => JsoniterCodecs.primitives
    case Upickle => UpickleCodecs.primitives
    case ZioJson => ZioJsonCodecs.primitives
    case Borer => BorerCodecs.primitives
    case PlayJson => PlayJsonCodecs.primitives

  def company(lib: String): JsonCodec[Company] = Lib.fromId(lib) match
    case Mcodec => McodecCodecs.company
    case Circe => CirceCodecs.company
    case Jsoniter => JsoniterCodecs.company
    case Upickle => UpickleCodecs.company
    case ZioJson => ZioJsonCodecs.company
    case Borer => BorerCodecs.company
    case PlayJson => PlayJsonCodecs.company

  def featureCollection(lib: String): JsonCodec[FeatureCollection] = Lib.fromId(lib) match
    case Mcodec => McodecCodecs.featureCollection
    case Circe => CirceCodecs.featureCollection
    case Jsoniter => JsoniterCodecs.featureCollection
    case Upickle => UpickleCodecs.featureCollection
    case ZioJson => ZioJsonCodecs.featureCollection
    case Borer => BorerCodecs.featureCollection
    case PlayJson => sys.error("play-json: no sealed-hierarchy support")

  def batch(lib: String): JsonCodec[Batch] = Lib.fromId(lib) match
    case Mcodec => McodecCodecs.batch
    case Circe => CirceCodecs.batch
    case Jsoniter => JsoniterCodecs.batch
    case Upickle => UpickleCodecs.batch
    case ZioJson => ZioJsonCodecs.batch
    case Borer => BorerCodecs.batch
    case PlayJson => PlayJsonCodecs.batch
