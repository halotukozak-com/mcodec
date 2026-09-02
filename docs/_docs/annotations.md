---
title: Annotations
order: 2
---

# Annotations

mcodec reads annotations off the derived [M&DE](https://made.halotukozak.com) mirror at compile time. They shift the
wire shape and cost nothing at runtime. `@name` and `@transparent` live in `halotukozak.made.annotation`; the rest are
in `halotukozak.mcodec.annotation`.

## `@name` — rename on the wire

Overrides a field label, or an ADT case label in the discriminator:

```scala
import halotukozak.mcodec.*
import halotukozak.made.annotation.name

case class User(@name("user_name") name: String, age: Int) derives MCodec

enum Shape derives MCodec:
  @name("circ") case Circle(radius: Double)

Json.write(User("bob", 30))            // {"user_name":"bob","age":30}
Json.write[Shape](Shape.Circle(2.0))   // {"circ":{"radius":2.0}}
```

Reads accept the renamed label. The Scala-side name stays as written.

## `@transparent` — unwrap a single-field case class

A `@transparent` wrapper serializes as its bare inner value, with no object around it. Reach for it when you want a
newtype but a real case class rather than an `opaque type`:

```scala
import halotukozak.mcodec.*
import halotukozak.made.annotation.transparent

@transparent case class Email(value: String) derives MCodec

Json.write(Email("a@b.com"))    // "a@b.com"
Json.read[Email]("\"a@b.com\"") // Email("a@b.com")
```

## `@flatten` — inline discriminator for ADTs

`@flatten` drops the nested-discriminator object and puts a discriminator **field** next to the case's own fields. The
key defaults to `_case`; pass a string to change it:

```scala
import halotukozak.mcodec.*
import halotukozak.mcodec.annotation.flatten

@flatten enum FlatShape derives MCodec:
  case Circle(radius: Double)
  case Rectangle(w: Double, h: Double)
  case Point

Json.write[FlatShape](FlatShape.Circle(2.0))   // {"_case":"Circle","radius":2.0}
Json.write[FlatShape](FlatShape.Point)         // {"_case":"Point"}

@flatten("type") enum TypedShape derives MCodec:
  case Square(side: Double)

Json.write[TypedShape](TypedShape.Square(3.0)) // {"type":"Square","side":3.0}
```

On read the discriminator can sit anywhere in the object, not only first. A `@flatten` key that clashes with a case's
own field name fails the read.

## `@defaultCase` — fallback for an unknown discriminator

Marks one case to decode into when the discriminator is missing or unrecognised, instead of failing. Two of them in
one hierarchy is a compile error. It works with the nested encoding and the flat one:

```scala
import halotukozak.mcodec.*
import halotukozak.mcodec.annotation.defaultCase

enum NestedCmd derives MCodec:
  case Start(n: Int)
  @defaultCase case Unknown

Json.read[NestedCmd]("{}")            // NestedCmd.Unknown
Json.read[NestedCmd]("{\"Nope\":{}}") // NestedCmd.Unknown
```

## `@stringEnum` — encode a C-style enum as a string

For an enum whose cases are all singletons, `@stringEnum` writes the bare case name in place of the default
`{"Red":{}}`:

```scala
import halotukozak.mcodec.*
import halotukozak.mcodec.annotation.stringEnum

@stringEnum enum Color derives MCodec:
  case Red, Green, Blue

Json.write[Color](Color.Red)  // "Red"
Json.read[Color]("\"Green\"") // Color.Green
```

An unknown string fails the read. Without the annotation the enum keeps the empty-object encoding.

## `@transientDefault` — omit a field that equals its default

When a field's value is `==` to its declared default, mcodec leaves it out of the output:

```scala
import halotukozak.mcodec.*
import halotukozak.mcodec.annotation.transientDefault

case class TD(@transientDefault x: Int = 7, keep: String) derives MCodec

Json.write(TD(7, "a")) // {"keep":"a"}
Json.write(TD(9, "a")) // {"x":9,"keep":"a"}
```

The check is structural `==` against the declared default, so a collection or `Option` default has to compare equal
(`Nil == Nil`, `None == None`). To write every `@transientDefault` field back in, for a debug dump say, wrap the codec:

```scala
import halotukozak.mcodec.*
import halotukozak.mcodec.annotation.transientDefault

case class TD(@transientDefault x: Int = 7, keep: String) derives MCodec

val forced = MCodec.forceTransientDefaults(MCodec[TD])
Json.write(TD(7, "a"))(using forced) // {"x":7,"keep":"a"}
```

`forceTransientDefaults` recurses into nested products.

## `@outOfOrder` — accept a field in any wire position

Fields read in declaration order by default. `@outOfOrder` lets one be picked up wherever it lands in the input. Write
order does not change:

```scala
import halotukozak.mcodec.*
import halotukozak.mcodec.annotation.outOfOrder

case class OOO(@outOfOrder tag: String, value: Int) derives MCodec

Json.read[OOO]("{\"value\":5,\"tag\":\"hello\"}") // OOO("hello", 5)
```
