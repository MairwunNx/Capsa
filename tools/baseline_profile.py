#!/usr/bin/env python3
"""Splits the baseline profile of the demo app into the profiles of the Capsa libraries.

`./gradlew :samples:androidApp:generateBaselineProfile` writes one profile for the whole demo: Compose, AndroidX, the samples,
and Capsa. This script keeps the rules of Capsa and writes each rule into the library that holds its class:

    kotlin/<module>/src/androidMain/baselineProfiles/baseline-prof.txt

The Android Gradle plugin puts that file into the AAR, and the apps that use Capsa compile its hot paths ahead of time.
The classes of each module come from its compiled Android classes, so build the libraries first.
tools/device_bench.sh runs this script after the generator.

    python3 tools/baseline_profile.py [app-profile]

Only the standard library, Python 3.9 or newer.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
KOTLIN = ROOT / "kotlin"
DEFAULT_PROFILE = KOTLIN / "samples/androidApp/src/release/generated/baselineProfiles/baseline-prof.txt"
# The libraries an app ships. capsa-host shares the package of capsa-core, so the classes decide, not the package.
MODULES = ("capsa-core", "capsa-host", "capsa-viewmodel", "capsa-compose")
CAPSA = "com/mairwunnx/capsa/"
SAMPLES = "com/mairwunnx/capsa/samples/"
# A rule starts with optional flags (H, S, P) and the class descriptor: HSPLcom/example/Foo;->bar()V or Lcom/example/Foo;
RULE = re.compile(r"^[HSP]*L([^;]+);")


def classes_of(module: str) -> set[str]:
    """The internal names of the classes the module compiles for Android, such as com/mairwunnx/capsa/CapsaRuntime."""
    root = KOTLIN / module / "build/classes/kotlin/android/main"
    if not root.is_dir():
        sys.exit(f"No compiled classes in {root}: run ./gradlew :{module}:compileAndroidMain in kotlin/ first")
    return {path.relative_to(root).with_suffix("").as_posix() for path in root.rglob("*.class")}


def main() -> int:
    parser = argparse.ArgumentParser(description="Split the baseline profile of the demo app into the Capsa libraries.")
    parser.add_argument("profile", nargs="?", type=Path, default=DEFAULT_PROFILE, help="baseline profile of the demo app")
    args = parser.parse_args()
    if not args.profile.is_file():
        sys.exit(f"No profile at {args.profile}: run ./gradlew :samples:androidApp:generateBaselineProfile in kotlin/ first")

    owner = {name: module for module in MODULES for name in classes_of(module)}
    rules: dict[str, list[str]] = {module: [] for module in MODULES}
    unknown = set()
    for line in args.profile.read_text(encoding="utf-8").splitlines():
        match = RULE.match(line)
        if not match or not match.group(1).startswith(CAPSA) or match.group(1).startswith(SAMPLES):
            continue
        # D8 names the lambdas it desugars in the app, such as CapsaHostCore$$ExternalSyntheticLambda0: they belong to the outer class.
        module = owner.get(match.group(1)) or owner.get(match.group(1).split("$$")[0])
        if module is None:
            unknown.add(match.group(1))
            continue
        rules[module].append(line)

    for module, lines in rules.items():
        out = KOTLIN / module / "src/androidMain/baselineProfiles/baseline-prof.txt"
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text("".join(f"{line}\n" for line in sorted(set(lines))), encoding="utf-8")
        print(f"{module}: {len(set(lines))} rules in {out.relative_to(ROOT)}")
    if unknown:
        # A class of Capsa that no shipped module holds, such as capsa-test. Its rules stay out.
        print(f"Skipped the rules of {len(unknown)} classes outside {', '.join(MODULES)}: {', '.join(sorted(unknown)[:5])}…")
    return 0


if __name__ == "__main__":
    sys.exit(main())
