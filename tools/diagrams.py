"""Draws the diagrams of Capsa in a light and a dark variant.

    python3 tools/diagrams.py                   # every diagram
    python3 tools/diagrams.py flow modules      # only these

Each diagram lands in docs/images as <name>-light.svg and <name>-dark.svg. Markdown shows the pair through <picture>
with prefers-color-scheme. Pillow measures the text, so boxes fit their labels: install it with "pip install Pillow".

The palette below is a copy of the one in tools/diagrams.py of Capability Slice Architecture, so an intent is blue
and a work is green in both repositories. Change a colour there and here together.
"""
import concurrent.futures
import html
import math
import os
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile

from PIL import Image, ImageFont

OUT = pathlib.Path(__file__).resolve().parent.parent / "docs" / "images"

# ---------- Palette ----------

# Page colours of GitHub. The background matches the page, so the picture blends in when the themes agree
# and still reads as a card when they don't.
THEMES = {
    "light": dict(bg="#ffffff", fg="#1f2328", muted="#59636e", border="#d0d7de", line="#818b98", card="#f6f8fa",
                  tint="0.08", danger="#cf222e", on_solid="#ffffff"),
    "dark": dict(bg="#0d1117", fg="#e6edf3", muted="#9198a1", border="#30363d", line="#6e7681", card="#161b22",
                 tint="0.14", danger="#f85149", on_solid="#ffffff"),
}

# One accent per family of entities, readable as text on the page of its theme.
ACCENTS = {
    "ui": dict(light="#0969da", dark="#4493f8"),          # UI, Slot, Intent
    "runtime": dict(light="#4f46e5", dark="#8b8cf8"),     # Runtime, Fact
    "capability": dict(light="#8250df", dark="#ab7df8"),  # Capability, Decision
    "slice": dict(light="#bf3989", dark="#db61a2"),       # Slice
    "projection": dict(light="#1b7c83", dark="#39c5cf"),  # Projection
    "work": dict(light="#1a7f37", dark="#3fb950"),        # Work, Signal
    "effect": dict(light="#bc4c00", dark="#f0883e"),      # Effect, Host
}
# The runtime box is filled solid. White text needs a deeper indigo than the dark accent.
RUNTIME_SOLID = dict(light="#4f46e5", dark="#5b52e0")

ENTITY = {
    "UI": "ui", "Slot": "ui", "Intent": "ui",
    "Runtime": "runtime", "Fact": "runtime",
    "Capability": "capability", "Decision": "capability",
    "Slice": "slice", "Projection": "projection",
    "Work": "work", "Signal": "work",
    "Effect": "effect", "Host": "effect",
}

SANS = "-apple-system, BlinkMacSystemFont, 'Segoe UI', 'Noto Sans', Helvetica, Arial, sans-serif"
MONO = "ui-monospace, SFMono-Regular, 'SF Mono', Menlo, Consolas, monospace"


def font(size, bold=False):
    """Helvetica on a Mac, its metric twin on Linux."""
    for path, index in (("/System/Library/Fonts/Helvetica.ttc", 1 if bold else 0),
                        ("/usr/share/fonts/truetype/liberation/LiberationSans-%s.ttf" % ("Bold" if bold else "Regular"), 0)):
        if os.path.exists(path):
            return ImageFont.truetype(path, size, index=index)
    raise SystemExit("no Helvetica or Liberation Sans to measure the text")


SLACK = 1.08  # browsers pick other fonts: leave room
_FONTS = {}


def tw(text, size, bold=False):
    key = (size, bold)
    if key not in _FONTS:
        _FONTS[key] = font(size, bold)
    return _FONTS[key].getlength(text) * SLACK


def mw(text, size):
    """Monospace text: every character takes about 0.6 em."""
    return len(text) * size * 0.6 * SLACK


def wrap(text, size, limit, bold=False):
    lines, line = [], ""
    for word in text.split():
        trial = f"{line} {word}".strip()
        if tw(trial, size, bold) <= limit or not line:
            line = trial
        else:
            lines.append(line)
            line = word
    return lines + [line]


# ---------- Drawing ----------

def keep_spaces(s):
    """SVG collapses a run of spaces and drops leading ones. No-break spaces keep them, so an indent in mono text survives."""
    return re.sub(r"^ +| {2,}", lambda m: "\xa0" * len(m.group()), s)


class Drawing:
    def __init__(self, theme, w, h, title, desc):
        self.t, self.theme, self.w, self.h, self.title, self.desc = THEMES[theme], theme, w, h, title, desc
        self.parts, self.style, self.markers = [], "", {}

    def accent(self, family):
        return ACCENTS[family][self.theme]

    def add(self, s):
        self.parts.append(s)

    def text(self, x, y, s, size=13, color=None, bold=False, anchor="start", mono=False, opacity=None):
        attrs = f'x="{x:g}" y="{y:g}" font-size="{size}" fill="{color or self.t["fg"]}"'
        if bold:
            attrs += ' font-weight="700"'
        if anchor != "start":
            attrs += f' text-anchor="{anchor}"'
        if mono:
            attrs += f' font-family="{MONO}"'
        if opacity:
            attrs += f' fill-opacity="{opacity}"'
        self.add(f"<text {attrs}>{html.escape(keep_spaces(s), quote=False)}</text>")

    def rect(self, x, y, w, h, fill="none", stroke=None, rx=10, sw=1.5, dash=None, fill_opacity=None, stroke_opacity=None):
        attrs = f'x="{x:g}" y="{y:g}" width="{w:g}" height="{h:g}" rx="{rx}" fill="{fill}"'
        if fill_opacity:
            attrs += f' fill-opacity="{fill_opacity}"'
        if stroke:
            attrs += f' stroke="{stroke}" stroke-width="{sw}"'
        if stroke_opacity:
            attrs += f' stroke-opacity="{stroke_opacity}"'
        if dash:
            attrs += f' stroke-dasharray="{dash}"'
        self.add(f"<rect {attrs}/>")

    def path(self, points, color=None, dashed=False, arrow=True, sw=1.6):
        d = " ".join(("M" if i == 0 else "L") + f"{x:g} {y:g}" for i, (x, y) in enumerate(points))
        attrs = f'd="{d}" fill="none" stroke="{color or self.t["line"]}" stroke-width="{sw}" stroke-linejoin="round"'
        if dashed:
            attrs += ' stroke-dasharray="6 5"'
        if arrow:
            attrs += f' marker-end="url(#{self.marker(color or self.t["line"])})"'
        self.add(f"<path {attrs}/>")

    def marker(self, color):
        """One arrowhead per colour, defined once in <defs>."""
        if color not in self.markers:
            self.markers[color] = f"m{len(self.markers)}"
        return self.markers[color]

    def box(self, x, y, w, h, family=None, title="", sub="", solid=False, title_size=16, dash=None):
        """A rounded box with a bold title and a muted line under it, both centred. A dashed box has no fill."""
        if dash:
            self.rect(x, y, w, h, stroke=self.t["line"], dash=dash)
            fg, muted, op = self.t["fg"], self.t["muted"], None
        elif solid:
            self.rect(x, y, w, h, fill=RUNTIME_SOLID[self.theme], stroke=RUNTIME_SOLID[self.theme])
            fg, muted, op = self.t["on_solid"], self.t["on_solid"], "0.85"
        elif family:
            accent = self.accent(family)
            self.rect(x, y, w, h, fill=accent, fill_opacity=self.t["tint"], stroke=accent, stroke_opacity="0.7")
            fg, muted, op = self.t["fg"], self.t["muted"], None
        else:
            self.rect(x, y, w, h, fill=self.t["card"], stroke=self.t["border"])
            fg, muted, op = self.t["fg"], self.t["muted"], None
        for s, size, bold in ((title, title_size, True), (sub, 13, False)):
            if s and tw(s, size, bold) + 20 > w:
                raise SystemExit(f"{self.title}: “{s}” does not fit a box {w} wide")
        cx = x + w / 2
        if sub:
            self.text(cx, y + h / 2 - 3, title, title_size, fg, bold=True, anchor="middle")
            self.text(cx, y + h / 2 + 17, sub, 13, muted, anchor="middle", opacity=op)
        else:
            self.text(cx, y + h / 2 + title_size * 0.35, title, title_size, fg, bold=True, anchor="middle")

    def step(self, cx, cy, n, label=None):
        """A numbered circle and the label to its right."""
        self.add(f'<circle cx="{cx:g}" cy="{cy:g}" r="11" fill="{RUNTIME_SOLID[self.theme]}"/>')
        self.text(cx, cy + 4.5, str(n), 12, self.t["on_solid"], bold=True, anchor="middle")
        if label:
            self.text(cx + 19, cy + 5, label, 14)

    def cross(self, cx, cy, r=6, color=None):
        c = color or self.t["danger"]
        self.add(f'<path d="M{cx - r:g} {cy - r:g} L{cx + r:g} {cy + r:g} M{cx + r:g} {cy - r:g} L{cx - r:g} {cy + r:g}" '
                 f'stroke="{c}" stroke-width="2" stroke-linecap="round"/>')

    def svg(self):
        defs = "".join(
            f'<marker id="{mid}" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">'
            f'<path d="M0 0 L10 5 L0 10z" fill="{color}"/></marker>'
            for color, mid in self.markers.items())
        head = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{self.w}" height="{self.h}" viewBox="0 0 {self.w} {self.h}" '
                f'font-family="{SANS}" role="img">\n'
                f"<title>{html.escape(self.title, quote=False)}</title>\n<desc>{html.escape(self.desc, quote=False)}</desc>\n")
        if self.style:
            head += f"<style>{self.style}</style>\n"
        if defs:
            head += f"<defs>{defs}</defs>\n"
        head += f'<rect width="{self.w}" height="{self.h}" rx="12" fill="{self.t["bg"]}"/>\n'
        return head + "\n".join(self.parts) + "\n</svg>\n"


