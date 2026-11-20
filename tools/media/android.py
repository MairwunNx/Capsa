"""Records the demo on the Android emulator: the AVD capsa-demo, the release build, the status bar in demo mode.

Touches go in through sendevent on the virtual touchscreen, so the app sees real finger taps. The spots in the video
come from media.encode: on this emulator show_touches draws a black square or nothing at all.
"""
import os
import pathlib
import re
import subprocess
import sys
import time

import media

PACKAGE = "com.mairwunnx.capsa.samples"
AVD = "capsa-demo"
IMAGE = os.environ.get("CAPSA_AVD_IMAGE", "system-images;android-37.2;google_apis_ps16k;arm64-v8a")
DEVICE = os.environ.get("CAPSA_AVD_DEVICE", "pixel_9")
LAG = 0.2  # from the start of a tap command to the first frame that shows it


def sdk():
    home = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or str(pathlib.Path.home() / "Library/Android/sdk")
    return pathlib.Path(home)


def tool(*parts):
    path = sdk().joinpath(*parts)
    if not path.exists():
        sys.exit(f"{path} is missing: install it with sdkmanager (see tools/media/README.md)")
    return path


def ensure_avd():
    emulator = tool("emulator", "emulator")
    if AVD in media.run(emulator, "-list-avds").split():
        return
    bin_dir = tool("cmdline-tools", "latest", "bin")
    image_dir = sdk().joinpath(*IMAGE.split(";"))
    if not image_dir.exists():
        media.run(bin_dir / "sdkmanager", IMAGE)
    # A separate AVD: the other AVDs of the machine keep their settings.
    subprocess.run([str(bin_dir / "avdmanager"), "create", "avd", "-n", AVD, "-k", IMAGE, "-d", DEVICE], input="no\n", text=True, check=True, capture_output=True)


def running_serial():
    adb = tool("platform-tools", "adb")
    for line in media.run(adb, "devices").splitlines()[1:]:
        serial = line.split("\t")[0]
        if serial.startswith("emulator-") and line.endswith("device"):
            name = media.run(adb, "-s", serial, "emu", "avd", "name", check=False).split()
            if name and name[0] == AVD:
                return serial
    return None


