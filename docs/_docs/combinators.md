---
title: Combinators
order: 3
---

# Combinators

Some types already map onto a representation with a codec: a newtype over `String`, an `opaque type`, a value that is
really an `Int`. Build their `MCodec` from that one instead of deriving.

## `transform` — map an existing codec

`transform` turns an `MCodec[T]` into an `MCodec[U]` given both directions of the conversion. It is `inline`, so
nothing extra survives to runtime:

```scala
import halotukozak.mcodec.*

opaque type Email = String
given MCodec[Email] = MCodec[String].transform(identity, identity)

case class UserId(value: Int)
given MCodec[UserId] = MCodec[Int].transform(_.value, UserId(_))
```

The arguments are `(onWrite: U => T, onRead: T => U)`, write first.

## `transformed` — map with failure handling

The same map, run inside a `try`/`catch`. A `NonFatal` throw from `onRead` surfaces as a `ReadFailure`, from `onWrite`
as a `WriteFailure`. Use it when the conversion can fail, such as parsing or validation. The arguments are read-first
and curried:

```scala
import halotukozak.mcodec.*

final case class Tagged(raw: String)
given MCodec[Tagged] = MCodec[String].transformed(Tagged(_))(_.raw)
```

## `nullable` — allow an explicit `null`

mcodec compiles with `-Yexplicit-nulls`, so nullability shows in the types. `nullable` widens `MCodec[T]` to
`MCodec[T | Null]`. It writes `null` for the null case and reads a JSON `null` back to it:

```scala
import halotukozak.mcodec.*

val nullableInt: MCodec[Int | Null] = MCodec[Int].nullable

given MCodec[String | Null] = MCodec[String].nullable
```

## `makeLazy` — break a derivation cycle

`MCodec.makeLazy` holds off on the wrapped codec until first use. `derives MCodec` already ties the knot for
self-referential types, so this is only for cycles you wire by hand:

```scala
import halotukozak.mcodec.*

final case class Expr(sub: Option[Expr])

given exprCodec: MCodec[Expr] = MCodec.makeLazy(MCodec.derived[Expr])
```

## `create` — a codec from two functions

`MCodec.create` takes a read function and a write function against the raw `Input`/`Output`:

```scala
import halotukozak.mcodec.*

final case class Wrap(n: Int)

given MCodec[Wrap] = MCodec.create[Wrap](
  in => Wrap(MCodec.read[Int](in)),
  (out, w) => MCodec.write(out, w.n),
)
```

For common shapes there are typed variants: `createSimple` (one scalar via `in.readInt()` / `out.writeInt(…)`),
`createList`, and `createObject`. They let you drive the streaming API without routing through another `MCodec`.

## `forceTransientDefaults`

Wraps a codec so it writes every [`@transientDefault`](annotations.html) field even when it matches the default,
recursing into nested products. Reads are unaffected.

```scala
import halotukozak.mcodec.*
import halotukozak.mcodec.annotation.transientDefault

case class Config(@transientDefault verbose: Boolean = false, name: String) derives MCodec

val verbose = MCodec.forceTransientDefaults(MCodec[Config])
```