# ---------- Diagrams ----------

def heading(d, x, y, title, note, mono_title=False):
    """A bold title and a muted note after it on one line."""
    if mono_title:
        d.text(x, y, title, 15, bold=True, mono=True)
        width = mw(title, 15)
    else:
        d.text(x, y, title, 16, bold=True)
        width = tw(title, 16, True)
    d.text(x + width + 10, y, note, 13, d.t["muted"])
    if x + width + 10 + tw(note, 13) > d.w - 16:
        raise SystemExit(f"{d.title}: the heading “{title}” is too long")


def triangle(d, x, y, color, hollow=False):
    """A start marker pointing down at y + 9."""
    fill = "none" if hollow else color
    d.add(f'<path d="M{x - 6:g} {y:g} L{x + 6:g} {y:g} L{x:g} {y + 9:g}Z" fill="{fill}" stroke="{color}" stroke-width="1.3"/>')


def dot(d, x, y, color, r=6):
    d.add(f'<circle cx="{x:g}" cy="{y:g}" r="{r}" fill="{color}"/>')


def bar(d, x0, x1, y, kind, h=14):
    """A run of a work on a lane: runs, waits, fails, or revoked."""
    t, work = d.t, d.accent("work")
    if kind == "runs":
        d.rect(x0, y, x1 - x0, h, fill=work, fill_opacity="0.35", stroke=work, rx=3, sw=1)
    elif kind == "fails":
        d.rect(x0, y, x1 - x0, h, fill=t["danger"], fill_opacity="0.18", stroke=t["danger"], rx=3, sw=1)
    else:  # waits, revoked
        d.rect(x0, y, x1 - x0, h, fill=t["line"], fill_opacity="0.18", stroke=t["line"], rx=3, sw=1, dash="3 2")


FLOW_DESC = ("1: the UI calls send with an intent, and the runtime queues it with the other facts. 2: the runtime calls decide "
             "of a capability with its slice and the fact. 3: decide returns commit with the new slice, works, and effects. "
             "4: the runtime starts a work or a stream, and it answers with a signal. 5: the runtime stores the new slice, "
             "mapState or combineState turns it into a UI model, and the UI collects it. 6: effects go to handleEffects of "
             "the host first, the rest goes to CollectEffects of the screen. The host is a CapsaViewModel or rememberCapsa.")


def flow(theme):
    d = Drawing(theme, 1060, 580, "How a fact flows through a Capsa screen", FLOW_DESC)
    t, host = d.t, d.accent("effect")
    # The host holds the runtime, the projections, and the service handlers.
    d.rect(330, 150, 350, 404, fill=host, fill_opacity="0.04", stroke=host, stroke_opacity="0.7", dash="6 5", rx=14)
    d.text(348, 540, "Host", 16, host, bold=True)
    d.text(348 + tw("Host", 16, True) + 10, 540, "CapsaViewModel or rememberCapsa { }", 13, t["muted"])

    d.box(24, 200, 180, 64, "ui", "UI", "slots render the ui")
    d.box(24, 436, 180, 60, "ui", "CollectEffects", "navigation, snackbars", title_size=15)
    d.box(366, 200, 280, 64, None, "Runtime", "queue of facts", solid=True)
    d.box(366, 322, 230, 58, "projection", "Projection", "mapState, combineState")
    d.box(366, 438, 230, 58, None, "handleEffects()", "analytics, service effects", title_size=15)
    d.box(740, 40, 296, 84, "capability", "Capability", "(Slice, Fact) → Decision", title_size=17)
    d.box(740, 200, 296, 64, "work", "Work", "work { } or stream { }")

    d.path([(204, 232), (364, 232)])                                   # 1 send
    d.path([(470, 200), (470, 70), (738, 70)], dashed=True)            # 2 decide
    d.path([(740, 104), (560, 104), (560, 198)], dashed=True)          # 3 commit
    d.path([(646, 222), (738, 222)], dashed=True)                      # 4 start
    d.path([(740, 250), (648, 250)], dashed=True)                      # Signal
    d.path([(506, 264), (506, 320)])                                   # 5 new slice
    d.path([(366, 351), (114, 351), (114, 266)], dashed=True)          # ui
    d.path([(626, 264), (626, 467), (598, 467)], dashed=True)          # 6 Effect
    d.path([(366, 467), (206, 467)], dashed=True)                      # effects

    d.step(222, 212, 1, "send(intent)")
    d.step(490, 52, 2, "decide(slice, fact)")
    d.step(580, 140, 3, "commit(work, effect)")
    d.step(694, 186, 4, "start")
    d.text(690, 276, "Signal", 14)
    d.step(526, 296, 5, "new slice")
    d.text(130, 372, "ui: StateFlow", 14)
    d.step(548, 412, 6, "Effect")
    d.text(222, 458, "the rest: effects", 13, t["muted"])
    return d