def boot():
    serial = running_serial()
    if serial is None:
        media.WORK.mkdir(parents=True, exist_ok=True)
        print(f"Starting the emulator {AVD}, its log is in {(media.WORK / 'emulator.log').relative_to(media.ROOT)}", flush=True)
        with open(media.WORK / "emulator.log", "w") as log:
            subprocess.Popen([str(tool("emulator", "emulator")), "-avd", AVD, "-no-snapshot", "-no-audio", "-no-boot-anim", "-gpu", "host"],
                             stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        for _ in range(150):
            time.sleep(2)
            serial = running_serial()
            if serial:
                break
        else:
            sys.exit("The emulator didn't start, see build/media/emulator.log")
    adb = tool("platform-tools", "adb")
    for _ in range(150):
        if media.run(adb, "-s", serial, "shell", "getprop", "sys.boot_completed", check=False).strip() == "1":
            return serial
        time.sleep(2)
    sys.exit("The emulator didn't finish booting")


class Session:
    def __init__(self):
        ensure_avd()
        self.serial = boot()
        self.adb("root")
        time.sleep(3)
        self.adb("wait-for-device")
        if not os.environ.get("CAPSA_SKIP_BUILD"):
            subprocess.run(["./gradlew", ":samples:androidApp:assembleRelease", "-q"], cwd=media.ROOT / "kotlin", check=True)
        # The release build: the crash-free screen survives its bug there instead of crashing.
        apk = media.ROOT / "kotlin/samples/androidApp/build/outputs/apk/release/androidApp-release.apk"
        self.adb("install", "-r", apk)
        self.shell("settings put system show_touches 0")
        self.shell("cmd uimode night no")
        self.demo_mode()
        self.width, self.height = map(int, re.search(r"(\d+)x(\d+)", self.shell("wm size")).groups())
        self.touchscreen()

    def adb(self, *args, check=True):
        return media.run(tool("platform-tools", "adb"), "-s", self.serial, *args, check=check)

    def shell(self, command, check=True):
        return self.adb("shell", command, check=check)

    def demo_mode(self):
        self.shell("settings put global sysui_demo_allowed 1")
        for extras in ["command enter", "command clock -e hhmm 1200", "command battery -e level 100 -e plugged false",
                       "command network -e wifi show -e level 4", "command network -e mobile hide", "command notifications -e visible false"]:
            self.shell(f"am broadcast -a com.android.systemui.demo -e {extras}")

    def touchscreen(self):
        """Finds the first multi-touch device and the range of its coordinates."""
        devices = re.split(r"add device \d+: ", self.shell("getevent -pl"))
        found = []
        for block in devices:
            x = re.search(r"ABS_MT_POSITION_X\s*: value \d+, min \d+, max (\d+)", block)
            y = re.search(r"ABS_MT_POSITION_Y\s*: value \d+, min \d+, max (\d+)", block)
            if x and y:
                path = block.split()[0]
                found.append((int(re.search(r"(\d+)$", path).group(1)), path, int(x.group(1)), int(y.group(1))))
        if not found:
            sys.exit("The emulator has no multi-touch device")
        _, self.device, self.max_x, self.max_y = min(found)

    def tap(self, point, ms=150):
        x, y = round(point[0] * self.max_x / self.width), round(point[1] * self.max_y / self.height)
        d = self.device
        # One finger in slot 0 with tool type finger, held for ms, then lifted.
        self.shell(f"sendevent {d} 3 47 0; sendevent {d} 3 57 7; sendevent {d} 3 55 0; sendevent {d} 3 53 {x}; sendevent {d} 3 54 {y}; "
                   f"sendevent {d} 3 58 60; sendevent {d} 3 48 5; sendevent {d} 0 0 0; sleep {ms / 1000}; sendevent {d} 3 57 -1; sendevent {d} 0 0 0")

    def nodes(self):
        self.shell("uiautomator dump /sdcard/capsa-ui.xml")
        result = []
        for node in re.findall(r"<node [^>]*>", self.adb("exec-out", "cat", "/sdcard/capsa-ui.xml")):
            bounds = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
            if not bounds:
                continue
            left, top, right, bottom = map(int, bounds.groups())
            result.append({
                "text": re.search(r' text="([^"]*)"', node).group(1),
                "desc": re.search(r'content-desc="([^"]*)"', node).group(1),
                "class": re.search(r'class="([^"]*)"', node).group(1),
                "checkable": 'checkable="true"' in node,
                "center": ((left + right) // 2, (top + bottom) // 2),
                "bottom": bottom,
            })
        return result

    def texts(self):
        return [n["text"] for n in self.nodes() if n["text"]]

    def find(self, label):
        for node in self.nodes():
            if label in (node["text"], node["desc"]):
                return node["center"]
        sys.exit(f"No {label!r} on the screen")

    def start_app(self):
        """Brings the task of the app to the front, as the launcher does, or starts it."""
        self.shell(f"am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -f 0x10200000 -n {PACKAGE}/.MainActivity")

    def open(self, title):
        self.shell(f"am force-stop {PACKAGE}")
        self.start_app()
        time.sleep(2)
        for _ in range(4):
            node = next((n for n in self.nodes() if n["text"] == title), None)
            if node and node["bottom"] < self.height * 0.95:
                self.tap(node["center"])
                time.sleep(1.5)
                return
            self.shell(f"input swipe {self.width // 2} {self.height * 3 // 4} {self.width // 2} {self.height * 3 // 8} 300")
            time.sleep(0.8)
        sys.exit(f"No sample {title!r} on the home screen")

    def launcher_icon(self):
        """The icon of the app on the home screen, if the launcher shows one there."""
        self.shell("input keyevent HOME")
        time.sleep(1.5)
        icon = next((n["center"] for n in self.nodes() if "Capsa samples" in (n["text"], n["desc"])), None)
        self.start_app()
        time.sleep(1.5)
        return icon

    def record(self, example):
        scene = getattr(self, "scene_" + example.replace("-", "_"))
        for attempt in range(4):
            # Each attempt opens the sample in a fresh process, so the fake servers start over.
            taps, ok = self.take(example, scene())
            if ok:
                break
            print(f"{example}: the fake server failed on attempt {attempt + 1}, recording again", flush=True)
        media.save_taps(example, "android", taps)
        return taps

    def take(self, example, act):
        self.shell("rm -f /sdcard/capsa.mp4", check=False)
        recorder = subprocess.Popen([str(tool("platform-tools", "adb")), "-s", self.serial, "shell", "screenrecord", "--bit-rate", "12000000",
                                     "--time-limit", "40", "/sdcard/capsa.mp4"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        start = time.time()
        taps = []

        def tap(point, ms=150):
            taps.append({"t": time.time() - start + LAG, "x": point[0], "y": point[1], "ms": ms})
            self.tap(point, ms)

        time.sleep(1.2)
        try:
            ok = act(tap)
        finally:
            time.sleep(0.8)
            self.shell("pkill -INT screenrecord", check=False)
            recorder.wait()
            time.sleep(1)
            self.adb("pull", "/sdcard/capsa.mp4", media.raw(example, "android"))
        return taps, ok

    def type(self, text):
        self.shell("input text " + text.replace(" ", "%s"))

    def wait_text(self, *texts, timeout=10):
        """Waits until the screen shows one of the texts, which can be a prefix, and returns it."""
        deadline = time.time() + timeout
        while time.time() < deadline:
            shown = self.texts()
            for text in texts:
                if any(s.startswith(text) for s in shown):
                    return text
            time.sleep(0.3)
        return None

    def close(self):
        print(f"The emulator keeps running: stop it with adb -s {self.serial} emu kill", flush=True)

    # One scene per example. The method opens the sample and finds what it taps, then returns the recorded part:
    # a single action and its result, 5 to 12 seconds, which answers whether it went as planned.

    def scene_counter(self):
        self.open("Counter")
        plus, minus, reset = self.find("+1"), self.find("−1"), self.find("Reset")

        def act(tap):
            for point in [plus] * 5 + [minus] * 2:
                tap(point)
                time.sleep(0.65)
            tap(reset)
            time.sleep(1.5)
            return True
        return act

    def scene_search(self):
        self.open("Search")
        field = next(n["center"] for n in self.nodes() if n["class"] == "android.widget.EditText")

        def act(tap):
            tap(field)
            time.sleep(0.9)
            for letter in "berry":
                self.type(letter)
                time.sleep(0.22)
            time.sleep(2.5)
            ok = "Blueberry" in self.texts()
            self.shell("input keyevent BACK")
            return ok
        return act

    def scene_like(self):
        self.open("Like")
        heart = self.find("Like")
        switch = [n["center"] for n in self.nodes() if n["checkable"] and n["center"] != heart][0]

        def act(tap):
            # The server goes offline, then a like shows at once, retries, and rolls back with a snackbar.
            tap(switch)
            time.sleep(0.7)
            tap(heart)
            ok = self.wait_text("Couldn’t save") is not None
            time.sleep(0.3)
            return ok
        return act

    def scene_clock(self):
        self.open("Clock")
        icon = self.launcher_icon()

        def act(tap):
            time.sleep(2.5)
            self.shell("input keyevent HOME")
            time.sleep(3.5)
            tap(icon) if icon else self.start_app()
            time.sleep(3.5)
            return True
        return act

    def scene_profile(self):
        self.open("Profile form")
        icon = self.launcher_icon()
        name, email = [n["center"] for n in self.nodes() if n["class"] == "android.widget.EditText"][:2]

        def act(tap):
            tap(name)
            time.sleep(0.5)
            self.type("Alex")
            time.sleep(0.6)
            tap(email)
            time.sleep(0.5)
            self.type("alex@example.com")
            time.sleep(0.9)
            self.shell("input keyevent HOME")
            time.sleep(1)
            before = self.shell(f"pidof {PACKAGE}", check=False).strip()
            # What the system does to an app in the background when it needs memory.
            self.shell(f"am kill {PACKAGE}")
            time.sleep(1)
            tap(icon) if icon else self.start_app()
            time.sleep(2)
            after = self.shell(f"pidof {PACKAGE}", check=False).strip()
            return before != after and "alex@example.com" in self.texts()
        return act

    def scene_dashboard(self):
        self.open("Dashboard")
        time.sleep(2.5)
        refresh = self.find("Refresh both")

        def act(tap):
            for _ in range(3):
                tap(refresh)
                time.sleep(2.6)
            return not any(t.startswith("Couldn’t") for t in self.texts())
        return act

    def scene_crash_free(self):
        self.open("Crash-free")
        tap_button, bug = self.find("Tap"), self.find("Throw in decide")

        def act(tap):
            for point, pause in [(tap_button, 0.7), (tap_button, 0.9), (bug, 1.8), (tap_button, 0.7), (tap_button, 1.5)]:
                tap(point)
                time.sleep(pause)
            return True
        return act

    def scene_two_screens(self):
        self.open("Two screens")
        time.sleep(1)
        note, back = self.find("Buy oat milk"), self.find("Back")

        def act(tap):
            tap(note)
            time.sleep(1.5)
            tap(self.find("Star it"))
            ok = self.wait_text("Remove the star", "Couldn’t") == "Remove the star"
            time.sleep(0.7)
            tap(back)
            time.sleep(2)
            return ok
        return act
