package halotukozak.mcodec

import java.lang as jl
import scala.reflect.{classTag, ClassTag}

// Kept separate from JavaCodecs (and out of MCodec's mixin scope) because
// java.lang.Enum.valueOf(Class, String) has no Scala.js/Scala Native javalib
// implementation, so this given can only exist on the JVM. Still resolves the
// same way for JVM consumers: it's a top-level given in this package, so it's
// in implicit scope for anyone in the package or importing halotukozak.mcodec.*.

// The Class[E] cast is the standard reflect idiom for Java enum reflection, not a null cast.
given jEnumCodec: [E <: jl.Enum[E]: ClassTag] => MCodec[E] = MCodec.nonNullString(
  name => jl.Enum.valueOf(classTag[E].runtimeClass.asInstanceOf[Class[E]], name),
  _.name.nn,
)