MODULES = [  # name, what it holds, x, y, w, family
    ("capsa-viewmodel", "CapsaViewModel", 40, 150, 250, "effect"),
    ("capsa-compose", "rememberCapsa, CollectEffects, Slot", 330, 150, 270, "effect"),
    ("capsa-test", "capsaHarness, replay", 640, 150, 220, None),
    ("capsa-host", "failFast, CapsaLogger, SliceSaver, CapsaEffects", 40, 290, 560, "effect"),
    ("capsa-core", "the runtime: capabilities, works, effects, projections. Only kotlinx-coroutines", 40, 420, 820, "runtime"),
]


def modules(theme):
    d = Drawing(theme, 900, 640, "The modules of Capsa and how they depend on each other",
                "In kotlin/: capsa-viewmodel and capsa-compose are the two hosts. Both depend on capsa-host, which depends "
                "on capsa-core. The hosts don’t depend on each other. capsa-test depends on capsa-core. capsa-bom aligns "
                "the versions of all five. dotnet/ and swift/ are future implementations next to kotlin/.")
    t = d.t
    d.rect(20, 20, 860, 476, fill=t["card"], fill_opacity="0.5", stroke=t["border"], rx=14)
    d.text(40, 48, "kotlin/", 15, bold=True, mono=True)
    d.text(40 + mw("kotlin/", 15) + 10, 48, "an arrow points to what a module depends on", 13, t["muted"])
    d.box(40, 66, 820, 52, None, "capsa-bom", "one version for the five modules below", title_size=15)
    for name, holds, x, y, w, family in MODULES:
        d.box(x, y, w, 72 if y < 400 else 60, family, name, holds)
    d.path([(165, 222), (165, 288)])
    d.path([(465, 222), (465, 288)])
    d.path([(320, 362), (320, 418)])
    d.path([(750, 222), (750, 418)])
    # The hosts stay apart.
    d.path([(292, 186), (328, 186)], color=t["danger"], dashed=True, arrow=False)
    d.cross(310, 186, 5)
    d.text(315, 256, "the hosts don’t depend on each other", 12, t["danger"], anchor="middle")
    # Later implementations
    d.box(20, 520, 420, 70, None, "dotnet/", "F# and Avalonia.FuncUI, later", dash="6 5")
    d.box(460, 520, 420, 70, None, "swift/", "Swift and SwiftUI, later", dash="6 5")
    d.text(450, 618, "Each implementation follows the same rules of Capability Slice Architecture.", 13, t["muted"], anchor="middle")
    return d


class Reveal:
    """Shows the parts of a timeline as a playhead passes them. CSS keyframes run inside <img> too.

    Without motion, every part stays visible and the playhead hides: the picture reads as a still timeline."""

    def __init__(self, d, x0, u, span, sweep=8.0, total=10.0):
        self.d, self.x0, self.u, self.span, self.sweep, self.total = d, x0, u, span, sweep, total
        self.n, self.css = 0, []

    def pct(self, t):
        return 100 * t / self.span * self.sweep / self.total

    def _wrap(self, start, keys, grow=False):
        name = f"r{self.n}"
        self.n += 1
        body = "".join(self.d.parts[start:])
        del self.d.parts[start:]
        style = f"animation:{name} {self.total:g}s linear infinite"
        if grow:
            style += ";transform-box:fill-box;transform-origin:0 50%"
        self.css.append(f".{name}{{{style}}}@keyframes {name}{{{keys}}}")
        self.d.add(f'<g class="anim {name}">{body}</g>')

    def show(self, t, start):
        """The parts added since start appear at time t."""
        p = self.pct(t)
        self._wrap(start, f"0%,{p:.2f}%{{opacity:0}}{p + 0.6:.2f}%,96%{{opacity:1}}100%{{opacity:0}}")

    def grow(self, t0, t1, start):
        """The parts added since start grow from the left between t0 and t1."""
        p0, p1 = self.pct(t0), self.pct(t1)
        self._wrap(start, f"0%,{p0:.2f}%{{opacity:0;transform:scaleX(0)}}{p0 + 0.01:.2f}%{{opacity:1;transform:scaleX(0)}}"
                          f"{p1:.2f}%,96%{{opacity:1;transform:scaleX(1)}}100%{{opacity:0;transform:scaleX(1)}}", grow=True)

    def finish(self, top, bottom):
        x1 = self.x0 + self.span * self.u
        p = self.pct(self.span)
        self.css.append(f".head{{animation:head {self.total:g}s linear infinite}}"
                        f"@keyframes head{{0%{{transform:translateX(0);opacity:1}}{p:.2f}%{{transform:translateX({x1 - self.x0:g}px);opacity:1}}"
                        f"{p + 2:.2f}%,100%{{transform:translateX({x1 - self.x0:g}px);opacity:0}}}}")
        self.css.append("@media (prefers-reduced-motion: reduce){.anim{animation:none}.head{display:none}}")
        self.d.add(f'<path class="head" d="M{self.x0:g} {top:g} L{self.x0:g} {bottom:g}" stroke="{self.d.accent("runtime")}" '
                   f'stroke-width="1.5" stroke-dasharray="3 3"/>')
        self.d.style = "".join(self.css)


def lanes(d, x, rows):
    """Lane titles on the left: (y, title, note)."""
    for y, title, note in rows:
        d.text(x, y + 22, title, 14, bold=True)
        d.text(x, y + 40, note, 12, d.t["muted"])


