#!/usr/bin/env bash
# Full benchmark run: publish mcodec locally, run the JMH serde matrix, run the
# compile-time sweep, aggregate to CSV, refresh docs/benchmarks.md and the charts.
#
#   benchmark/scripts/run_all.sh [quick|full]
#
#   quick  1 fork, short iterations, compile sizes 0,1,10,25   (a few minutes)
#   full   3 forks, compile sizes 0,1,10,25,50,100             (~1h)
set -euo pipefail

MODE="${1:-full}"
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RESULTS="$REPO/benchmark/results"
mkdir -p "$RESULTS"
cd "$REPO"

echo "==> publishing mcodec 0.0.0-BENCH to the local Ivy cache"
scala-cli --power publish local . --exclude benchmark --project-version 0.0.0-BENCH --doc=false

if [[ "$MODE" == "quick" ]]; then
  JMH_ARGS=(-f 1 -wi 3 -w 1 -i 5 -r 1)
  SIZES="0,1,10,25"
else
  JMH_ARGS=(-f 3 -wi 5 -w 1 -i 10 -r 1)
  SIZES="0,1,10,25,50,100"
fi

echo "==> serialization / deserialization matrix (JMH, Scala 3)"
scala-cli --power run benchmark --exclude gencodec --jmh -- \
  'bench.bench.*' "${JMH_ARGS[@]}" -rf json -rff "$RESULTS/jmh.json" -foe true

echo "==> GenCodec serialization (separate Scala 2.13 build)"
scala-cli --power run benchmark/gencodec --jmh -- \
  'bench.gencodec.*' "${JMH_ARGS[@]}" -rf json -rff "$RESULTS/jmh-gencodec.json" -foe true

echo "==> compilation-time sweep"
python3 benchmark/compile/bench_compile.py --sizes "$SIZES" --out "$RESULTS"

echo "==> aggregating + refreshing report"
python3 benchmark/scripts/aggregate.py --results "$RESULTS"
benchmark/scripts/plot.py --results "$RESULTS" || echo "(plot skipped — need 'uv' or matplotlib)"

echo "==> done. See docs/benchmarks.md and benchmark/results/*.csv"
