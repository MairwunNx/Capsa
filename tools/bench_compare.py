#!/usr/bin/env python3
"""Compares a fresh JMH run of capsa-benchmarks with the baseline in the repository.

The run comes from `./gradlew :capsa-benchmarks:jvmGcBenchmark` (JMH JSON with the GC profiler). The script prints
a table "benchmark, base, now, diff" and exits with 1 when any benchmark got slower than the threshold, 25 % by default.

    python3 tools/bench_compare.py                    # compare the last run with the baseline
    python3 tools/bench_compare.py --write-baseline   # turn the last run into the new baseline

Only the standard library, Python 3.9 or newer.
"""

from __future__ import annotations

import argparse
import datetime
import json
import os
import platform
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_RUN = ROOT / "kotlin/capsa-benchmarks/build/reports/benchmarks/gc/jvm.json"
DEFAULT_BASELINE = ROOT / "kotlin/capsa-benchmarks/baseline/jvm.json"
# JMH 1.37 names the metric without the leading dot that older versions printed.
ALLOC_METRICS = ("gc.alloc.rate.norm", "·gc.alloc.rate.norm")


def benchmark_name(entry: dict) -> str:
    """FactPassBenchmark.passOneFact(capabilities=10): the class and the method without the package, then the parameters."""
    name = ".".join(entry["benchmark"].split(".")[-2:])
    params = entry.get("params") or {}
    if params:
        name += "(" + ", ".join(f"{key}={value}" for key, value in sorted(params.items())) + ")"
    return name


def read_run(path: Path) -> tuple[dict, dict]:
    """Returns the benchmarks of a JMH JSON report by name, and the JVM it ran on."""
    entries = json.loads(path.read_text(encoding="utf-8"))
    if not entries:
        sys.exit(f"{path} holds no benchmarks")
    benchmarks = {}
    for entry in entries:
        primary = entry["primaryMetric"]
        secondary = entry.get("secondaryMetrics") or {}
        alloc = next((secondary[key] for key in ALLOC_METRICS if key in secondary), None)
        error = primary.get("scoreError")
        benchmarks[benchmark_name(entry)] = {
            "mode": entry["mode"],
            "score": round(primary["score"], 3),
            # JMH writes NaN for the error of a single iteration.
            "error": round(error, 3) if isinstance(error, (int, float)) and error == error else None,
            "unit": primary["scoreUnit"],
            "allocBytesPerOp": round(alloc["score"], 1) if alloc else None,
        }
    first = entries[0]
    jvm = {"jdk": first.get("jdkVersion"), "vm": f"{first.get('vmName')} {first.get('vmVersion')}"}
    return benchmarks, jvm


def cpu_name() -> str:
    """The processor of this machine, for the baseline and for the warning about different machines."""
    try:
        if sys.platform == "darwin":
            command = ["sysctl", "-n", "machdep.cpu.brand_string"]
            return subprocess.run(command, capture_output=True, text=True, check=True).stdout.strip()
        for line in Path("/proc/cpuinfo").read_text(encoding="utf-8").splitlines():
            if line.startswith("model name"):
                return line.split(":", 1)[1].strip()
    except (OSError, subprocess.CalledProcessError):
        pass
    return platform.processor() or platform.machine()


def change_of(mode: str, base: float, now: float) -> float:
    """The change as a share of the baseline: negative when the benchmark got slower, whatever the JMH mode."""
    if base == 0 or now == 0:
        return 0.0
    if mode == "thrpt":
        return now / base - 1
    return base / now - 1


def format_score(value: float) -> str:
    return f"{value:,.1f}".replace(",", " ")


def format_alloc(value: float | None) -> str:
    return "–" if value is None else f"{format_score(value)} B"