def stale_signal(theme):
    x0, u = 230, 62
    d = Drawing(theme, 900, 360, "An old answer comes after the new one, and the runtime drops it",
                "The UI sends a query “ca”, and work 1 starts. Then it sends “cat”: WorkPolicy.Latest cancels work 1 "
                "and starts work 2 under the same key. Work 2 answers first, and the slice shows the results for “cat”. "
                "The request of work 1 still runs and answers later, but the runtime drops that signal, so the slice keeps “cat”.")
    t, work, ui, sl = d.t, d.accent("work"), d.accent("ui"), d.accent("slice")
    X = lambda v: x0 + v * u
    heading(d, 20, 36, "WorkPolicy.Latest", "a new query cancels the old work, and its late answer never reaches the slice")
    lanes(d, 20, [(72, "UI", "send(QueryChanged)"), (128, "work 1", "key SEARCH, “ca”"),
                  (184, "work 2", "key SEARCH, “cat”"), (240, "slice", "SearchSlice")])
    for y in (128, 184, 240):
        d.add(f'<path d="M{x0 - 10} {y:g} L{X(10.6):g} {y:g}" stroke="{t["border"]}" stroke-width="1"/>')
    r = Reveal(d, x0, u, 10.6)

    for v, q in ((0.5, "“ca”"), (2.5, "“cat”")):
        s = len(d.parts)
        triangle(d, X(v), 88, ui)
        d.text(X(v) + 10, 97, q, 13, ui, bold=True)
        r.show(v, s)
    # work 1: runs, gets cancelled, and its request answers anyway
    s = len(d.parts); bar(d, X(0.5), X(2.5), 148, "runs"); r.grow(0.5, 2.5, s)
    s = len(d.parts); bar(d, X(2.5), X(7), 148, "revoked"); r.grow(2.5, 7, s)
    s = len(d.parts)
    d.cross(X(2.5), 155, 5)
    d.text(X(2.5) + 10, 177, "cancelled, but the request still runs", 12, t["muted"])
    r.show(2.5, s)
    s = len(d.parts)
    dot(d, X(7) + 10, 155, t["line"])
    d.path([(X(7) + 10, 162), (X(7) + 10, 236)], color=t["line"], dashed=True, arrow=False, sw=1.4)
    d.cross(X(7) + 10, 232, 6)
    d.text(X(7) + 24, 160, "late answer: dropped", 13, t["danger"], bold=True)
    r.show(7, s)
    # work 2: answers first
    s = len(d.parts); bar(d, X(2.5), X(5), 204, "runs"); r.grow(2.5, 5, s)
    s = len(d.parts)
    dot(d, X(5) + 10, 211, work)
    d.text(X(5) + 22, 216, "ResultsLoaded", 13, work, bold=True)
    d.path([(X(5) + 10, 218), (X(5) + 10, 256)], color=work, arrow=True, sw=1.4)
    r.show(5, s)
    # slice
    s = len(d.parts)
    d.rect(X(0.5), 258, X(5) - X(0.5), 22, fill=t["line"], fill_opacity="0.15", stroke=t["line"], rx=11, sw=1)
    d.text(X(0.5) + 12, 273, "loading", 12, t["muted"])
    r.grow(0.5, 5, s)
    s = len(d.parts)
    d.rect(X(5) + 2, 258, X(10.6) - X(5) - 2, 22, fill=sl, fill_opacity=t["tint"], stroke=sl, stroke_opacity="0.7", rx=11, sw=1)
    d.text(X(5) + 24, 273, "results for “cat”", 12, sl, bold=True)
    r.show(5, s)
    s = len(d.parts)
    d.text(X(7) + 24, 273, "still “cat”", 12, t["muted"])
    r.show(7.2, s)

    d.path([(x0, 316), (X(10.6), 316)], color=t["line"], sw=1.2)
    d.text(X(10.6), 338, "time", 13, t["muted"], anchor="end")
    d.text(20, 338, "Harness tests see it as WorkEvent.Dropped.", 12, t["muted"])
    r.finish(64, 300)
    return d


def debounce_retry(theme):
    w, lx = 900, 20
    d = Drawing(theme, w, 444, "Debounce and retry of a work on a timeline",
                "Debounce: the user types c, ca, and cat. Each keystroke restarts the wait of 300 ms, and the request goes out "
                "once, 300 ms after the last keystroke, and answers with a signal. Retry: the first attempt fails, the runtime "
                "waits 1 s, the second attempt fails, the runtime waits 2 s, and the third attempt succeeds. onError receives "
                "the error only after the last failed attempt.")
    t, work = d.t, d.accent("work")
    # legend
    lgx = 40
    triangle(d, lgx + 6, 22, t["fg"])
    d.text(lgx + 18, 33, "start", 13, t["muted"])
    lgx += 70
    bar(d, lgx, lgx + 28, 22, "waits", h=12)
    d.text(lgx + 36, 33, "waits", 13, t["muted"])
    lgx += 100
    bar(d, lgx, lgx + 28, 22, "runs", h=12)
    d.text(lgx + 36, 33, "work runs", 13, t["muted"])
    lgx += 126
    bar(d, lgx, lgx + 28, 22, "fails", h=12)
    d.cross(lgx + 28, 28, 5)
    d.text(lgx + 42, 33, "fails", 13, t["muted"])
    lgx += 96
    d.cross(lgx + 6, 28, 5)
    d.text(lgx + 18, 33, "cancelled", 13, t["muted"])
    lgx += 104
    dot(d, lgx + 6, 28, work)
    d.text(lgx + 18, 33, "signal", 13, t["muted"])

    # Debounce, in milliseconds
    y = 56
    d.rect(lx, y, w - 2 * lx, 176, fill=t["card"], stroke=t["border"], rx=8, sw=1)
    heading(d, lx + 20, y + 30, "debounce", "work(SEARCH, debounce = 300.milliseconds) { … }")
    ms = lambda v: 60 + v * 0.72
    for v, q in ((0, "“c”"), (150, "“ca”"), (280, "“cat”")):
        triangle(d, ms(v), y + 50, t["fg"])
        d.text(ms(v) + 10, y + 59, q, 12, t["muted"])
    by = y + 72
    bar(d, ms(0), ms(150), by, "waits")
    d.cross(ms(150), by + 7, 5)
    bar(d, ms(150), ms(280), by, "waits")
    d.cross(ms(280), by + 7, 5)
    bar(d, ms(280), ms(580), by, "waits")
    d.text((ms(280) + ms(580)) / 2, by + 32, "300 ms of quiet", 12, t["muted"], anchor="middle")
    bar(d, ms(580), ms(960), by, "runs")
    d.text((ms(580) + ms(960)) / 2, by + 32, "one request", 12, t["muted"], anchor="middle")
    dot(d, ms(960) + 10, by + 7, work)
    d.text(ms(960) + 22, by + 12, "ResultsLoaded", 13, work, bold=True)
    d.path([(ms(0), y + 132), (ms(1100), y + 132)], color=t["line"], sw=1.2)
    for v in range(0, 1101, 100):
        d.add(f'<path d="M{ms(v):g} {y + 129} L{ms(v):g} {y + 135}" stroke="{t["line"]}" stroke-width="1"/>')
    d.text(ms(1100), y + 152, "ms", 12, t["muted"], anchor="end")
    d.text(lx + 20, y + 162, "Each keystroke cancels the wait and starts it again. The request goes out once.", 13, t["muted"])

    # Retry, in seconds
    y = 252
    d.rect(lx, y, w - 2 * lx, 176, fill=t["card"], stroke=t["border"], rx=8, sw=1)
    heading(d, lx + 20, y + 30, "retry", "work(SEND, retry = Retry(times = 3, delay = 1.seconds, factor = 2.0)) { … }")
    sec = lambda v: 60 + v * 130
    triangle(d, sec(0), y + 50, t["fg"])
    by = y + 72
    for a, b, label in ((0, 0.7, "attempt 1"), (1.7, 2.4, "retry 1")):
        bar(d, sec(a), sec(b), by, "fails")
        d.cross(sec(b), by + 7, 5)
        d.text((sec(a) + sec(b)) / 2, by + 32, label, 12, t["muted"], anchor="middle")
    for a, b, label in ((0.7, 1.7, "1 s"), (2.4, 4.4, "2 s")):
        d.add(f'<path d="M{sec(a) + 4:g} {by + 7} L{sec(b) - 4:g} {by + 7}" stroke="{t["line"]}" stroke-width="1.2" stroke-dasharray="2 3"/>')
        d.text((sec(a) + sec(b)) / 2, by - 4, label, 12, t["muted"], anchor="middle")
    bar(d, sec(4.4), sec(5.1), by, "runs")
    d.text((sec(4.4) + sec(5.1)) / 2, by + 32, "retry 2", 12, t["muted"], anchor="middle")
    dot(d, sec(5.1) + 10, by + 7, work)
    d.text(sec(5.1) + 22, by + 12, "Sent", 13, work, bold=True)
    d.path([(sec(0), y + 132), (sec(6.1), y + 132)], color=t["line"], sw=1.2)
    for v in range(0, 7):
        d.add(f'<path d="M{sec(v):g} {y + 129} L{sec(v):g} {y + 135}" stroke="{t["line"]}" stroke-width="1"/>')
    d.text(sec(6.1), y + 152, "s", 12, t["muted"], anchor="end")
    d.text(lx + 20, y + 162, "The pause doubles: 1 s, then 2 s. onError gets the error only when the last retry fails too.", 13, t["muted"])
    return d


