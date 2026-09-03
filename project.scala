//> using scala 3.9.0

//> using dep com.halotukozak::made::0.6.0

//> using test.dep org.scalameta::munit::1.3.5
//> using test.dep org.scalameta::munit-scalacheck::1.3.1

//> using options -deprecation -feature -new-syntax -unchecked
//> using options -language:noAutoTupling
//> using options -Vprofile -Xprint-inline
//> using options -Xcheck-macros -Ycheck:macros -Ydebug-flags -Ydebug-missing-refs
//> using options -Ycheck:all
//> using options -Yexplain-lowlevel -Yexplicit-nulls
//> using options -Yshow-suppressed-errors -Yshow-var-bounds
//> using options -Wsafe-init -Werror -Wunused:all
// Dotty misreports the position of Product methods (canEqual, productArity, ...) as
// 0 for locally-scoped case classes under -Xcheck-macros -- a compiler-internal
// diagnostic bug, not a real code issue.
//> using options "-Wconf:msg=Missing symbol position.*:s"
////> using options -Yprofile-enabled" -Yprofile-trace:debug/compile-trace.json"

//> using publish.organization com.halotukozak
//> using publish.name mcodec
//> using publish.computeVersion git:tag
//> using publish.description "mcodec - GenCodec-style serialization for Scala 3, built on Made"
//> using publish.url https://github.com/halotukozak/mcodec
//> using publish.license MIT
//> using publish.vcs github:halotukozak/mcodec
//> using publish.repository central
//> using publish.developer "halotukozak|Bartłomiej Kozak|https://github.com/halotukozak"
