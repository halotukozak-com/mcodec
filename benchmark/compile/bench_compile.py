#!/usr/bin/env python3
"""Compile-time sweep: for each library and each model count N, measure a clean
(no compilation server, no incremental) compile of N derived codecs.

    bench_compile.py [--libs a,b,c] [--sizes 0,1,10,25,50,100] [--runs 3]
                     [--profile-n 50] [--out DIR] [--smoke]

Writes to <out> (default: benchmark/results):
    compile-raw.csv      library,n,run,seconds,ok
    compile.csv          library,n,mean_s,stdev_s,min_s,class_bytes,ok
    compile-phases.csv   library,phase,seconds        (only for --profile-n)

Absolute numbers include a fixed ~JVM+scalac startup cost (measured as the N=0
row); the meaningful signal is the slope as N grows. Run on a quiet machine.
"""
import argparse
import csv
import json
import shutil
import statistics
import subprocess
import sys
import tempfile
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parent.parent
GENERATE = HERE / "generate.py"
DEFAULT_LIBS = ["mcodec", "circe", "jsoniter", "upickle", "zio-json", "borer", "play-json", "gencodec"]
DEFAULT_SIZES = [0, 1, 10, 25, 50, 100]
# -Yprofile-trace is Scala 3 only; gencodec (Scala 2.13) gets wall-time rows only.
NO_PROFILE = {"gencodec"}


def run(cmd, **kw):
    return subprocess.run(cmd, capture_output=True, text=True, **kw)


def generate(lib: str, n: int, dest: Path) -> None:
    r = run([sys.executable, str(GENERATE), lib, str(n), str(dest)])
    if r.returncode != 0:
        raise RuntimeError(f"generate failed: {r.stderr}")


def compile_once(src: Path, extra: list[str] | None = None) -> tuple[float, bool, str]:
    # --server=false: no Bloop, no incremental compiler -> a clean measurement.
    cmd = ["scala-cli", "--power", "compile", str(src), "--server=false"]
    if extra:
        cmd += extra
    t0 = time.perf_counter()
    r = run(cmd, cwd=REPO)
    dt = time.perf_counter() - t0
    return dt, r.returncode == 0, r.stdout + r.stderr


def class_bytes(src: Path) -> int:
    total = 0
    for p in (src / ".scala-build").rglob("*.class"):
        total += p.stat().st_size
    return total


def parse_trace(trace: Path) -> dict[str, float]:
    """Sum scalac phase durations (seconds) from a -Yprofile-trace file.

    dotty emits paired B/E events with `cat == "phase"`; ts is microseconds.
    """
    data = json.loads(trace.read_text())
    events = data["traceEvents"] if isinstance(data, dict) else data
    phases: dict[str, float] = {}
    stacks: dict[str, list[tuple[str, float]]] = {}
    for e in events:
        if e.get("cat") != "phase":
            continue
        tid = e.get("tid", "?")
        if e.get("ph") == "B":
            stacks.setdefault(tid, []).append((e["name"], e["ts"]))
        elif e.get("ph") == "E" and stacks.get(tid):
            name, start = stacks[tid].pop()
            phases[name] = phases.get(name, 0.0) + (e["ts"] - start) / 1e6
    return phases


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--libs", default=",".join(DEFAULT_LIBS))
    ap.add_argument("--sizes", default=",".join(map(str, DEFAULT_SIZES)))
    ap.add_argument("--runs", type=int, default=3)
    ap.add_argument("--profile-n", type=int, default=50)
    ap.add_argument("--out", default=str(REPO / "benchmark" / "results"))
    ap.add_argument("--smoke", action="store_true", help="sizes=0,1 runs=1, no profile")
    args = ap.parse_args()

    libs = args.libs.split(",")
    sizes = [0, 1] if args.smoke else sorted(set(map(int, args.sizes.split(","))))
    runs = 1 if args.smoke else args.runs
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    raw_rows, summary_rows, phase_rows = [], [], []
    work = Path(tempfile.mkdtemp(prefix="mcodec-compile-"))
    print(f"workdir: {work}")
    try:
        for lib in libs:
            for n in sizes:
                dest = work / f"{lib}-{n}"
                generate(lib, n, dest)
                # warm dependency resolution once (not timed)
                compile_once(dest)
                shutil.rmtree(dest / ".scala-build", ignore_errors=True)
                times, ok_all = [], True
                for k in range(runs):
                    dt, ok, log = compile_once(dest)
                    ok_all &= ok
                    raw_rows.append([lib, n, k, f"{dt:.3f}", ok])
                    if ok:
                        times.append(dt)
                    else:
                        print(f"  !! {lib} n={n} run={k} FAILED\n{log[-800:]}")
                    shutil.rmtree(dest / ".scala-build", ignore_errors=True)
                cb = 0
                if times:
                    compile_once(dest)  # rebuild to measure class size
                    cb = class_bytes(dest)
                summary_rows.append([
                    lib, n,
                    f"{statistics.mean(times):.3f}" if times else "",
                    f"{statistics.pstdev(times):.3f}" if len(times) > 1 else "0",
                    f"{min(times):.3f}" if times else "",
                    cb, ok_all,
                ])
                print(f"  {lib:10s} n={n:<4d} "
                      f"{('%.2fs' % statistics.mean(times)) if times else 'ERR':>8s}  {cb} class bytes")

            if not args.smoke and args.profile_n and lib not in NO_PROFILE:
                dest = work / f"{lib}-{args.profile_n}-prof"
                generate(lib, args.profile_n, dest)
                trace = dest / "trace.json"
                _, ok, log = compile_once(
                    dest, ["-O", "-Yprofile-enabled", "-O", f"-Yprofile-trace:{trace}"])
                if ok and trace.exists():
                    for phase, secs in sorted(parse_trace(trace).items(), key=lambda x: -x[1]):
                        phase_rows.append([lib, phase, f"{secs:.4f}"])
                else:
                    print(f"  (no profile trace for {lib})")

        _write(out / "compile-raw.csv", ["library", "n", "run", "seconds", "ok"], raw_rows)
        _write(out / "compile.csv",
               ["library", "n", "mean_s", "stdev_s", "min_s", "class_bytes", "ok"], summary_rows)
        if phase_rows:
            _write(out / "compile-phases.csv", ["library", "phase", "seconds"], phase_rows)
        print(f"\nwrote {out}/compile.csv"
              + ("" if not phase_rows else f" + compile-phases.csv"))
    finally:
        shutil.rmtree(work, ignore_errors=True)


def _write(path: Path, header: list[str], rows: list) -> None:
    with path.open("w", newline="") as f:
        w = csv.writer(f)
        w.writerow(header)
        w.writerows(rows)


if __name__ == "__main__":
    main()
