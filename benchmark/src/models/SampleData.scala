package bench.models

import scala.util.Random

/**
 * Deterministic sample instances of every model.
 *
 * Sizes are chosen so each payload is big enough to be JIT-stable and to make
 * throughput differences visible, but small enough that a full JMH matrix
 * finishes in minutes rather than hours.
 */
object SampleData:

  private def rng() = new Random(42L)

  val primitives: Primitives =
    Primitives(
      id = 9876543210L,
      name = "widget-controller-unit",
      active = true,
      score = 87.635213,
      count = 42,
      ratio = 0.7314159,
      tag = Some("prod"),
      notes = None,
      createdAt = 1_725_000_000_000L,
      updatedAt = 1_725_600_000_000L,
      version = 7,
      checksum = -123456789012345L,
    )

  /** ~40 employees across 4 departments, 3 levels of nesting under Company. */
  val company: Company =
    val r = rng()
    def address(i: Int) =
      Address(
        street = s"${100 + i} Example Street",
        city = if i % 2 == 0 then "Springfield" else "Shelbyville",
        zip = f"${10000 + i}%05d",
        country = "US",
        location = GeoPoint(37.0 + r.nextDouble(), -122.0 - r.nextDouble()),
      )
    def employee(i: Int) =
      Employee(
        id = i.toLong,
        name = s"Employee $i",
        email = s"employee$i@example.com",
        salary = BigDecimal(50000 + (r.nextInt(80000))),
        address = address(i),
        manager = if i == 0 then None else Some((i / 5).toLong),
      )
    val departments = (0 until 4).toList.map { d =>
      val staff = (0 until 10).toList.map(s => employee(d * 10 + s))
      Department(
        name = s"Department $d",
        budget = BigDecimal(1_000_000 + r.nextInt(5_000_000)),
        head = staff.head,
        staff = staff.tail,
      )
    }
    Company(
      name = "Example Corp",
      founded = 1998,
      hq = address(0),
      departments = departments,
    )

  /**
   * A FeatureCollection with every Geometry variant, including a nested
   * GeometryCollection, so discriminator dispatch is fully exercised.
   */
  val featureCollection: FeatureCollection =
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
    FeatureCollection(
      geometries.zipWithIndex.map { (g, i) =>
        Feature(
          id = s"feature-$i",
          geometry = g,
          properties = Map("name" -> s"Region $i", "kind" -> g.getClass.getSimpleName.nn, "verified" -> "true"),
        )
      },
    )

  /** 500 events; each event carries small maps/sets/lists. ~150 KB of JSON. */
  val batch: Batch =
    val r = rng()
    val kinds = Vector("push", "pull_request", "issue", "release", "fork", "star")
    def event(i: Int) =
      Event(
        id = i.toLong,
        kind = kinds(r.nextInt(kinds.length)),
        actor = s"user-${r.nextInt(2000)}",
        payload = Map(
          "repo" -> s"org/repo-${r.nextInt(100)}",
          "ref" -> s"refs/heads/branch-${r.nextInt(30)}",
          "sha" -> f"${r.nextLong()}%016x",
        ),
        tags = (0 to r.nextInt(4)).map(t => s"tag-$t").toSet,
        values = List.fill(r.nextInt(6))(r.nextDouble() * 100),
      )
    Batch(
      generatedAt = 1_725_000_000_000L,
      events = (0 until 500).toVector.map(event),
      offsets = (0 until 20).map(p => s"partition-$p" -> r.nextLong()).toMap,
    )
