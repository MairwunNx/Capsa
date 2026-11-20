"""Records a sample of the demo app and turns the recording into docs/media/<example>-<platform>.{mp4,webp,png}.

    tools/media/record.sh <example>... <android|ios|both>
    tools/media/record.sh all both

The examples are the screens of kotlin/samples: counter, search, like, clock, profile, dashboard, crash-free, two-screens.
"""
import sys

import media

PLATFORMS = ("android", "ios")


def main(argv):
    if len(argv) < 2 or argv[-1] not in PLATFORMS + ("both",):
        sys.exit(__doc__)
    examples = list(media.EXAMPLES) if argv[:-1] == ["all"] else argv[:-1]
    unknown = [e for e in examples if e not in media.EXAMPLES]
    if unknown:
        sys.exit("Unknown example: " + ", ".join(unknown) + ". Known: " + ", ".join(media.EXAMPLES))
    platforms = PLATFORMS if argv[-1] == "both" else (argv[-1],)
    media.check_tools(platforms)
    for platform in platforms:
        if platform == "android":
            import android as recorder
        else:
            import ios as recorder
        session = recorder.Session()
        for example in examples:
            taps = session.record(example)
            print(media.encode(example, platform, taps), flush=True)
        session.close()


if __name__ == "__main__":
    main(sys.argv[1:])
