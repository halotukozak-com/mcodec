# benchmark/

Comparative benchmarks for mcodec: **compile time** and **serialization
throughput** against circe, jsoniter-scala, uPickle, zio-json, borer, play-json
and GenCodec. Results and methodology live in
[`../docs/benchmarks.md`](../docs/benchmarks.md).

This directory is a **separate Mill module** (`benchmark` in `build.mill`),
depending on `jvm` directly rather than a published artifact. It is not
published, and **there is no CI for it** — a shared runner is too noisy for
comparative numbers — run it locally with `./mill benchmark.runAll` when you
want fresh numbers.

`benchmark/gencodec/` is a **second, nested Scala 2.13 module** (AVSystem
GenCodec has no Scala 3 release). Run the Scala 3 suite with
`./mill benchmark.runJmh` and that one with `./mill benchmark.gencodec.runJmh`.

## Layout

```
src/models/             shared, library-neutral data models + sample instances
src/codecs/             per-library codec instances behind a common `JsonCodec` facade
src/bench/              JMH state classes (library is a @Param)
gencodec/src/           nested Scala 2.13 module — AVSystem GenCodec, runtime rows only
compile/generate.py     emit a standalone single-library project of N derived codecs
compile/bench_compile.py   clean-compile sweep over N, per library (scala-cli, for a
                        build-tool-neutral measurement across every library)
scripts/aggregate.py    JMH json + compile csv  ->  tidy csv + refreshed report tables
scripts/plot.py         charts (uv + matplotlib)
results/                generated csv/json (git-ignored)
```

## Prerequisites

- `scala-cli` (only for the compile-time sweep — kept build-tool-neutral so
  every library, mcodec included, is measured the same way)
- `python3` for the compile sweep and aggregation
- `uv` (or a `matplotlib` on `PATH`) for charts — optional

## Running

```sh
# everything (publishes mcodec 0.0.0-BENCH to the local Ivy cache itself, for
# the compile-time sweep below)
./mill benchmark.runAll                # ~1h, the committed dataset
./mill benchmark.runAll --mode quick   # a few minutes, for a fast sanity pass

# serialization only, some libraries
./mill benchmark.runJmh 'CompanyBench.*' -p lib=mcodec,circe
./mill benchmark.gencodec.runJmh '.*'   # GenCodec (Scala 2.13)

# compile time only
python3 benchmark/compile/bench_compile.py --libs mcodec,circe --sizes 0,10,50
python3 benchmark/compile/bench_compile.py --smoke        # N=0,1 sanity check
```

## Adding a library

1. `src/codecs/<Lib>Codecs.scala` — provide `JsonCodec[A]` for `Primitives`,
   `Company`, `FeatureCollection`, `Batch` (skip what the library can't derive).
2. Register it in `src/codecs/Codecs.scala` and add its id to `JsonCodec.Lib`.
3. Add it to the `@Param` arrays in `src/bench/SerdeBench.scala`.
4. Add a dependency in `build.mill`'s `benchmark` module and a `_derive_line`
   case in `compile/generate.py`.