def compare(run_path: Path, baseline_path: Path, threshold: float) -> int:
    run, jvm = read_run(run_path)
    baseline = json.loads(baseline_path.read_text(encoding="utf-8"))
    base_benchmarks = baseline["benchmarks"]

    machine = baseline.get("machine", {})
    notes = []
    if machine.get("cpu") and machine["cpu"] != cpu_name():
        notes.append(f"The baseline comes from {machine['cpu']}, this run from {cpu_name()}: the difference includes the machine.")
    if machine.get("jdk") and machine["jdk"] != jvm["jdk"]:
        notes.append(f"The baseline ran on JDK {machine['jdk']}, this run on JDK {jvm['jdk']}.")

    rows = []
    failures = []
    for name in sorted(set(base_benchmarks) | set(run)):
        base = base_benchmarks.get(name)
        now = run.get(name)
        if now is None:
            rows.append((name, format_score(base["score"]), "missing", "–", format_alloc(base.get("allocBytesPerOp")), "–"))
            failures.append(f"{name} is in the baseline but not in the run")
            continue
        if base is None:
            rows.append((name, "new", format_score(now["score"]), "–", "–", format_alloc(now["allocBytesPerOp"])))
            continue
        change = change_of(now["mode"], base["score"], now["score"])
        slower = change < -threshold
        rows.append((
            name,
            format_score(base["score"]),
            format_score(now["score"]),
            f"{change:+.1%}" + (" ❌" if slower else ""),
            format_alloc(base.get("allocBytesPerOp")),
            format_alloc(now["allocBytesPerOp"]),
        ))
        if slower:
            failures.append(f"{name} got {-change:.1%} slower, the limit is {threshold:.0%}")

    unit = next(iter(run.values()))["unit"]
    header = ("Benchmark", f"Base, {unit}", f"Now, {unit}", "Diff", "Alloc base", "Alloc now")
    lines = [
        f"Baseline from {baseline.get('date', '?')}, threshold {threshold:.0%}.",
        "",
        "| " + " | ".join(header) + " |",
        "|" + "|".join(["---"] + ["---:"] * (len(header) - 1)) + "|",
        *("| " + " | ".join(row) + " |" for row in rows),
    ]
    if notes:
        lines += ["", *(f"> {note}" for note in notes)]
    if failures:
        lines += ["", *(f"- {failure}" for failure in failures)]
    report = "\n".join(lines)
    print(report)

    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as out:
            out.write("## Benchmarks\n\n" + report + "\n")

    if failures:
        print(f"\nFAILED: {len(failures)} of {len(rows)} benchmarks", file=sys.stderr)
        return 1
    print(f"\nOK: {len(rows)} benchmarks within {threshold:.0%} of the baseline")
    return 0


def write_baseline(run_path: Path, baseline_path: Path) -> int:
    run, jvm = read_run(run_path)
    baseline = {
        "machine": {
            "cpu": cpu_name(),
            "os": f"{platform.system()} {platform.release()} {platform.machine()}",
            "jdk": jvm["jdk"],
            "vm": jvm["vm"],
        },
        "date": datetime.date.today().isoformat(),
        "settings": "JMH throughput, 3 warmups and 5 iterations of 1 s, 1 fork, -prof gc",
        "benchmarks": run,
    }
    baseline_path.parent.mkdir(parents=True, exist_ok=True)
    baseline_path.write_text(json.dumps(baseline, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Wrote {len(run)} benchmarks to {baseline_path}")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Compare a JMH run of capsa-benchmarks with the baseline.")
    parser.add_argument("run", nargs="?", type=Path, default=DEFAULT_RUN, help="JMH JSON report of the run")
    parser.add_argument("--baseline", type=Path, default=DEFAULT_BASELINE, help="baseline JSON in the repository")
    parser.add_argument("--threshold", type=float, default=25.0, help="allowed slowdown in percent, 25 by default")
    parser.add_argument("--write-baseline", action="store_true", help="write the run as the new baseline instead of comparing")
    args = parser.parse_args()

    if not args.run.is_file():
        sys.exit(f"No run at {args.run}: run ./gradlew :capsa-benchmarks:jvmGcBenchmark in kotlin/ first")
    if args.write_baseline:
        return write_baseline(args.run, args.baseline)
    if not args.baseline.is_file():
        sys.exit(f"No baseline at {args.baseline}")
    return compare(args.run, args.baseline, args.threshold / 100)


if __name__ == "__main__":
    sys.exit(main())
