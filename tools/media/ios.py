"""Records the demo on the iOS simulator capsa-demo: the release build, English, light, 12:00 in the status bar.

XCUITest in tools/media/ios plays the scenes: simctl can record but cannot tap. Taps come back from the test as
points, and media.encode draws them as spots: a recording of the simulator shows no touches.
"""
import json
import os
import re
import shutil
import signal
import subprocess
import sys
import time
from datetime import datetime

import media

SIMULATOR = "capsa-demo"
BUNDLE = "com.mairwunnx.capsa.samples"
LAG = 0.05  # the test logs a tap just before it sends it
DRIVER = media.WORK / "ios-driver"
IO = media.WORK / "ios-io"


def simctl(*args, check=True):
    return media.run("xcrun", "simctl", *args, check=check)


def newest_iphone():
    """The newest iOS runtime and the newest iPhone Pro it supports, unless CAPSA_SIM_DEVICE names a device type."""
    runtimes = [r for r in json.loads(simctl("list", "runtimes", "-j"))["runtimes"] if r["platform"] == "iOS" and r["isAvailable"]]
    if not runtimes:
        sys.exit("No iOS runtime: install one in Xcode, Settings, Components")
    newest = max(tuple(map(int, r["version"].split("."))) for r in runtimes)
    runtime = next(r for r in runtimes if tuple(map(int, r["version"].split("."))) == newest)
    phones = [d for d in runtime["supportedDeviceTypes"] if d["productFamily"] == "iPhone"]
    wanted = os.environ.get("CAPSA_SIM_DEVICE")
    if wanted:
        device = next((d for d in phones if d["name"] == wanted), None) or sys.exit(f"{runtime['name']} has no {wanted}")
    else:
        pros = [d for d in phones if re.fullmatch(r"iPhone \d+ Pro", d["name"])]
        device = max(pros, key=lambda d: int(d["name"].split()[1])) if pros else phones[-1]
    return runtime, device


def simulator():
    """A simulator of its own, so the language and the status bar of the other simulators stay as they are."""
    devices = json.loads(simctl("list", "devices", "-j"))["devices"]
    for runtime_devices in devices.values():
        for device in runtime_devices:
            if device["name"] == SIMULATOR and device["isAvailable"]:
                return device["udid"]
    runtime, device = newest_iphone()
    print(f"Creating the simulator {SIMULATOR}: {device['name']}, {runtime['name']}", flush=True)
    return simctl("create", SIMULATOR, device["identifier"], runtime["identifier"]).strip()


class Session:
    def __init__(self):
        self.udid = simulator()
        self.boot()
        if "en" != simctl("spawn", self.udid, "defaults", "read", "-g", "AppleLanguages").split()[1].strip('",'):
            # English on screen and an English keyboard. The simulator reads them at boot.
            simctl("spawn", self.udid, "defaults", "write", "-g", "AppleLanguages", "-array", "en")
            simctl("spawn", self.udid, "defaults", "write", "-g", "AppleLocale", "en_US")
            simctl("spawn", self.udid, "defaults", "write", "-g", "AppleKeyboards", "-array", "en_US@sw=QWERTY;hw=Automatic", "emoji@sw=Emoji")
            simctl("shutdown", self.udid)
            self.boot()
        # The keyboard otherwise greets a new simulator with a slide-to-type tip over its keys.
        simctl("spawn", self.udid, "defaults", "write", "com.apple.Preferences", "DidShowContinuousPathIntroduction", "-bool", "true")
        noon = datetime.now().astimezone().replace(hour=12, minute=0, second=0, microsecond=0)
        simctl("status_bar", self.udid, "override", "--time", noon.isoformat(timespec="milliseconds"), "--batteryState", "charged",
               "--batteryLevel", "100", "--cellularMode", "active", "--cellularBars", "4", "--wifiMode", "active", "--wifiBars", "3",
               "--dataNetwork", "wifi", "--operatorName", "")
        simctl("ui", self.udid, "appearance", "light")
        self.install_app()
        self.build_driver()

    def boot(self):
        simctl("boot", self.udid, check=False)
        simctl("bootstatus", self.udid, "-b")

    def install_app(self):
        # The release build: the crash-free screen survives its bug there instead of crashing.
        products = media.WORK / "ios-app"
        if not os.environ.get("CAPSA_SKIP_BUILD"):
            subprocess.run(["xcodebuild", "-project", "kotlin/samples/iosApp/iosApp.xcodeproj", "-scheme", "iosApp", "-configuration", "Release",
                            "-sdk", "iphonesimulator", "-destination", f"id={self.udid}", "-derivedDataPath", str(products), "-quiet", "build"],
                           cwd=media.ROOT, check=True)
        app = next((products / "Build/Products/Release-iphonesimulator").glob("*.app"))
        simctl("install", self.udid, app)

    def build_driver(self):
        DRIVER.mkdir(parents=True, exist_ok=True)
        media.run("xcodegen", "generate", "--quiet", "--spec", media.ROOT / "tools/media/ios/project.yml", "--project", DRIVER)
        media.run("xcodebuild", "build-for-testing", "-project", DRIVER / "CapsaMediaDriver.xcodeproj", "-scheme", "DriverUITests",
                  "-destination", f"id={self.udid}", "-derivedDataPath", DRIVER / "derived", "-quiet")

    def record(self, example):
        test = "test" + "".join(part.capitalize() for part in example.split("-"))
        for attempt in range(4):
            taps, ok = self.take(example, test)
            if ok:
                break
            print(f"{example}: the scene didn't go as planned on attempt {attempt + 1}, recording again", flush=True)
        media.save_taps(example, "ios", taps)
        return taps

    def take(self, example, test):
        shutil.rmtree(IO, ignore_errors=True)
        IO.mkdir(parents=True)
        env = dict(os.environ, TEST_RUNNER_DRIVER_DIR=str(IO))
        runner = subprocess.Popen(["xcodebuild", "test-without-building", "-project", str(DRIVER / "CapsaMediaDriver.xcodeproj"),
                                   "-scheme", "DriverUITests", "-destination", f"id={self.udid}", "-derivedDataPath", str(DRIVER / "derived"),
                                   f"-only-testing:DriverUITests/Scenes/{test}"], env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        while not (IO / "ready").exists():
            if runner.poll() is not None:
                sys.exit(f"{test} ended before its scene:\n" + runner.stdout.read()[-3000:])
            time.sleep(0.1)
        video = media.raw(example, "ios")
        recorder = subprocess.Popen(["xcrun", "simctl", "io", self.udid, "recordVideo", "--codec=h264", "--force", str(video)],
                                    stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        start = time.time()
        time.sleep(1.2)
        go = time.time() - start
        (IO / "go").write_text("")
        while not (IO / "done").exists() and runner.poll() is None:
            time.sleep(0.1)
        time.sleep(0.7)
        recorder.send_signal(signal.SIGINT)
        recorder.wait()
        output = runner.communicate()[0]
        if not (IO / "done").exists():
            sys.exit(f"{test} failed:\n" + output[-3000:])
        width = int(media.run("ffprobe", "-v", "error", "-select_streams", "v", "-show_entries", "stream=width", "-of", "csv=p=0", video).strip())
        scale = width / float((IO / "width").read_text())
        taps = [{"t": t["t"] + go + LAG, "x": t["x"] * scale, "y": t["y"] * scale, "ms": t["ms"]} for t in json.loads((IO / "taps.json").read_text())]
        return taps, (IO / "ok").exists()

    def close(self):
        print(f"The simulator keeps running: stop it with xcrun simctl shutdown {SIMULATOR}", flush=True)
