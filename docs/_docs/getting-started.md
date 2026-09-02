---
title: Getting Started
order: 1
---

# Getting Started

mcodec serializes Scala 3 values in the style of [AVSystem GenCodec](https://github.com/AVSystem/scala-commons). One
`MCodec[T]` type class covers both directions. The wire format lives outside the codec: codecs call an abstract
streaming `Input`/`Output`, and JSON is one implementation of that pair.

## Add the dependency

mcodec is on Maven Central under `com.halotukozak`. It targets Scala **3.9.0** (the version
[M&DE](https://made.halotukozak.com) needs for derivation) and cross-builds for JVM, Scala.js and Scala Native.

```scala
//> using scala 3.9.0
//> using dep com.halotukozak::mcodec::0.2.0
```

## Derive a codec

Add `derives MCodec` to a case class, enum, or sealed hierarchy:

```scala
import halotukozak.mcodec.*

case class User(name: String, age: Int) derives MCodec

val json = Json.write(User("Alice", 30)) // {"name":"Alice","age":30}
val user = Json.read[User](json)          // User("Alice", 30)
```

`Json.write` returns a `String` and `Json.read[T]` parses one. Both look up a `given MCodec[T]`, which `derives MCodec`
places in the companion object.

Derivation recurses. The codec for `User` is built from the codecs for `String` and `Int`; the codec for a record with
a nested case class from the codecs for its fields. Self-referential types resolve too:

```scala
import halotukozak.mcodec.*

enum Tree derives MCodec:
  case Leaf(value: Int)
  case Branch(left: Tree, right: Tree)
```

## Derive without `derives`

When you can't edit the type, assign the codec yourself:

```scala
import halotukozak.mcodec.*

case class User(name: String, age: Int)

given MCodec[User] = MCodec.derived
```

## What's built in

The library ships codecs for the common types, so you never derive these:

- primitives (`Int`, `Long`, `Double`, `Boolean`, `String`, …), `BigInt`, `BigDecimal`, `UUID`
- `Option[T]`, `Either[L, R]`, tuples
- `List`, `Vector`, `Seq`, `Set`, `Map`

A `Map[K, V]` becomes a JSON object when `K` serializes to a string, and an array of `[key, value]` pairs when it
doesn't.

## ADT wire shape

A sealed hierarchy defaults to a **nested discriminator**: a one-key object whose key is the case name.

```scala
import halotukozak.mcodec.*

enum Shape derives MCodec:
  case Circle(radius: Double)
  case Rectangle(w: Double, h: Double)
  case Point

Json.write[Shape](Shape.Circle(2.0))   // {"Circle":{"radius":2.0}}
Json.write[Shape](Shape.Point)         // {"Point":{}}
```

[Annotations](annotations.html) covers the alternatives: `@flatten` for an inline `_case` field, `@stringEnum` for a
bare string, `@defaultCase` for a fallback, plus the field-level controls.

## Next steps

- [Annotations](annotations.html) — rename fields and cases, switch the ADT encoding, drop defaulted fields
- [Combinators](combinators.html) — build codecs from existing ones (`transform`, `nullable`, `makeLazy`, …)
- [Backends](backends.html) — the format-agnostic `Input`/`Output` core
- [Benchmarks](benchmarks.html) — compile time and throughput against the field
