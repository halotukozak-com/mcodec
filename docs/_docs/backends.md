---
title: Backends
order: 4
---

# Backends

An `MCodec[T]` never names a format. It reads from an abstract
[`Input`](https://github.com/halotukozak-com/mcodec/blob/main/src/mcodec/Input.scala) and writes to an abstract
[`Output`](https://github.com/halotukozak-com/mcodec/blob/main/src/mcodec/Output.scala), a small streaming API. A
*backend* implements that pair for one wire format, and every derived codec runs against it unchanged.

## The `Input` / `Output` model

`Output` offers three things to write:

- `writeNull()`
- `writeSimple(): SimpleOutput`, then one of `writeString` / `writeInt` / `writeLong` / `writeDouble` /
  `writeBoolean` / `writeBigInt` / `writeBigDecimal` (`writeByte`, `writeChar`, `writeBinary`, … sit on top of those)
- `writeList(): ListOutput` and `writeObject(): ObjectOutput`, sequential writers you feed elements or named fields

`Input` mirrors it: `readNull()`, `readSimple()`, `readList()`, `readObject()`, `skip()`, and on backends that can
peek, `peekKind()` — used by `@flatten` and `@outOfOrder`.

A product codec calls `output.writeObject()`, writes each field by name, and closes it. A collection codec calls
`writeList()`. Reading walks `ObjectInput` / `ListInput` with `hasNext`, `nextField()`, and `nextElement()`.

## The JSON backend

`Json` is the only wired-up entry point. `Json.write[T](value)` and `Json.read[T](string)` each need a
`given MCodec[T]`:

```scala
import halotukozak.mcodec.*

case class Point(x: Int, y: Int) derives MCodec

val s: String = Json.write(Point(1, 2)) // {"x":1,"y":2}
val p: Point = Json.read[Point](s)
```

`Json.write` builds the `String` through a `JsonOutput`. `Json.read` parses it with a `JsonReader` behind a
`JsonInput` and checks that nothing is left over.

## BSON and CBOR

`Input` / `Output` implementations for BSON and CBOR sit in the source tree and run in the test suite. There is no
public `Bson` / `Cbor` `read`/`write` object yet, so they are not part of the supported API.

## Writing your own backend

Implement `Input` and `Output`. The `InputAndSimpleInput` / `OutputAndSimpleOutput` helper traits fold the simple
readers and writers in when your format doesn't split them out. Every existing `MCodec[T]` then works against your
backend as is.