def while_visible(theme):
    x0, u = 230, 62
    d = Drawing(theme, 900, 372, "A stream with whileVisible pauses while the screen is hidden",
                "The screen is visible, the timer stream ticks, and the slice counts down. A rotation brings the screen back "
                "within the grace of one second, so the stream keeps running. The app goes to the background: the runtime "
                "cancels the stream, and no work runs. The app comes back: the runtime calls the block again, and the stream "
                "starts from the current value.")
    t, work, ui, sl = d.t, d.accent("work"), d.accent("ui"), d.accent("slice")
    X = lambda v: x0 + v * u
    heading(d, 20, 36, "stream(TICK, whileVisible = true)", "the runtime pauses the stream while nobody sees the screen", mono_title=True)
    lanes(d, 20, [(64, "screen", "collected, in the foreground"), (124, "stream", "timer ticks"), (184, "slice", "seconds left")])
    for y in (124, 184):
        d.add(f'<path d="M{x0 - 10} {y:g} L{X(10.4):g} {y:g}" stroke="{t["border"]}" stroke-width="1"/>')
    # screen
    py = 84
    for a, b in ((0, 4.2), (7, 10.4)):
        d.rect(X(a), py, X(b) - X(a) - 2, 24, fill=ui, fill_opacity=t["tint"], stroke=ui, stroke_opacity="0.7", rx=12, sw=1)
        d.text(X(a) + 14, py + 16, "visible", 12, ui, bold=True)
    d.rect(X(4.2) + 2, py, X(7) - X(4.2) - 4, 24, fill=t["line"], fill_opacity="0.12", stroke=t["line"], rx=12, sw=1, dash="3 2")
    d.text((X(4.2) + X(7)) / 2, py + 16, "hidden: in the background", 12, t["muted"], anchor="middle")
    d.add(f'<path d="M{X(2):g} {py - 4} L{X(2):g} {py + 28}" stroke="{t["fg"]}" stroke-width="1.2" stroke-dasharray="2 2"/>')
    d.text(X(2) + 8, py - 6, "rotation: back within the 1 s grace", 12, t["muted"])
    # stream
    by = 144
    bar(d, X(0), X(4.2), by, "runs")
    d.cross(X(4.2), by + 7, 5)
    d.text((X(4.2) + X(7)) / 2, by + 11, "cancelled, no work runs", 12, t["muted"], anchor="middle")
    triangle(d, X(7), by - 12, t["fg"])
    d.text(X(7) + 10, by - 4, "block() again", 12, t["muted"])
    bar(d, X(7), X(10.4), by, "runs")
    for v in [i * 0.6 + 0.3 for i in range(7)] + [7.3 + i * 0.6 for i in range(6)]:
        dot(d, X(v), by + 7, work, r=3)
    d.text(X(0.3), by + 32, "each tick is a signal", 12, t["muted"])
    # slice
    sy = 204
    d.rect(X(0), sy, X(10.4) - X(0), 24, fill=sl, fill_opacity=t["tint"], stroke=sl, stroke_opacity="0.7", rx=12, sw=1)
    for v, label, color in ((0.3, "04:59", sl), (3.3, "04:53", sl), (5.0, "kept: 04:53", t["muted"]), (7.3, "04:47", sl), (9.6, "04:41", sl)):
        d.text(X(v), sy + 16, label, 12, color, bold=color == sl, mono=True)
    d.path([(x0, 254), (X(10.4), 254)], color=t["line"], sw=1.2)
    d.text(X(10.4), 276, "time", 13, t["muted"], anchor="end")
    d.text(20, 308, "The runtime counts the screen visible while someone collects a slice, a projection, or effects.flow,", 13, t["muted"])
    d.text(20, 328, "and the app is in the foreground. After the last collector leaves, it waits one second,", 13, t["muted"])
    d.text(20, 348, "so a rotation doesn’t restart the stream. On return the timer computes the time left from its deadline.", 13, t["muted"])
    return d


HISTORY = [
    "DecideFailed: like failed on Intent LikeClicked(id=7)",
    "Capsa history, newest last:",
    "  Signal Started -> search, like",
    "  Intent QueryChanged(query=cat) -> search",
    "  search: start work \"search\"",
    "  Signal ResultsLoaded(count=12) -> search",
    "  Intent LikeClicked(id=7) -> like",
]


def crash_free(theme):
    d = Drawing(theme, 960, 470, "A bug in decide: debug crashes with the history, release logs it and the screen lives",
                "The like capability throws in decide on LikeClicked. In a debug build Capsa.failFast is true: Capsa throws "
                "CapsaViolationException, whose message lists the violation and the last events of the runtime, and the app "
                "crashes. In a release build Capsa.failFast is false: Capsa hands the error to CapsaLogger, which can send it "
                "to a crash reporter, and the screen keeps its last valid slice and goes on with the next facts.")
    t, cap, ui = d.t, d.accent("capability"), d.accent("ui")
    d.box(330, 20, 300, 70, "capability", "Capability like", "decide throws on LikeClicked", title_size=16)
    d.path([(440, 90), (440, 112), (245, 112), (245, 132)], color=t["danger"])
    d.path([(520, 90), (520, 112), (715, 112), (715, 132)], color=t["line"])
    # Debug
    d.text(20, 158, "Debug", 16, bold=True)
    d.text(20 + tw("Debug", 16, True) + 10, 158, "Capsa.failFast = true", 13, t["muted"], mono=True)
    d.rect(20, 172, 450, 238, fill=t["danger"], fill_opacity="0.06", stroke=t["danger"], stroke_opacity="0.7")
    d.text(38, 200, "CapsaViolationException", 16, t["danger"], bold=True)
    for i, line in enumerate(HISTORY):
        if mw(line, 12) > 414:
            raise SystemExit(f"crash-free: “{line}” is too long")
        d.text(38, 228 + i * 21, line, 12, t["fg"] if i == 0 else t["muted"], mono=True)
    d.text(38, 394, "The app crashes. The report shows how the screen got there.", 13, t["fg"])
    # Release
    d.text(490, 158, "Release", 16, bold=True)
    d.text(490 + tw("Release", 16, True) + 10, 158, "Capsa.failFast = false", 13, t["muted"], mono=True)
    d.box(490, 172, 450, 64, "effect", "CapsaLogger", "your logger sends the error to a crash reporter")
    d.path([(715, 236), (715, 266)])
    d.box(490, 268, 450, 64, "ui", "The screen lives", "the slice stays as it was before the fact")
    d.path([(715, 332), (715, 362)])
    d.box(490, 364, 450, 46, None, "The next facts run as usual", title_size=14)
    d.text(20, 446, "Capsa detects the build: the debuggable flag on Android, Platform.isDebugBinary on iOS and macOS.", 13, t["muted"])
    return d


