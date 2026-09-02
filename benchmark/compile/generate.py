#!/usr/bin/env python3
"""Generate a standalone single-library project that derives codecs for N models.

Usage:
    generate.py <library> <n> <out_dir>

Produces, in <out_dir>:
    project.scala   - pins the compiler + only this library's dependency
    Models.scala    - N independent case classes (8-14 fields) + n//4 sums of 6 cases
    Derive.scala    - one codec instance per generated type

Models are kept independent (no cross-references) so every library derives the
same N + n//4 codecs and the sweep measures count-scaling, not nesting depth
(runtime nesting is covered by the Company model in the JMH suite).

`gencodec` is AVSystem GenCodec on **Scala 2.13** — a different compiler, so its
row in the report is explicitly flagged. Everything else is Scala 3.
"""
import sys
from pathlib import Path

SCALA3 = "3.9.0"
SCALA2 = "2.13.18"

# scala-3 libraries: (dependency directives, extra compiler options)
LIBS = {
    "mcodec": (
        ["//> using dep com.halotukozak::mcodec::0.0.0-BENCH",
         "//> using dep com.halotukozak::made::0.4.1"],
        ["//> using options -Yexplicit-nulls"],
    ),
    "circe": (
        ["//> using dep io.circe::circe-core::0.14.16",
         "//> using dep io.circe::circe-generic::0.14.16"],
        [],
    ),
    "jsoniter": (
        ["//> using dep com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-core::2.40.1",
         "//> using dep com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-macros::2.40.1"],
        [],
    ),
    "upickle": (["//> using dep com.lihaoyi::upickle::4.4.3"], []),
    "zio-json": (["//> using dep dev.zio::zio-json::1.0.0"], []),
    "borer": (
        ["//> using dep io.bullet::borer-core::1.18.0",
         "//> using dep io.bullet::borer-derivation::1.18.0"],
        [],
    ),
    "play-json": (["//> using dep com.typesafe.play::play-json::2.10.8"], []),
}
ALL_LIBS = list(LIBS) + ["gencodec"]

# Libraries whose macro does not derive sealed hierarchies.
NO_SUM = {"play-json"}

FIELD_TYPES = ["Int", "String", "Boolean", "Long", "Double", "Option[String]", "Option[Int]"]


def fields(i: int) -> list[str]:
    return [f"  f{f}: {FIELD_TYPES[(i + f) % len(FIELD_TYPES)]}" for f in range(8 + (i % 7))]


# --- Scala 3 --------------------------------------------------------------------

def gen_models_s3(n: int, with_sums: bool) -> str:
    out = ["package gen", ""]
    for i in range(n):
        out += [f"final case class M{i}(\n" + ",\n".join(fields(i)) + ",\n)", ""]
    if with_sums:
        for s in range(n // 4):
            cases = "\n".join(
                f"  case C{s}_{c}(a: {FIELD_TYPES[(s + c) % len(FIELD_TYPES)]}, b: Int)" for c in range(6))
            out += [f"enum E{s} derives CanEqual:\n{cases}", ""]
    return "\n".join(out)


def gen_derive_s3(lib: str, n: int) -> str:
    sums = 0 if lib in NO_SUM else n // 4
    lines = [_derive_s3(lib, f"M{i}") for i in range(n)]
    for s in range(sums):
        if lib == "upickle":
            lines += [f"given ReadWriter[E{s}.C{s}_{c}] = macroRW" for c in range(6)]
        lines.append(_derive_s3(lib, f"E{s}", is_sum=True))
    header = {
        "mcodec": "import gen.*\nimport halotukozak.mcodec.*\n",
        "circe": "import gen.*\nimport io.circe.Codec\nimport io.circe.generic.semiauto.*\n",
        "jsoniter": ("import gen.*\nimport com.github.plokhotnyuk.jsoniter_scala.core.*\n"
                     "import com.github.plokhotnyuk.jsoniter_scala.macros.*\n"),
        "upickle": "import gen.*\nimport upickle.default.*\n",
        "zio-json": "import gen.*\nimport zio.json.*\n",
        "borer": "import gen.*\nimport io.bullet.borer.Codec\nimport io.bullet.borer.derivation.MapBasedCodecs.*\n",
        "play-json": "import gen.*\nimport play.api.libs.json.*\n",
    }[lib]
    body = "\n".join("  " + ln for ln in lines)
    return f"{header}\nobject Derived:\n  val marker: Int = 0\n{body}\n"


def _derive_s3(lib: str, ty: str, is_sum: bool = False) -> str:
    return {
        "mcodec": f"given MCodec[{ty}] = MCodec.derived",
        "circe": f"given Codec[{ty}] = deriveCodec",
        "jsoniter": f"given JsonValueCodec[{ty}] = JsonCodecMaker.make(CodecMakerConfig.withAllowRecursiveTypes(true))",
        "upickle": f"given ReadWriter[{ty}] = macroRW",
        "zio-json": f"given JsonCodec[{ty}] = DeriveJsonCodec.gen",
        "borer": f"given Codec[{ty}] = " + ("deriveAllCodecs" if is_sum else "deriveCodec"),
        "play-json": f"given Format[{ty}] = Json.format",
    }[lib]


# --- Scala 2.13 / GenCodec ----------------------------------------------------

def gen_gencodec(n: int, out: Path) -> None:
    (out / "project.scala").write_text("\n".join([
        f"//> using scala {SCALA2}",
        "//> using dep com.avsystem.commons::commons-core::2.29.0",
        "//> using options -Wconf:any:silent",
        "",
    ]))
    models = ["// format: off", "package gen", "",
              "import com.avsystem.commons.serialization.GenCodec", ""]
    for i in range(n):
        models += [
            f"final case class M{i}(\n" + ",\n".join("  " + f.strip() for f in fields(i)) + ",\n)",
            f"object M{i} {{ implicit val c: GenCodec[M{i}] = GenCodec.materialize }}",
            "",
        ]
    for s in range(n // 4):
        cases = "\n".join(
            f"  final case class C{s}_{c}(a: {FIELD_TYPES[(s + c) % len(FIELD_TYPES)]}, b: Int) extends E{s}"
            for c in range(6))
        models += [
            f"sealed trait E{s}",
            f"object E{s} {{\n{cases}\n  implicit val c: GenCodec[E{s}] = GenCodec.materializeRecursively\n}}",
            "",
        ]
    (out / "Models.scala").write_text("\n".join(models))
    # derivation lives in the companions; a marker keeps the file non-empty
    (out / "Derive.scala").write_text("// format: off\npackage gen\nobject Derived { val marker: Int = 0 }\n")


# --- entry point -------------------------------------------------------------

def main() -> None:
    if len(sys.argv) != 4:
        raise SystemExit(__doc__)
    lib, n, out = sys.argv[1], int(sys.argv[2]), Path(sys.argv[3])
    if lib not in ALL_LIBS:
        raise SystemExit(f"unknown library '{lib}', pick one of {ALL_LIBS}")
    out.mkdir(parents=True, exist_ok=True)

    if lib == "gencodec":
        gen_gencodec(n, out)
        return

    deps, opts = LIBS[lib]
    (out / "project.scala").write_text("\n".join(
        [f"//> using scala {SCALA3}", *deps, "//> using options -Wconf:any:silent", *opts, ""]))
    (out / "Models.scala").write_text(gen_models_s3(n, with_sums=lib not in NO_SUM))
    (out / "Derive.scala").write_text(gen_derive_s3(lib, n))


if __name__ == "__main__":
    main()
