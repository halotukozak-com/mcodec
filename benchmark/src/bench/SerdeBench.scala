package bench.bench

import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations.*
import bench.codecs.{Codecs, JsonCodec}
import bench.models.*

/**
 * Serialization / deserialization throughput, `A <-> String`, per library.
 *
 * One state class per model; `lib` is a JMH `@Param`, so each run produces a
 * `<Model>Bench.write` and `<Model>Bench.read` row for every library.
 *
 * Filter with the usual JMH regex, e.g.
 * {{{
 *   scala-cli --power run benchmark --jmh -- 'CompanyBench.*' -p lib=mcodec,circe
 * }}}
 */
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(3)
@State(Scope.Thread)
abstract class SerdeBench[A]:
  /** Set by JMH from the `@Param` declared on each concrete subclass. */
  def lib: String
  protected def make(lib: String): JsonCodec[A]
  protected def sample: A

  private var codec: JsonCodec[A] = scala.compiletime.uninitialized
  private var value: A = scala.compiletime.uninitialized
  private var json: String = scala.compiletime.uninitialized

  @Setup(Level.Trial)
  def setup(): Unit =
    codec = make(lib)
    value = sample
    json = codec.encode(value)
    // smoke check: the codec actually round-trips this model
    require(codec.decode(json) != null, s"$lib: decode returned null")
    require(codec.encode(codec.decode(json)).nonEmpty, s"$lib: re-encode empty")

  @Benchmark def write(): String = codec.encode(value)
  @Benchmark def read(): A = codec.decode(json)

class PrimitivesBench extends SerdeBench[Primitives]:
  @Param(Array("mcodec", "circe", "jsoniter", "upickle", "zio-json", "borer", "play-json"))
  var lib: String = scala.compiletime.uninitialized
  def make(lib: String) = Codecs.primitives(lib)
  def sample = SampleData.primitives

class CompanyBench extends SerdeBench[Company]:
  @Param(Array("mcodec", "circe", "jsoniter", "upickle", "zio-json", "borer", "play-json"))
  var lib: String = scala.compiletime.uninitialized
  def make(lib: String) = Codecs.company(lib)
  def sample = SampleData.company

class FeatureCollectionBench extends SerdeBench[FeatureCollection]:
  @Param(Array("mcodec", "circe", "jsoniter", "upickle", "zio-json", "borer"))
  var lib: String = scala.compiletime.uninitialized
  def make(lib: String) = Codecs.featureCollection(lib)
  def sample = SampleData.featureCollection

class BatchBench extends SerdeBench[Batch]:
  @Param(Array("mcodec", "circe", "jsoniter", "upickle", "zio-json", "borer", "play-json"))
  var lib: String = scala.compiletime.uninitialized
  def make(lib: String) = Codecs.batch(lib)
  def sample = SampleData.batch
