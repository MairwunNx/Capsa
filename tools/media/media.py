"""What both platforms share: the paths, the examples, and the encoder with its touch spots."""
import json
import pathlib
import shutil
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
WORK = ROOT / "build" / "media"
OUT = ROOT / "docs" / "media"

# Where each clip starts and ends in its recording, in seconds. Clock skips its first ticks to stay under 12 seconds.
EXAMPLES = {
    "counter": {},
    "search": {},
    "like": {},
    "clock": {"start": 1.2, "end": 12.6, "hold": 0.5},
    "profile": {},
    "dashboard": {},
    "crash-free": {},
    "two-screens": {},
}

SPOT = 140  # the diameter of a touch spot on a 1080 px wide screen


def run(*args, check=True, **kwargs):
    """Runs a command and returns its output as text."""
    result = subprocess.run([str(a) for a in args], capture_output=True, text=True, **kwargs)
    if check and result.returncode != 0:
        sys.exit(f"{' '.join(map(str, args))} failed:\n{result.stdout}{result.stderr}")
    return result.stdout


def check_tools(platforms):
    needed = ["ffmpeg", "ffprobe", "img2webp"] + (["xcrun", "xcodebuild", "xcodegen"] if "ios" in platforms else [])
    missing = [t for t in needed if shutil.which(t) is None]
    if missing:
        sys.exit("Install first: " + ", ".join(missing) + " (see tools/media/README.md)")


def raw(example, platform, extension="mp4"):
    (WORK / "raw").mkdir(parents=True, exist_ok=True)
    return WORK / "raw" / f"{example}-{platform}.{extension}"


def spot():
    """A grey translucent disc with a white ring, the same on both platforms: Android's show_touches is not used."""
    path = WORK / "spot.png"
    if not path.exists():
        WORK.mkdir(parents=True, exist_ok=True)
        r = SPOT / 2
        d = f"hypot(X-{r},Y-{r})"
        alpha = f"if(lte({d},{r - 4}),if(gte({d},{r - 9}),220,90),0)"
        grey = f"if(gte({d},{r - 9}),255,60)"
        run("ffmpeg", "-y", "-v", "error", "-f", "lavfi", "-i", f"color=c=black:s={SPOT}x{SPOT},format=rgba",
            "-vf", f"geq=r='{grey}':g='{grey}':b='{grey}':a='{alpha}'", "-frames:v", "1", path)
    return path


def duration(path):
    return float(run("ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", path).strip())


def encode(example, platform, taps):
    """Turns the raw recording into the mp4 for LFS, the animated WebP for the README, and the first frame as a PNG.

    taps holds {"t", "x", "y", "ms"} in seconds and pixels of the raw recording. Each tap shows as a spot.
    """
    options = EXAMPLES[example]
    source = raw(example, platform)
    width = int(run("ffprobe", "-v", "error", "-select_streams", "v", "-show_entries", "stream=width", "-of", "csv=p=0", source).strip())
    size = round(SPOT * width / 1080)
    # The recorders write a frame only when the screen changes, so fps comes first: a spot needs frames to show on.
    graph = "[0:v]fps=30[v];"
    previous = "v"
    if taps:
        graph += f"[1:v]scale={size}:{size},split={len(taps)}" + "".join(f"[s{i}]" for i in range(len(taps))) + ";"
        for i, tap in enumerate(taps):
            begin, end = tap["t"], tap["t"] + max(tap["ms"] / 1000, 0.3)
            x, y = round(tap["x"] - size / 2), round(tap["y"] - size / 2)
            graph += f"[{previous}][s{i}]overlay=x={x}:y={y}:enable='between(t,{begin:.3f},{end:.3f})'[o{i}];"
            previous = f"o{i}"
    trim = f"trim=start={options.get('start', 0.6)}" + (f":end={options['end']}" if "end" in options else "")
    graph += f"[{previous}]{trim},setpts=PTS-STARTPTS,scale=720:-2:flags=lanczos,tpad=stop_mode=clone:stop_duration={options.get('hold', 1)},format=yuv420p[out]"
    OUT.mkdir(parents=True, exist_ok=True)
    stem = OUT / f"{example}-{platform}"
    mp4 = stem.with_suffix(".mp4")
    run("ffmpeg", "-y", "-v", "error", "-i", source, "-i", spot(), "-filter_complex", graph, "-map", "[out]", "-an",
        "-c:v", "libx264", "-preset", "slow", "-crf", "24", "-profile:v", "high", "-movflags", "+faststart", mp4)
    frames = WORK / "frames" / stem.name
    shutil.rmtree(frames, ignore_errors=True)
    frames.mkdir(parents=True)
    run("ffmpeg", "-y", "-v", "error", "-i", mp4, "-vf", "fps=15,scale=360:-2:flags=lanczos", frames / "f%04d.png")
    # Homebrew's ffmpeg has no WebP encoder, img2webp from libwebp does it. 67 ms a frame is 15 fps.
    run("img2webp", "-loop", "0", "-min_size", "-lossy", "-q", "70", "-m", "6", "-d", "67", *sorted(frames.iterdir()), "-o", stem.with_suffix(".webp"))
    run("ffmpeg", "-y", "-v", "error", "-i", mp4, "-frames:v", "1", stem.with_suffix(".png"))
    kib = stem.with_suffix(".webp").stat().st_size // 1024
    return f"{stem.relative_to(ROOT)}: {duration(mp4):.1f} s, WebP {kib} KiB"


def save_taps(example, platform, taps):
    raw(example, platform, "json").write_text(json.dumps(taps))
