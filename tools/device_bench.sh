#!/usr/bin/env bash
# Runs the Capsa benchmarks on an Android phone connected over USB, and compares them with the last results of that phone.
#
#   tools/device_bench.sh            # microbenchmarks and macrobenchmarks
#   tools/device_bench.sh --profile  # also generates the baseline profile of the libraries
#   tools/device_bench.sh --yes      # skips the question before the run
#
# The microbenchmarks of capsa-benchmarks-android run the classes of capsa-benchmarks in ART. The macrobenchmarks of
# samples/macrobenchmark measure the frames of the demo app and its cold start. The results go to
# kotlin/capsa-benchmarks-android/results/<device>.json with the date, the Android version, and the commit.
# tools/bench_compare.py compares the microbenchmarks with the previous file and allows 30 %: a phone is noisier than a laptop.
# Commit the new file when its numbers are the new reference, `git checkout` it otherwise.
#
# The release doesn't run this script. Run it before a release when you want to see Capsa on a real phone.

set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
kotlin="$root/kotlin"
profile=false
ask=true
for arg in "$@"; do
  case "$arg" in
    --profile) profile=true ;;
    --yes) ask=false ;;
    *) echo "Unknown option: $arg" >&2; exit 2 ;;
  esac
done

# One phone, with USB debugging on.
devices="$(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')"
count="$(printf '%s' "$devices" | grep -c . || true)"
if [ "$count" -eq 0 ]; then
  echo "No phone found. Connect the phone over USB, turn on USB debugging, and allow this computer on the phone." >&2
  exit 1
fi
if [ "$count" -gt 1 ]; then
  echo "More than one device is connected: $(echo $devices). Leave only the phone to measure." >&2
  exit 1
fi
serial="$devices"
shell() { adb -s "$serial" shell "$@" | tr -d '\r'; }

model="$(shell getprop ro.product.model)"
release="$(shell getprop ro.build.version.release)"
sdk="$(shell getprop ro.build.version.sdk)"
battery="$(shell dumpsys battery | awk '$1 == "level:" { print $2; exit }')"
echo "Phone: $model, Android $release (API $sdk), battery $battery %"

# androidx.benchmark asks `su root id` to find out whether it has root. MagiskSU reads that as an interactive shell and
# waits for input forever, so the run hangs before the first measurement. A denial answers at once.
if shell 'su --help 2>&1' | grep -q MagiskSU && shell 'timeout 5 su -c id 2>/dev/null' | grep -q 'uid=0'; then
  echo "Magisk grants root to the shell, and androidx.benchmark hangs on it." >&2
  echo "In Magisk, open Superuser and set Shell to Deny, then run this script again." >&2
  exit 1
fi

if [ "${battery:-0}" -lt 50 ]; then
  echo "The battery is at $battery %. Charge the phone first: a low battery slows the processor down." >&2
  exit 1
fi

if $ask; then
  cat <<'TEXT'

The phone has no root, so its clocks run free and the numbers float. To keep the noise down:
  - turn on airplane mode, leaving USB debugging on;
  - keep the phone on the charger and let it cool down;
  - don't touch it until the run ends, about 20 minutes.

TEXT
  read -r -p "Press Enter to start, Ctrl+C to stop. " _
fi

device_name="$(printf '%s' "$model" | tr '[:upper:] ' '[:lower:]-' | tr -cd 'a-z0-9-')"
results="$kotlin/capsa-benchmarks-android/results/$device_name.json"
outputs="$kotlin/capsa-benchmarks-android/build/outputs/connected_android_test_additional_output"
macro_outputs="$kotlin/samples/macrobenchmark/build/outputs/connected_android_test_additional_output"
export ANDROID_SERIAL="$serial"

cd "$kotlin"
rm -rf "$outputs" "$macro_outputs"

if $profile; then
  echo "== Baseline profile"
  ./gradlew :capsa-core:compileAndroidMain :capsa-host:compileAndroidMain :capsa-viewmodel:compileAndroidMain :capsa-compose:compileAndroidMain \
    :samples:androidApp:generateBaselineProfile
  python3 "$root/tools/baseline_profile.py"
fi

echo "== Microbenchmarks"
./gradlew :capsa-benchmarks-android:connectedAndroidTest
run="$(find "$outputs" -name '*benchmarkData.json' | head -n 1)"

echo "== Macrobenchmarks"
# Twice the default iterations: free clocks need more of them.
./gradlew :samples:macrobenchmark:connectedBenchmarkReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.mairwunnx.capsa.samples.macrobenchmark.FrameTimingBenchmark,com.mairwunnx.capsa.samples.macrobenchmark.StartupBenchmark \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.iterations=20
macro_run="$(find "$macro_outputs" -name '*benchmarkData.json' | head -n 1)"

echo "== Comparison"
status=0
if [ -f "$results" ]; then
  python3 "$root/tools/bench_compare.py" "$run" --baseline "$results" --threshold 30 || status=$?
else
  echo "No earlier results of $model: this run becomes the first one."
fi
python3 "$root/tools/bench_compare.py" "$run" --baseline "$results" --write-baseline
python3 - "$macro_run" "$results" <<'PY'
import json, sys
macro, results = sys.argv[1], sys.argv[2]
report = json.load(open(macro, encoding="utf-8"))
out = json.load(open(results, encoding="utf-8"))
out["macrobenchmarks"] = {
    entry["className"].split(".")[-1] + "." + entry["name"]: {
        metric: {"P50": round(values["P50"], 1), "P90": round(values["P90"], 1), "P99": round(values["P99"], 1)}
        if "P50" in values else {"median": round(values["median"], 1), "minimum": round(values["minimum"], 1), "maximum": round(values["maximum"], 1)}
        for metric, values in {**entry.get("metrics", {}), **entry.get("sampledMetrics", {})}.items()
    }
    for entry in report["benchmarks"]
}
with open(results, "w", encoding="utf-8") as file:
    json.dump(out, file, indent=2, ensure_ascii=False)
    file.write("\n")
for name, metrics in out["macrobenchmarks"].items():
    print(name)
    for metric, values in metrics.items():
        print(f"  {metric}: " + ", ".join(f"{key} {value}" for key, value in values.items()))
PY
echo "Results: $results"
exit $status
