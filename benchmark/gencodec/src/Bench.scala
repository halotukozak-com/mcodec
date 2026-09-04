// format: off
// ^ Scala 2.13 source (AVSystem GenCodec has no Scala 3 release). The repo's
// scalafmt config applies Scala-3-only rewrites (optional braces, `import a.*`)
// that 2.13 can't parse, so formatting is disabled for this file.
package bench.gencodec

import java.util.concurrent.TimeUnit
import scala.util.Random
import org.openjdk.jmh.annotations._
import com.avsystem.commons.serialization.GenCodec
import com.avsystem.commons.serialization.json.{JsonStringInput, JsonStringOutput}

// --- models (mirror of benchmark/src/models/Models.scala, Scala 2.13) --------

final case class Primitives(
    id: Long,
    name: String,
    active: Boolean,
    score: Double,
    count: Int,
    ratio: Double,
    tag: Option[String],
    notes: Option[String],
    createdAt: Long,
    updatedAt: Long,
    version: Int,
    checksum: Long,
)
object Primitives { implicit val c: GenCodec[Primitives] = GenCodec.materialize }

final case class GeoPoint(lat: Double, lng: Double)
object GeoPoint { implicit val c: GenCodec[GeoPoint] = GenCodec.materialize }

final case class Address(street: String, city: String, zip: String, country: String, location: GeoPoint)
object Address { implicit val c: GenCodec[Address] = GenCodec.materialize }

final case class Employee(id: Long, name: String, email: String, salary: BigDecimal, address: Address, manager: Option[Long])
object Employee { implicit val c: GenCodec[Employee] = GenCodec.materialize }

final case class Department(name: String, budget: BigDecimal, head: Employee, staff: List[Employee])
object Department { implicit val c: GenCodec[Department] = GenCodec.materialize }

final case class Company(name: String, founded: Int, hq: Address, departments: List[Department])
object Company { implicit val c: GenCodec[Company] = GenCodec.materialize }

final case class Position(lng: Double, lat: Double)
object Position { implicit val c: GenCodec[Position] = GenCodec.materialize }

sealed trait Geometry
object Geometry {
  final case class Point(coordinates: Position) extends Geometry
  final case class MultiPoint(coordinates: List[Position]) extends Geometry
  final case class LineString(coordinates: List[Position]) extends Geometry
  final case class MultiLineString(coordinates: List[List[Position]]) extends Geometry
  final case class Polygon(coordinates: List[List[Position]]) extends Geometry
  final case class MultiPolygon(coordinates: List[List[List[Position]]]) extends Geometry
  final case class GeometryCollection(geometries: List[Geometry]) extends Geometry
  implicit val c: GenCodec[Geometry] = GenCodec.materializeRecursively
}

final case class Feature(id: String, geometry: Geometry, properties: Map[String, String])
object Feature { implicit val c: GenCodec[Feature] = GenCodec.materialize }

final case class FeatureCollection(features: List[Feature])
object FeatureCollection { implicit val c: GenCodec[FeatureCollection] = GenCodec.materialize }

final case class Event(
    id: Long,
    kind: String,
    actor: String,
    payload: Map[String, String],
    tags: Set[String],
    values: List[Double],
)
object Event { implicit val c: GenCodec[Event] = GenCodec.materialize }

final case class Batch(generatedAt: Long, events: Vector[Event], offsets: Map[String, Long])
object Batch { implicit val c: GenCodec[Batch] = GenCodec.materialize }

// --- sample data (mirror of SampleData.scala) --------------------------------

object SampleData {
  private def rng() = new Random(42L)

  val primitives: Primitives = Primitives(
    9876543210L, "widget-controller-unit", active = true, 87.635213, 42, 0.7314159,
    Some("prod"), None, 1725000000000L, 1725600000000L, 7, -123456789012345L,
  )

  val company: Company = {
    val r = rng()
    def address(i: Int) = Address(
      s"${100 + i} Example Street",
      if (i % 2 == 0) "Springfield" else "Shelbyville",
      f"${10000 + i}%05d", "US", GeoPoint(37.0 + r.nextDouble(), -122.0 - r.nextDouble()),
    )
    def employee(i: Int) = Employee(
      i.toLong, s"Employee $i", s"employee$i@example.com",
      BigDecimal(50000 + r.nextInt(80000)), address(i),
      if (i == 0) None else Some((i / 5).toLong),
    )
    val departments = (0 until 4).toList.map { d =>
      val staff = (0 until 10).toList.map(s => employee(d * 10 + s))
      Department(s"Department $d", BigDecimal(1000000 + r.nextInt(5000000)), staff.head, staff.tail)
    }
    Company("Example Corp", 1998, address(0), departments)
  }

  val featureCollection: FeatureCollection = {
    val r = rng()
    def pos() = Position(-122.0 - r.nextDouble(), 37.0 + r.nextDouble())
    def ring() = List.fill(6)(pos())
    val geometries: List[Geometry] = List(
      Geometry.Point(pos()),
      Geometry.MultiPoint(List.fill(8)(pos())),
      Geometry.LineString(List.fill(12)(pos())),
      Geometry.MultiLineString(List.fill(3)(List.fill(10)(pos()))),
      Geometry.Polygon(List.fill(2)(ring())),
      Geometry.MultiPolygon(List.fill(2)(List.fill(2)(ring()))),
      Geometry.GeometryCollection(List(Geometry.Point(pos()), Geometry.LineString(List.fill(5)(pos())))),
    )
    FeatureCollection(geometries.zipWithIndex.map { case (g, i) =>
      Feature(s"feature-$i", g, Map("name" -> s"Region $i", "kind" -> g.getClass.getSimpleName, "verified" -> "true"))
    })
  }

  val batch: Batch = {
    val r = rng()
    val kinds = Vector("push", "pull_request", "issue", "release", "fork", "star")
    def event(i: Int) = Event(
      i.toLong, kinds(r.nextInt(kinds.length)), s"user-${r.nextInt(2000)}",
      Map("repo" -> s"org/repo-${r.nextInt(100)}", "ref" -> s"refs/heads/branch-${r.nextInt(30)}", "sha" -> f"${r.nextLong()}%016x"),
      (0 to r.nextInt(4)).map(t => s"tag-$t").toSet,
      List.fill(r.nextInt(6))(r.nextDouble() * 100),
    )
    Batch(1725000000000L, (0 until 500).toVector.map(event), (0 until 20).map(p => s"partition-$p" -> r.nextLong()).toMap)
  }
}

// --- JMH: mirrors bench.bench.SerdeBench; the "library" is always GenCodec ----

@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(3)
@State(Scope.Thread)
abstract class GenCodecBench[A](implicit codec: GenCodec[A]) {
  protected def sample: A
  private var value: A = _
  private var json: String = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    value = sample
    json = JsonStringOutput.write(value)
    require(JsonStringInput.read[A](json) != null)
  }

  @Benchmark def write(): String = JsonStringOutput.write(value)
  @Benchmark def read(): A = JsonStringInput.read[A](json)
}

class PrimitivesBench extends GenCodecBench[Primitives] { def sample = SampleData.primitives }
class CompanyBench extends GenCodecBench[Company] { def sample = SampleData.company }
class FeatureCollectionBench extends GenCodecBench[FeatureCollection] { def sample = SampleData.featureCollection }
class BatchBench extends GenCodecBench[Batch] { def sample = SampleData.batch }
