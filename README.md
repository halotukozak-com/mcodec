# mcodec

**GenCodec-style serialization for Scala 3** — a format-agnostic, streaming `Input`/`Output` core with type class
derivation built on [M&DE](https://github.com/halotukozak/made). Ships with a JSON backend.

> **Experimental.** Pinned to Scala **3.9.0-RC4** (Scala Next), required by Made (`made_3:0.1.2`). JVM only.

## Overview

mcodec is a serialization library in the spirit of [AVSystem GenCodec](https://github.com/AVSystem/scala-commons): a
single `MCodec[T]` type class both reads and writes, and the wire format is decoupled from the codecs. Codecs talk to
an abstract streaming `Input`/`Output`, so the same `MCodec[T]` works across any backend that implements those.

- **One type class for read and write** — `MCodec[T]` with `read(input)` / `write(output, value)`
- **Format-agnostic core** — codecs target a streaming `Input`/`Output`; the JSON backend is just one implementation
- **Automatic derivation** — `derives MCodec` for case classes, enums, and sealed hierarchies, powered by M&DE mirrors
- **Annotation-aware** — `@name` to rename fields and ADT cases on the wire, `@flatten` / `@defaultCase` for ADTs
- **Combinators** — `transform`, `transformed`, `nullable`, and `makeLazy` for building codecs from existing ones
- **Explicit nulls** — compiled with `-Yexplicit-nulls`; nullability is expressed in the types

Built-in codecs cover the primitives, `BigInt`/`BigDecimal`, `UUID`, `Option`, `Either`, tuples, and the common
collections (`List`, `Vector`, `Seq`, `Set`, `Map`).

## Installation

> mcodec has not had its first release yet. The coordinates below are how it will be published to Maven Central under
> `com.halotukozak` once tagged. Until then, build from source (see [Build](#build)).

### scala-cli

```scala
//> using scala 3.9.0-RC4
//> using dep com.halotukozak::mcodec::<version>
```

### sbt

```scala
scalaVersion := "3.9.0-RC4"
libraryDependencies += "com.halotukozak" %% "mcodec" % "<version>"
```

### mill

```scala
def scalaVersion = "3.9.0-RC4"
def mvDeps = Seq(mvn"com.halotukozak::mcodec::<version>")
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
scala-cli --power compile .
scala-cli --power test .
scala-cli --power fmt .
```

## Acknowledgements

mcodec is inspired by the [**AVSystem commons**](https://github.com/AVSystem/scala-commons) by [**ghik
**](https://github.com/ghik), whose `GenCodec` is the model for the codec design
