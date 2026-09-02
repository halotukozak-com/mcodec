// GenCodec (AVSystem commons) — the library mcodec is modelled on. It has no
// Scala 3 release, so this is a SEPARATE Scala 2.13 build, nested under
// benchmark/ but not part of it: build the Scala 3 suite with
//   scala-cli --power compile benchmark --exclude gencodec
// and this one with
//   scala-cli --power compile benchmark/gencodec
//
// Only the runtime serialization numbers feed docs/benchmarks.md; compile time
// is not comparable across the 2.13 and 3.x compilers.

//> using scala 2.13.18
//> using dep com.avsystem.commons::commons-core::2.29.0
//> using jmh
//> using jmhVersion 1.37
//> using options -Xsource:3