def testing(theme):
    d = Drawing(theme, 960, 470, "capsaHarness: facts in, slices and work events out",
                "A test appends facts to capsaHarness, a real runtime on virtual time, and moves the clock with runCurrent "
                "and advanceTimeBy. It reads the slice of a capability with stateOf, the effects with takeEffects, the works "
                "with workEvents: Started, Cancelled, Failed, Dropped, and the violations. replay feeds facts straight into "
                "decide and starts no works.")
    t = d.t
    heading(d, 20, 36, "capsaHarness(search, results)", "a real runtime on virtual time in a runTest", mono_title=True)
    ins = [("runtime", "append(fact)", "queues an intent or a signal"),
           (None, "runCurrent()", "runs what is due now"),
           (None, "advanceTimeBy(d)", "moves the virtual clock")]
    for i, (family, title, sub) in enumerate(ins):
        y = 70 + i * 82
        d.box(20, y, 250, 64, family, title, sub, title_size=15)
        d.path([(270, y + 32), (336, 172 + (i - 1) * 30)])
    d.box(338, 112, 264, 120, None, "capsaHarness", "a real runtime", solid=True, title_size=17)
    outs = [("slice", "stateOf(capability)", "the slice now"),
            ("effect", "takeEffects()", "the effects since the last take"),
            ("work", "workEvents", "Started, Cancelled, Failed, Dropped"),
            (None, "violations", "with strict = false")]
    for i, (family, title, sub) in enumerate(outs):
        y = 56 + i * 64
        d.box(690, y, 250, 54, family, title, sub, title_size=15)
        d.path([(602, 160 + (i - 1.5) * 20), (688, y + 27)])
    # replay
    y = 336
    d.add(f'<path d="M20 {y - 14} L940 {y - 14}" stroke="{t["border"]}" stroke-width="1"/>')
    heading(d, 20, y + 14, "capability.replay(facts)", "no runtime: facts go straight into decide", mono_title=True)
    d.box(20, y + 34, 250, 60, "runtime", "LikeClicked, LikeSaved", "facts in order", title_size=15)
    d.box(338, y + 34, 264, 60, "capability", "decide", "of one capability", title_size=16)
    d.box(690, y + 34, 250, 60, "slice", "state, states", "works, effects, signals", title_size=15)
    d.path([(270, y + 64), (336, y + 64)])
    d.path([(602, y + 64), (688, y + 64)])
    d.text(20, y + 122, "replay starts no works: it shows what decide returned. The harness runs them.", 13, t["muted"])
    return d


INSTALL = [  # title, who, commands, note
    ("Plugin", "Claude Code", ["claude plugin marketplace add MairwunNx/Capsa", "claude plugin install capsa@capsa"], "then /capsa:capsa-setup"),
    ("npx skills", "harnesses with skills", ["npx skills add MairwunNx/Capsa"], "then call capsa-setup in your harness"),
    ("Manual copy", "any harness", ["cp -R plugins/capsa/skills/* .claude/skills/"], "from a clone, or into the skills folder your harness reads"),
]
SKILLS = ["capsa", "capsa-setup", "capsa-migrate", "capsa-new", "capsa-review"]


def install(theme):
    d = Drawing(theme, 900, 488, "Three ways to install the skills of Capsa",
                "1: the Claude Code plugin, claude plugin marketplace add MairwunNx/Capsa and claude plugin install capsa@capsa. "
                "2: npx skills add MairwunNx/Capsa. 3: copy the folders of plugins/capsa/skills into the skills folder of your "
                "harness. Each way brings the same five skills: capsa, capsa-setup, capsa-migrate, capsa-new, and capsa-review.")
    t = d.t
    rail = 860
    for i, (title, who, commands, note) in enumerate(INSTALL):
        y, h = 20 + i * 104, 92
        d.rect(20, y, 800, h, fill=t["card"], stroke=t["border"], rx=8, sw=1)
        d.add(f'<circle cx="54" cy="{y + h / 2:g}" r="16" fill="{RUNTIME_SOLID[theme]}"/>')
        d.text(54, y + h / 2 + 6, str(i + 1), 16, t["on_solid"], bold=True, anchor="middle")
        d.text(86, y + 38, title, 16, bold=True)
        d.text(86, y + 60, who, 13, t["muted"])
        cy = y + 32 if len(commands) > 1 else y + 44
        for j, cmd in enumerate(commands):
            if 290 + mw(cmd, 13) > 806:
                raise SystemExit(f"install: “{cmd}” is too long")
            d.text(290, cy + j * 22, cmd, 13, mono=True)
        d.text(290, y + h - 12, note, 12, t["muted"])
        d.path([(820, y + h / 2), (rail, y + h / 2)], arrow=False)
    d.path([(rail, 64), (rail, 336)])
    # the skills
    y = 338
    d.rect(20, y, 860, 130, fill=t["card"], stroke=t["border"], rx=8, sw=1)
    d.text(40, y + 34, "Five skills", 16, bold=True)
    d.text(40 + tw("Five skills", 16, True) + 10, y + 34, "the same in every harness", 13, t["muted"])
    x = 40
    for name in SKILLS:
        cw = mw(name, 13) + 28
        cap = d.accent("capability")
        d.rect(x, y + 52, cw, 30, fill=cap, fill_opacity=t["tint"], stroke=cap, stroke_opacity="0.7", rx=15, sw=1)
        d.text(x + cw / 2, y + 72, name, 13, cap, bold=True, anchor="middle", mono=True)
        x += cw + 12
    d.text(40, y + 110, "The agent loads capsa on its own. Start with capsa-setup, then capsa-migrate or capsa-new.", 13, t["muted"])
    return d


DIAGRAMS = {
    "flow": flow,
    "modules": modules,
    "stale-signal": stale_signal,
    "debounce-retry": debounce_retry,
    "while-visible": while_visible,
    "crash-free": crash_free,
    "testing": testing,
    "install": install,
}


def main(names):
    unknown = [n for n in names if n not in DIAGRAMS]
    if unknown:
        raise SystemExit(f"unknown diagrams: {', '.join(unknown)}. Known: {', '.join(DIAGRAMS)}")
    OUT.mkdir(parents=True, exist_ok=True)
    for name in names or DIAGRAMS:
        for theme in THEMES:
            path = OUT / f"{name}-{theme}.svg"
            path.write_text(DIAGRAMS[name](theme).svg(), encoding="utf-8")
            print(path.relative_to(OUT.parent.parent))


# ---------- Animations for KDoc ----------
#
#     python3 tools/diagrams.py --kdoc                  # every animation
#     python3 tools/diagrams.py --kdoc retry debounce   # only these
#
# Each animation lands in docs/kdoc as <name>.gif, and KDoc links it from master on raw.githubusercontent.com.
# One light card with a border on a transparent page reads in a light and a dark IDE. Headless Chrome draws the frames:
# a copy of the animated SVG pauses every animation at a negative delay, and the browser shows that moment.
# --virtual-time-budget doesn't move CSS animations. Set CHROME when Chrome lives elsewhere.

