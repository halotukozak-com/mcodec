# benchmark/

Comparative benchmarks for mcodec: **compile time** and **serialization
throughput** against circe, jsoniter-scala, uPickle, zio-json, borer, play-json
and GenCodec. Results and methodology live in
[`../docs/benchmarks.md`](../docs/benchmarks.md).

This directory is a **separate scala-cli build** — `//> using exclude` does not
keep it out of `scala-cli .`, so the main CI/publish workflows pass
`--exclude benchmark`. It is not published, and **there is no CI for it** — run it
locally with `scripts/run_all.sh` when you want fresh numbers.

`benchmark/gencodec/` is a **second, nested Scala 2.13 build** (AVSystem GenCodec
has no Scala 3 release). Build the Scala 3 suite with
`scala-cli --power compile benchmark --exclude gencodec` and that one with
`scala-cli --power compile benchmark/gencodec`.

## Layout

```
project.scala          scala 3.9.0 + JMH + every competitor library
src/models/            shared, library-neutral data models + sample instances
src/codecs/            per-library codec instances behind a common `JsonCodec` facade
src/bench/             JMH state classes (library is a @Param)
gencodec/              nested Scala 2.13 build — AVSystem GenCodec, runtime rows only
compile/generate.py    emit a standalone single-library project of N derived codecs
compile/bench_compile.py   clean-compile sweep over N, per library
scripts/aggregate.py   JMH json + compile csv  ->  tidy csv + refreshed report tables
scripts/plot.py        charts (uv + matplotlib)
scripts/run_all.sh     the whole pipeline
results/               generated csv/json (git-ignored)
```

## Prerequisites

- `scala-cli` (JMH support is behind `--power`)
- `python3` for the compile sweep and aggregation
- `uv` (or a `matplotlib` on `PATH`) for charts — optional

## Running

```sh
# mcodec is consumed as a normal dependency; publish the working tree first
# (from the repo root):
scala-cli --power publish local . --exclude benchmark --project-version 0.0.0-BENCH --doc=false

# everything
benchmark/scripts/run_all.sh full        # ~1h, the committed dataset
benchmark/scripts/run_all.sh quick       # a few minutes, for a fast sanity pass

# serialization only, some libraries
scala-cli --power run benchmark --exclude gencodec --jmh -- 'CompanyBench.*' -p lib=mcodec,circe
scala-cli --power run benchmark/gencodec --jmh -- 'bench.gencodec.*'   # GenCodec (Scala 2.13)

# compile time only
python3 benchmark/compile/bench_compile.py --libs mcodec,circe --sizes 0,10,50
python3 benchmark/compile/bench_compile.py --smoke        # N=0,1 sanity check
```

## Adding a library

1. `src/codecs/<Lib>Codecs.scala` — provide `JsonCodec[A]` for `Primitives`,
   `Company`, `FeatureCollection`, `Batch` (skip what the library can't derive).
2. Register it in `src/codecs/Codecs.scala` and add its id to `JsonCodec.Lib`.
3. Add it to the `@Param` arrays in `src/bench/SerdeBench.scala`.
4. Add a dependency + `_derive_line` case in `compile/generate.py`.
