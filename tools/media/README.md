# Demo videos

The clips in `docs/media` come from the demo app in `kotlin/samples`. One command records a sample again:

```sh
tools/media/record.sh like android
tools/media/record.sh like crash-free profile both
tools/media/record.sh all ios
```

The examples are `counter`, `search`, `like`, `clock`, `profile`, `dashboard`, `crash-free`, and `two-screens`. Each one ends up as three files:

- `<example>-<platform>.mp4`: H.264 without sound, 720 px wide, stored in Git LFS.
- `<example>-<platform>.webp`: the animated copy for the README and the wiki, 360 px wide at 15 fps.
- `<example>-<platform>.png`: the first frame, for a preview.

Commit them with `docs(media): <example> example on android and ios`.

## What the machine needs

- Python 3, `ffmpeg`, and `img2webp` from libwebp: `brew install ffmpeg webp`. Homebrew’s ffmpeg has no WebP encoder, so img2webp builds the animation.
- For Android: the Android SDK in `ANDROID_HOME` (or `~/Library/Android/sdk`) with the emulator, platform tools, and `cmdline-tools;latest`. The script creates the AVD `capsa-demo` and installs its system image with sdkmanager. It never touches your other AVDs.
- For iOS: Xcode with an iOS runtime and `xcodegen` (`brew install xcodegen`). The script creates the simulator `capsa-demo` on the newest runtime and switches it to English. Your other simulators keep their language and status bar.

## What the script does

1. It builds the release app: there, the crash-free screen survives its bug instead of crashing. `CAPSA_SKIP_BUILD=1` reuses the last build.
2. It boots the emulator or the simulator if it isn’t running and leaves it running afterwards. The status bar shows 12:00 and a full battery, and the theme is light.
3. It opens the sample in a fresh process and plays its scene: one action and its result, 5 to 12 seconds. When the fake server of the demo fails where the scene needs it to answer, the script records the scene again.
4. It turns the recording into the three files and draws a spot wherever a finger tapped.

On Android, `tools/media/android.py` taps through `sendevent` and reads the screen with uiautomator. On iOS, the XCUITest project in `tools/media/ios` plays the scenes, and `tools/media/ios.py` generates it with xcodegen and records with `simctl io recordVideo`. Recordings, frames, and generated projects stay in `build/media`.

To change a scene, edit its `scene_<example>` method in `android.py` and its `test<Example>` method in `ios/UITests/Scenes.swift`. To change where a clip starts or ends, edit `EXAMPLES` in `media.py`.

`CAPSA_AVD_IMAGE` and `CAPSA_AVD_DEVICE` pick another system image or device for a new AVD. `CAPSA_SIM_DEVICE` picks the device type of a new simulator, for example “iPhone 18 Pro”.
