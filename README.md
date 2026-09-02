# mcodec

**GenCodec-style serialization for Scala 3** — a format-agnostic, streaming `Input`/`Output` core with type class
derivation built on [M&DE](https://github.com/halotukozak/made). Ships with a JSON backend; BSON and CBOR `Input`/
`Output` implementations exist in-tree but aren't wired up to a public `read`/`write` entry point yet.

> **Experimental.** Pinned to Scala **3.9.0**, required by Made (`made_3:0.4.1`). Cross-built for JVM, Scala.js, and
> Scala Native.

## Overview

mcodec is a serialization library in the spirit of [AVSystem GenCodec](https://github.com/AVSystem/scala-commons): a
single `MCodec[T]` type class both reads and writes, and the wire format is decoupled from the codecs. Codecs talk to
an abstract streaming `Input`/`Output`, so the same `MCodec[T]` works across any backend that implements those.

- **One type class for read and write** — `MCodec[T]` with `read(input)` / `write(output, value)`
- **Format-agnostic core** — codecs target a streaming `Input`/`Output`; the JSON backend is just one implementation
- **Automatic derivation** — `derives MCodec` for case classes, enums, and sealed hierarchies, powered by M&DE mirrors
- **Annotation-aware** — `@name` to rename fields and ADT cases on the wire, `@flatten` / `@defaultCase` for ADTs,
  `@stringEnum` to encode an enum case by name, `@outOfOrder` to accept a field regardless of wire position, and
  `@transientDefault` to omit a field from output when it equals its declared default
- **Combinators** — `transform`, `transformed`, `nullable`, and `makeLazy` for building codecs from existing ones
- **Explicit nulls** — compiled with `-Yexplicit-nulls`; nullability is expressed in the types

Built-in codecs cover the primitives, `BigInt`/`BigDecimal`, `UUID`, `Option`, `Either`, tuples, and the common
collections (`List`, `Vector`, `Seq`, `Set`, `Map`).

## Installation

Published to Maven Central under `com.halotukozak`.

### scala-cli

```scala
//> using scala 3.9.0
//> using dep com.halotukozak::mcodec::0.2.0
```

### sbt

```scala
scalaVersion := "3.9.0"
libraryDependencies += "com.halotukozak" %% "mcodec" % "0.2.0"
```

### mill

```scala
def scalaVersion = "3.9.0"
def mvnDeps = Seq(mvn"com.halotukozak::mcodec::0.2.0")
```

## Quickstart

Derive an `MCodec` for a case class and round-trip it through JSON.

```scala
import halotukozak.mcodec.*

case class User(name: String, age: Int) derives MCodec

val json = Json.write(User("Alice", 30)) // {"name":"Alice","age":30}
val user = Json.read[User](json) // User("Alice", 30)
```

Rename fields and ADT cases on the wire with `@name`:

```scala
import halotukozak.mcodec.*
import made.annotation.name

case class User(@name("user_name") name: String, age: Int) derives MCodec

enum Shape derives MCodec:
  @name("circ") case Circle(radius: Double)

Json.write(User("bob", 30)) // {"user_name":"bob","age":30}
Json.write[Shape](Shape.Circle(2.0)) // {"circ":{"radius":2.0}}
```

Build codecs from existing ones with the combinators on `MCodec`:

```scala
import halotukozak.mcodec.*

opaque type Email = String
given MCodec[Email] = MCodec[String].transform(identity, identity)

val nullableInt: MCodec[Int | Null] = MCodec[Int].nullable
```

## Build

```sh
scala-cli --power compile . --exclude benchmark
scala-cli --power test . --exclude benchmark
scala-cli --power fmt .
```

`--exclude benchmark` keeps the separate benchmark build (competitor deps, JMH)
out of the library build — `scala-cli` has no directive for this.

## Benchmarks

[`docs/benchmarks.md`](docs/benchmarks.md) compares mcodec's **compile time** and
**serialization throughput** against circe, jsoniter-scala, uPickle, zio-json,
borer, play-json and AVSystem GenCodec (the design mcodec is modelled on). The
suite lives in [`benchmark/`](benchmark/) (a separate scala-cli build) and is
regenerated with `benchmark/scripts/run_all.sh`.

## Acknowledgements

mcodec is inspired by the [**AVSystem commons**](https://github.com/AVSystem/scala-commons) by [**ghik
**](https://github.com/ghik), whose `GenCodec` is the model for the codec design
