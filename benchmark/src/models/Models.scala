package bench.models

/**
 * Shared domain models for every library under test.
 *
 * These are plain data types with NO `derives` clause and NO library-specific
 * annotations — each library derives / configures its own codec in its own
 * `bench.codecs.*` file, so the comparison is apples to apples.
 *
 * Four scenarios, each stressing a different part of a codec:
 *   - [[Primitives]]   flat record, number formatting/parsing, `Option`
 *   - [[Company]]       deep nesting, recursion, `BigDecimal`
 *   - [[Geometry]]      sealed hierarchy / discriminator dispatch
 *   - [[Batch]]         large collections, `Map`, `Set`, `Vector`
 */

// --- 1. Primitives: flat record -------------------------------------------------

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

// --- 2. Company: deep nesting -------------------------------------------------

final case class GeoPoint(lat: Double, lng: Double)

final case class Address(
  street: String,
  city: String,
  zip: String,
  country: String,
  location: GeoPoint,
)

final case class Employee(
  id: Long,
  name: String,
  email: String,
  salary: BigDecimal,
  address: Address,
  manager: Option[Long],
)

final case class Department(
  name: String,
  budget: BigDecimal,
  head: Employee,
  staff: List[Employee],
)

final case class Company(
  name: String,
  founded: Int,
  hq: Address,
  departments: List[Department],
)

// --- 3. Geometry: sealed hierarchy (GeoJSON-flavoured) -----------------------

/**
 * A `[lng, lat]` position. Modelled as a record rather than a tuple/array so
 * every library encodes it the same shape without extra configuration.
 */
final case class Position(lng: Double, lat: Double)

enum Geometry derives CanEqual:
  case Point(coordinates: Position)
  case MultiPoint(coordinates: List[Position])
  case LineString(coordinates: List[Position])
  case MultiLineString(coordinates: List[List[Position]])
  case Polygon(coordinates: List[List[Position]])
  case MultiPolygon(coordinates: List[List[List[Position]]])
  case GeometryCollection(geometries: List[Geometry])

final case class Feature(id: String, geometry: Geometry, properties: Map[String, String])

final case class FeatureCollection(features: List[Feature])

// --- 4. Batch: large collections -------------------------------------------------

final case class Event(
  id: Long,
  kind: String,
  actor: String,
  payload: Map[String, String],
  tags: Set[String],
  values: List[Double],
)

final case class Batch(
  generatedAt: Long,
  events: Vector[Event],
  offsets: Map[String, Long],
)
