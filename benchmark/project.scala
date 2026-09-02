// mcodec benchmark module — isolated from the main build.
// Run from the repo root:  scala-cli --power --jmh benchmark
//
// This directory is NOT part of the published library. It pins the same Scala
// version as mcodec (3.9.0, forced by Made 0.4.1) and pulls every competitor
// library so the JMH suite can compare them head to head.

//> using scala 3.9.0

// mcodec itself. Publish it into the local Ivy cache first:
//   scala-cli --power publish local . --project-version 0.0.0-BENCH
//> using dep com.halotukozak::mcodec::0.0.0-BENCH
//> using dep com.halotukozak::made::0.5.0

//> using jmh
//> using jmhVersion 1.37

// --- competitor libraries (JSON, Scala 3) -----------------------------------
//> using dep io.circe::circe-core::0.14.16
//> using dep io.circe::circe-parser::0.14.16
//> using dep io.circe::circe-generic::0.14.16
//> using dep com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-core::2.40.1
//> using dep com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-macros::2.40.1
//> using dep com.lihaoyi::upickle::4.4.3
//> using dep dev.zio::zio-json::1.0.0
//> using dep io.bullet::borer-core::1.18.0
//> using dep io.bullet::borer-derivation::1.18.0
//> using dep com.typesafe.play::play-json::2.10.8

// mcodec compiles under -Werror with a long option list; the benchmark code
// pulls in several third-party APIs and doesn't need that strictness.
//> using options -deprecation -feature -new-syntax -Wconf:any:silent
//> using options -Yexplicit-nulls
