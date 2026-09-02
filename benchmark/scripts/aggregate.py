#!/usr/bin/env python3
"""Normalise raw benchmark output into tidy CSVs and refresh the report tables.

    aggregate.py [--results DIR] [--report FILE]

Inputs  (in --results, default benchmark/results):
    jmh.json            JMH result file (scala-cli ... --jmh -- -rf json -rff ...)
    compile.csv         from bench_compile.py
    compile-phases.csv  from bench_compile.py (optional)

Outputs:
    serde.csv           library,model,op,score_ops_s,error,payload_bytes
    <report>            markdown tables refreshed between <!-- BENCH:* --> markers
"""
import argparse
import csv
import json
import re
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent.parent
MODEL_LABEL = {
    "PrimitivesBench": "Primitives",
    "CompanyBench": "Company (nested)",
    "FeatureCollectionBench": "Geometry (ADT)",
    "BatchBench": "Batch (collections)",
}


def load_jmh(path: Path, fixed_lib: str | None = None) -> list[dict]:
    """`fixed_lib` labels a file whose benchmarks carry no `lib` @Param
    (the standalone GenCodec / Scala 2.13 suite)."""
    rows = []
    for e in json.loads(path.read_text()):
        cls, method = e["benchmark"].rsplit(".", 1)
        rows.append({
            "library": fixed_lib or e["params"]["lib"],
            "model": MODEL_LABEL.get(cls.rsplit(".", 1)[-1], cls.rsplit(".", 1)[-1]),
            "op": method,
            "score": float(e["primaryMetric"]["score"]),
            "error": float(e["primaryMetric"].get("scoreError") or 0.0),
        })
    return rows


def md_table(headers: list[str], rows: list[list[str]]) -> str:
    out = ["| " + " | ".join(headers) + " |",
           "| " + " | ".join("---" for _ in headers) + " |"]
    for r in rows:
        out.append("| " + " | ".join(str(c) for c in r) + " |")
    return "\n".join(out)


def serde_tables(rows: list[dict]) -> dict[str, str]:
    libs = sorted({r["library"] for r in rows})
    tables = {}
    for op in ("write", "read"):
        models = [m for m in MODEL_LABEL.values() if any(r["model"] == m for r in rows)]
        header = ["library"] + models
        body = []
        for lib in libs:
            line = [lib]
            for m in models:
                hit = [r for r in rows if r["library"] == lib and r["model"] == m and r["op"] == op]
                line.append(f"{hit[0]['score']:,.0f}" if hit else "n/a")
            body.append(line)
        tables[f"serde-{op}"] = (
            f"_Throughput, ops/s (higher is better) — `{op}`._\n\n" + md_table(header, body))
    return tables


def compile_table(path: Path) -> str:
    if not path.exists():
        return "_No `compile.csv` yet — run `bench_compile.py`._"
    by_lib: dict[str, dict[int, str]] = {}
    cbytes: dict[str, int] = {}
    ns: set[int] = set()
    for row in csv.DictReader(path.open()):
        n = int(row["n"])
        ns.add(n)
        val = f"{float(row['mean_s']):.1f}" if row["mean_s"] and row["ok"] == "True" else "err"
        by_lib.setdefault(row["library"], {})[n] = val
        if row["class_bytes"] and row["class_bytes"] != "0":
            cbytes[row["library"]] = int(row["class_bytes"])
    order = sorted(ns)
    hi = order[-1]
    header = ["library"] + [f"N={n}" for n in order] + [f"bytecode @ N={hi}"]
    body = []
    for lib in sorted(by_lib):
        kb = f"{cbytes[lib] / 1024:,.0f} KB" if lib in cbytes else "-"
        label = "gencodec ¹" if lib == "gencodec" else lib
        body.append([label] + [by_lib[lib].get(n, "-") for n in order] + [kb])
    note = ("\n\n¹ GenCodec is compiled by **Scala 2.13.18**, not the Scala 3 compiler —"
            " a cross-ecosystem data point, not a like-for-like measurement." if "gencodec" in by_lib else "")
    return ("_Clean compile wall time, seconds (lower is better); last column is total emitted "
            "`.class` bytes. N = derived codecs._\n\n" + md_table(header, body) + note)


def phase_table(path: Path) -> str:
    if not path.exists():
        return ""
    keep = ["parser", "typer", "posttyper", "inlining", "erasure", "genBCode"]
    agg: dict[str, dict[str, float]] = {}
    for row in csv.DictReader(path.open()):
        ph = row["phase"]
        if ph in keep:
            agg.setdefault(row["library"], {})[ph] = float(row["seconds"])
    if not agg:
        return ""
    header = ["library"] + keep + ["typer+inlining"]
    body = []
    for lib in sorted(agg):
        d = agg[lib]
        deriv = d.get("typer", 0) + d.get("inlining", 0)
        body.append([lib] + [f"{d.get(p, 0):.2f}" for p in keep] + [f"**{deriv:.2f}**"])
    return ("\n\n### Compiler phase breakdown\n\n"
            "_Seconds per scalac phase for the N=50 project. Macro / mirror derivation and "
            "`inline` expansion land in **typer** and **inlining** — their sum is the derivation cost._\n\n"
            + md_table(header, body))


def inject(report: Path, blocks: dict[str, str]) -> None:
    text = report.read_text()
    for key, content in blocks.items():
        pat = re.compile(rf"(<!-- BENCH:{key} START -->).*?(<!-- BENCH:{key} END -->)", re.S)
        if pat.search(text):
            text = pat.sub(lambda m: f"{m.group(1)}\n{content}\n{m.group(2)}", text)
        else:
            print(f"  (marker BENCH:{key} not found in report)")
    report.write_text(text)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--results", default=str(REPO / "benchmark" / "results"))
    ap.add_argument("--report", default=str(REPO / "docs" / "benchmarks.md"))
    args = ap.parse_args()
    res, report = Path(args.results), Path(args.report)

    blocks: dict[str, str] = {}
    jmh = res / "jmh.json"
    if jmh.exists():
        rows = load_jmh(jmh)
        gc = res / "jmh-gencodec.json"
        if gc.exists():
            rows += load_jmh(gc, fixed_lib="gencodec")
            print("  + gencodec (Scala 2.13)")
        with (res / "serde.csv").open("w", newline="") as f:
            w = csv.writer(f)
            w.writerow(["library", "model", "op", "score_ops_s", "error"])
            for r in rows:
                w.writerow([r["library"], r["model"], r["op"], f"{r['score']:.1f}", f"{r['error']:.1f}"])
        blocks.update(serde_tables(rows))
        print(f"  serde.csv: {len(rows)} rows")
    else:
        print("  (no jmh.json)")

    blocks["compile"] = compile_table(res / "compile.csv") + phase_table(res / "compile-phases.csv")

    if report.exists():
        inject(report, blocks)
        print(f"  refreshed {report}")


if __name__ == "__main__":
    main()