KDOC_OUT = OUT.parent / "kdoc"
KDOC = {}  # name -> (draw, seconds of motion, seconds to hold the last frame)
FPS = 12


def kdoc(name, seconds, hold=1.0):
    """Registers an animation: draw(seconds) returns a Card whose motion takes that long."""
    def register(draw):
        KDOC[name] = (draw, seconds, hold)
        return draw
    return register


class Card(Drawing):
    """A light card with a border. The page around it stays transparent."""

    def __init__(self, w, h, title, desc):
        super().__init__("light", w, h, title, desc)

    def svg(self):
        bg = f'<rect width="{self.w}" height="{self.h}" rx="12" fill="{self.t["bg"]}"/>'
        card = (f'<rect x="0.5" y="0.5" width="{self.w - 1}" height="{self.h - 1}" rx="12" fill="{self.t["bg"]}" '
                f'stroke="{self.t["border"]}"/>')
        return super().svg().replace(bg, card, 1)


def steps(d, seconds):
    """A Reveal for a sequence without a timeline: its time is in seconds, and settle() adds no playhead."""
    return Reveal(d, 0, 1, seconds, sweep=seconds, total=seconds + 4)


def settle(r):
    r.css.append("@media (prefers-reduced-motion: reduce){.anim{animation:none}}")
    r.d.style = "".join(r.css)


def between(r, t0, t1, start):
    """The parts added since start show from t0 until t1, then give way to what replaces them."""
    p0, p1 = r.pct(t0), r.pct(t1)
    r._wrap(start, f"0%,{p0:.2f}%{{opacity:0}}{p0 + 0.01:.2f}%,{p1:.2f}%{{opacity:1}}{p1 + 0.01:.2f}%,100%{{opacity:0}}")


def chrome():
    for path in (os.environ.get("CHROME"), "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
                 shutil.which("google-chrome"), shutil.which("chromium")):
        if path and os.path.exists(path):
            return path
    raise SystemExit("no Chrome to draw the frames: set CHROME to its binary")


def shoot(svg, at, w, h, folder):
    """Draws the SVG at second at into folder/frame.png."""
    folder.mkdir()
    page = folder / "frame.svg"
    page.write_text(svg.replace("</style>", f"*{{animation-play-state:paused!important;animation-delay:-{at:.3f}s!important}}</style>", 1),
                    encoding="utf-8")
    png = folder / "frame.png"
    subprocess.run([chrome(), "--headless=new", "--disable-gpu", "--hide-scrollbars", "--force-device-scale-factor=1",
                    f"--user-data-dir={folder / 'profile'}", f"--window-size={w},{h}", "--default-background-color=00000000",
                    f"--screenshot={png}", page.as_uri()], check=True, capture_output=True, timeout=60)
    return png


