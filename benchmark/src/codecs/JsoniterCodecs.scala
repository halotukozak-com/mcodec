package bench.codecs

import bench.models.*
import com.github.plokhotnyuk.jsoniter_scala.core.{JsonCodec as _, *}
import com.github.plokhotnyuk.jsoniter_scala.macros.*

/**
 * jsoniter-scala 2.x — compile-time macro codecs, the performance reference.
 *
 * The macro generates one monolithic codec for the whole type tree, so only the
 * root types need a `JsonValueCodec`. Byte-native: `writeToString` / `readFromString`
 * pay a UTF-8 round trip here vs. jsoniter's native `Array[Byte]` path.
 */
object JsoniterCodecs:

  given primitivesVc: JsonValueCodec[Primitives] = JsonCodecMaker.make
  given companyVc: JsonValueCodec[Company] = JsonCodecMaker.make
  given featureCollectionVc: JsonValueCodec[FeatureCollection] =
    JsonCodecMaker.make(CodecMakerConfig.withAllowRecursiveTypes(true))
  given batchVc: JsonValueCodec[Batch] = JsonCodecMaker.make

  private def codec[A](using c: JsonValueCodec[A]): JsonCodec[A] = new JsonCodec[A]:
    def encode(value: A): String = writeToString(value)
    def decode(json: String): A = readFromString[A](json)

  val primitives: JsonCodec[Primitives] = codec
  val company: JsonCodec[Company] = codec
  val featureCollection: JsonCodec[FeatureCollection] = codec
  val batch: JsonCodec[Batch] = codec