def to_gif(pngs):
    """Quantizes the frames to one palette, so a frame stores only what changed since the previous one.

    Half-transparent pixels of the rounded corners become either the card or the page: GIF has no partial alpha."""
    images = [Image.open(p).convert("RGBA") for p in pngs]
    w, h = images[0].size
    sample = Image.new("RGB", (w, h * 2), "white")
    sample.paste(images[len(images) // 2].convert("RGB"), (0, 0))
    sample.paste(images[-1].convert("RGB"), (0, h))
    palette = sample.quantize(255, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
    frames = []
    for im in images:
        frame = im.convert("RGB").quantize(palette=palette, dither=Image.Dither.NONE)
        frame.paste(255, mask=im.getchannel("A").point(lambda a: 255 if a < 128 else 0))
        frames.append(frame)
    return frames


def animate(name):
    draw, seconds, hold = KDOC[name]
    d = draw(seconds)
    svg = d.svg()
    times = [i / FPS for i in range(int(seconds * FPS))] + [seconds + 0.3]
    with tempfile.TemporaryDirectory() as tmp, concurrent.futures.ThreadPoolExecutor(8) as pool:
        pngs = list(pool.map(lambda it: shoot(svg, it[1], d.w, d.h, pathlib.Path(tmp) / str(it[0])), enumerate(times)))
        frames = to_gif(pngs)
    KDOC_OUT.mkdir(parents=True, exist_ok=True)
    out = KDOC_OUT / f"{name}.gif"
    durations = [round(1000 / FPS)] * (len(frames) - 1) + [round(hold * 1000)]
    frames[0].save(out, save_all=True, append_images=frames[1:], duration=durations, loop=0, transparency=255, disposal=1, optimize=True)
    print(f"{out.relative_to(OUT.parent.parent)}: {len(frames)} frames, {out.stat().st_size // 1024} KB")


def kdoc_main(names):
    unknown = [n for n in names if n not in KDOC]
    if unknown:
        raise SystemExit(f"unknown animations: {', '.join(unknown)}. Known: {', '.join(KDOC)}")
    for name in names or KDOC:
        animate(name)


@kdoc("work-policies", 3.0)
def kdoc_work_policies(seconds):
    x0, u = 170, 58
    d = Card(600, 336, "What each WorkPolicy does when a work starts again under the same key",
             "Run 1 starts, and run 2 starts later under the same key. Latest cancels run 1 and lets run 2 answer. "
             "KeepExisting ignores run 2 while run 1 goes on, and run 1 answers. Parallel runs both, and both answer.")
    t, work = d.t, d.accent("work")
    X = lambda v: x0 + v * u
    heading(d, 20, 32, "WorkPolicy", "what a second start under the same key does")
    lanes(d, 20, [(46, "work(KEY)", "starts twice")])
    r = Reveal(d, x0, u, 7, sweep=seconds, total=seconds + 4)
    for v, label in ((0.5, "run 1"), (3, "run 2")):
        s = len(d.parts)
        triangle(d, X(v), 62, t["fg"])
        d.text(X(v) + 10, 71, label, 13, bold=True)
        r.show(v, s)
    policies = [("Latest", "cancels run 1", [(0.5, 3, True, False), (3, 6, False, True)]),
                ("KeepExisting", "ignores run 2", [(0.5, 5.5, False, True), None]),
                ("Parallel", "runs both", [(0.5, 5.5, False, True), (3, 6, False, True)])]
    for i, (title, note, runs) in enumerate(policies):
        y = 92 + i * 72
        d.add(f'<path d="M20 {y - 4:g} L580 {y - 4:g}" stroke="{t["border"]}" stroke-width="1"/>')
        lanes(d, 20, [(y, title, note)])
        for j, run in enumerate(runs):
            by = y + 12 + j * 26
            if run is None:
                s = len(d.parts)
                triangle(d, X(3), by - 2, t["line"], hollow=True)
                d.text(X(3) + 12, by + 8, "ignored: run 1 still goes", 12, t["muted"])
                r.show(3, s)
                continue
            a, b, cancelled, answers = run
            s = len(d.parts)
            bar(d, X(a), X(b), by, "runs")
            r.grow(a, b, s)
            s = len(d.parts)
            if cancelled:
                d.cross(X(b), by + 7, 5)
                d.text(X(b) + 10, by + 12, "cancelled", 12, t["muted"])
            if answers:
                dot(d, X(b) + 9, by + 7, work, r=5)
            r.show(b, s)
    d.path([(x0, 308), (X(7), 308)], color=t["line"], sw=1.2)
    d.text(X(7), 326, "time", 12, t["muted"], anchor="end")
    d.text(20, 326, "A green dot is the signal of a run.", 12, t["muted"])
    r.finish(50, 300)
    return d


@kdoc("stale-signal", 3.0)
def kdoc_stale_signal(seconds):
    x0, u = 150, 44
    d = Card(600, 300, "WorkPolicy.Latest drops the late answer of a cancelled run",
             "The UI sends “ca”, and work 1 starts. Then it sends “cat”: WorkPolicy.Latest cancels work 1 and starts work 2 "
             "under the same key. Work 2 answers, and the slice shows the results for “cat”. The request of work 1 answers "
             "later, but the runtime drops that signal, so the slice keeps “cat”.")
    t, work, ui, sl = d.t, d.accent("work"), d.accent("ui"), d.accent("slice")
    X = lambda v: x0 + v * u
    heading(d, 20, 32, "WorkPolicy.Latest", "a cancelled run never overwrites a newer answer")
    lanes(d, 20, [(46, "UI", "send(QueryChanged)"), (102, "work 1", "SEARCH, “ca”"), (158, "work 2", "SEARCH, “cat”"),
                  (214, "slice", "SearchSlice")])
    for y in (102, 158, 214):
        d.add(f'<path d="M{x0 - 10} {y:g} L{X(9.6):g} {y:g}" stroke="{t["border"]}" stroke-width="1"/>')
    r = Reveal(d, x0, u, 9.6, sweep=seconds, total=seconds + 4)
    for v, q in ((0.5, "“ca”"), (2.5, "“cat”")):
        s = len(d.parts)
        triangle(d, X(v), 62, ui)
        d.text(X(v) + 10, 71, q, 13, ui, bold=True)
        r.show(v, s)
    late = 7.2
    s = len(d.parts); bar(d, X(0.5), X(2.5), 122, "runs"); r.grow(0.5, 2.5, s)
    s = len(d.parts); bar(d, X(2.5), X(late), 122, "revoked"); r.grow(2.5, late, s)
    s = len(d.parts)
    d.cross(X(2.5), 129, 5)
    d.text(X(2.5) + 10, 151, "cancelled, but the request still runs", 12, t["muted"])
    r.show(2.5, s)
    s = len(d.parts); bar(d, X(2.5), X(4.5), 178, "runs"); r.grow(2.5, 4.5, s)
    s = len(d.parts)
    dot(d, X(4.5) + 9, 185, work, r=5)
    d.text(X(4.5) + 20, 190, "ResultsLoaded", 13, work, bold=True)
    d.path([(X(4.5) + 9, 191), (X(4.5) + 9, 230)], color=work, sw=1.4)
    r.show(4.5, s)
    s = len(d.parts)
    d.rect(X(0.5), 232, X(4.5) - X(0.5), 22, fill=t["line"], fill_opacity="0.15", stroke=t["line"], rx=11, sw=1)
    d.text(X(0.5) + 10, 247, "loading", 12, t["muted"])
    r.grow(0.5, 4.5, s)
    s = len(d.parts)
    d.rect(X(4.5) + 2, 232, X(9.6) - X(4.5) - 2, 22, fill=sl, fill_opacity=t["tint"], stroke=sl, stroke_opacity="0.7", rx=11, sw=1)
    d.text(X(4.5) + 22, 247, "results for “cat”", 12, sl, bold=True)
    r.show(4.5, s)
    s = len(d.parts)
    dot(d, X(late) + 8, 129, t["line"], r=5)
    d.path([(X(late) + 8, 135), (X(late) + 8, 232)], color=t["line"], dashed=True, arrow=False, sw=1.4)
    d.cross(X(late) + 8, 228, 6)
    d.text(X(late) + 20, 134, "dropped", 13, t["danger"], bold=True)
    d.text(X(late) + 20, 247, "still “cat”", 12, t["muted"])
    r.show(late, s)
    d.path([(x0, 274), (X(9.6), 274)], color=t["line"], sw=1.2)
    d.text(X(9.6), 292, "time", 12, t["muted"], anchor="end")
    d.text(20, 292, "capsaHarness reports it as WorkEvent.Dropped.", 12, t["muted"])
    r.finish(46, 264)
    return d


@kdoc("debounce", 2.6)
def kdoc_debounce(seconds):
    x0, u = 150, 42
    d = Card(600, 240, "A work with debounce sends one request after the user stops typing",
             "The user types c, ca, and cat. Each keystroke starts the work again: WorkPolicy.Latest cancels the wait of the "
             "previous run. 300 ms after the last keystroke the request goes out once and answers with a signal.")
    t, work, ui = d.t, d.accent("work"), d.accent("ui")
    X = lambda v: x0 + v * u  # one unit is 100 ms
    d.text(20, 32, "work(SEARCH, debounce = 300.milliseconds)", 14, bold=True, mono=True)
    d.text(20, 52, "each keystroke restarts the wait, the request leaves once", 13, t["muted"])
    lanes(d, 20, [(64, "typing", "send(QueryChanged)"), (114, "work", "SEARCH")])
    r = Reveal(d, x0, u, 10, sweep=seconds, total=seconds + 4)
    for v, q in ((0, "“c”"), (1.4, "“ca”"), (2.8, "“cat”")):
        s = len(d.parts)
        triangle(d, X(v), 80, ui)
        d.text(X(v) + 9, 89, q, 13, ui, bold=True)
        r.show(v, s)
    by = 136
    for a, b in ((0, 1.4), (1.4, 2.8)):
        s = len(d.parts); bar(d, X(a), X(b), by, "waits"); r.grow(a, b, s)
        s = len(d.parts); d.cross(X(b), by + 7, 5); r.show(b, s)
    s = len(d.parts); bar(d, X(2.8), X(5.8), by, "waits"); r.grow(2.8, 5.8, s)
    s = len(d.parts)
    d.text((X(2.8) + X(5.8)) / 2, by + 30, "300 ms of quiet", 12, t["muted"], anchor="middle")
    r.show(5.8, s)
    s = len(d.parts); bar(d, X(5.8), X(8.6), by, "runs"); r.grow(5.8, 8.6, s)
    s = len(d.parts)
    d.text((X(5.8) + X(8.6)) / 2, by + 30, "one request", 12, t["muted"], anchor="middle")
    dot(d, X(8.6) + 9, by + 7, work, r=5)
    d.text(X(8.6) + 9, by - 8, "ResultsLoaded", 13, work, bold=True, anchor="middle")
    r.show(8.6, s)
    d.path([(x0, 196), (X(10), 196)], color=t["line"], sw=1.2)
    for v in range(11):
        d.add(f'<path d="M{X(v):g} 193 L{X(v):g} 199" stroke="{t["line"]}" stroke-width="1"/>')
    d.text(X(0), 216, "0", 12, t["muted"], anchor="middle")
    d.text(X(10), 216, "1000 ms", 12, t["muted"], anchor="end")
    r.finish(60, 186)
    return d


if __name__ == "__main__":
    if sys.argv[1:2] == ["--kdoc"]:
        kdoc_main(sys.argv[2:])
    else:
        main(sys.argv[1:])
