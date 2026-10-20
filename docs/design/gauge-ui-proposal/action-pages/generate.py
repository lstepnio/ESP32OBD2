"""Reproducible action-page review art. Does not alter App or firmware assets."""

import json
from math import hypot
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).resolve().parent
ROOT = OUT.parents[3]
TOKENS = json.loads((ROOT / "design/tokens.json").read_text())["color"]
FONT = ROOT / "firmware/gauge/assets/fonts"
S = 3
C = TOKENS
rows = []


class Art:
    def __init__(self, phone=False):
        self.phone = phone
        self.size = (360, 640) if phone else (240, 240)
        self.im = Image.new("RGB", tuple(v * S for v in self.size), C["canvas"])
        self.d = ImageDraw.Draw(self.im)
        self.bounds = []
        if not phone:
            self.d.ellipse((0, 0, 239 * S, 239 * S), fill="#05080A")

    def text(self, value, y, size=16, color="text", x=None, bold=False):
        x = self.size[0] / 2 if x is None else x
        font = ImageFont.truetype(
            str(FONT / ("NotoSans-SemiBold.ttf" if bold else "NotoSans-Medium.ttf")), size * S
        )
        box = self.d.textbbox((x * S, y * S), value, font=font, anchor="mt")
        bounds = [round(v / S, 2) for v in box]
        if not self.phone:
            assert all(
                hypot(a - 120, b - 120) <= 112
                for a in (bounds[0], bounds[2])
                for b in (bounds[1], bounds[3])
            ), (value, bounds)
        else:
            assert 12 <= bounds[0] < bounds[2] <= 348, (value, bounds)
        self.bounds.append({"text": value, "bounds": bounds, "fontPx": size})
        self.d.text((x * S, y * S), value, font=font, fill=C.get(color, color), anchor="mt")

    def button(self, label, y=145, color="accent", width=148):
        x = self.size[0] / 2
        box = [x - width / 2, y, x + width / 2, y + 44]
        if not self.phone:
            assert all(
                hypot(a - 120, b - 120) <= 112 for a in (box[0], box[2]) for b in (box[1], box[3])
            )
        self.d.rounded_rectangle(tuple(v * S for v in box), radius=12 * S, fill=C[color])
        self.text(label, y + 10, 16, "canvas", bold=True)

    def dots(self, index=0):
        for i in range(3):
            x = 108 + 12 * i
            self.d.ellipse(
                ((x - 2) * S, 210 * S, (x + 2) * S, 214 * S),
                fill=C["text" if i == index else "surfaceRaised"],
            )

    def save(self, key, title, category, note):
        self.im.resize(self.size, Image.Resampling.LANCZOS).save(OUT / (key + ".png"))
        rows.append(
            dict(
                id=key,
                title=title,
                category=category,
                note=note,
                isNew=True,
                revised=True,
                changeSummary="New proposal. Approval does not authorize implementation or vehicle commands.",
                bounds=self.bounds,
                logicalSize=list(self.size),
            )
        )


def face(
    key,
    title,
    heading,
    lines,
    button=None,
    note="",
    category="Action pages",
    index=0,
    top=None,
    bottom=None,
):
    a = Art()
    # The visible return control occupies a separate 44 x 44 logical hit region.
    a.d.line(
        [(110 * S, 28 * S), (120 * S, 36 * S), (130 * S, 28 * S)], fill=C["muted"], width=2 * S
    )
    if top:
        a.d.line(
            [
                (96 * S, 49 * S),
                (101 * S, 49 * S),
                (104 * S, 45 * S),
                (115 * S, 45 * S),
                (118 * S, 49 * S),
                (121 * S, 49 * S),
                (121 * S, 58 * S),
                (116 * S, 58 * S),
                (113 * S, 62 * S),
                (103 * S, 62 * S),
                (100 * S, 58 * S),
                (96 * S, 58 * S),
                (96 * S, 49 * S),
            ],
            fill=C["warning"],
            width=2 * S,
        )
        a.text("2", 46, 14, "warning", x=135)
    a.text(heading, 67, 20, bold=True)
    for value, y, size, color in lines:
        a.text(value, y, size, color)
    if button:
        a.button(*button)
    if bottom:
        a.text(bottom, 193, 12, "warning")
    a.dots(index)
    a.save(key, title, category, note)


a = Art()
a.text("RPM", 65, 16, "muted")
a.text("675", 91, 60, bold=True)
for arrow_x in (100, 120, 140):
    a.d.line([(arrow_x * S, 188 * S), (arrow_x * S, 173 * S)], fill=C["accent"], width=2 * S)
    a.d.line(
        [((arrow_x - 5) * S, 178 * S), (arrow_x * S, 173 * S), ((arrow_x + 5) * S, 178 * S)],
        fill=C["accent"],
        width=2 * S,
    )
a.text("Actions  2 / 3", 197, 12, "muted")
a.save(
    "entry",
    "Enter from any reading",
    "Navigation",
    "Keep the configured entry gesture: default three distinct upward swipes within five seconds. A small transient progress cue appears after the first swipe. A completed sequence opens actions, never starts one.",
)

face(
    "air-ready",
    "Air up: ready",
    "Air up",
    [("5 min", 101, 30, "text")],
    ("Start",),
    "One purpose and one primary control. Duration is configured in the App. Start opens a named confirmation. No arbitrary RPM control is implied.",
)
face(
    "winch-ready",
    "Winch: next action",
    "Winch",
    [("1 min", 101, 30, "text")],
    ("Start",),
    "Horizontal swipes move through the saved action order. Navigation never starts or stops a command.",
    index=1,
)
face(
    "abs-ready",
    "ABS + traction: ready",
    "ABS + traction",
    [("On", 103, 28, "text")],
    ("Turn off", 145, "warning"),
    "Uses a deliberate desired state. Confirmation explains that an ignition cycle may be needed to restore. Line Lock is excluded.",
    index=2,
)
face(
    "abs-confirm",
    "Confirm ABS + traction off",
    "Turn both off?",
    [("ABS + traction", 105, 16, "muted")],
    ("Turn off", 145, "warning"),
    "Named confirmation before disabling either system. Restoration can require a parked ignition cycle; no separate Line Lock control.",
    bottom="Restart may be needed",
    index=2,
    category="Execution",
)
face(
    "confirm",
    "Confirm the named action",
    "Start Air up?",
    [("Raised idle · 5 min", 104, 14, "muted")],
    ("Start Air up",),
    "A second deliberate tap starts once. Swipe down or the top return control cancels to the action page. Confirm only while stopped with required fresh vehicle state.",
    category="Execution",
)
face(
    "starting",
    "Request in progress",
    "Air up",
    [("Starting…", 108, 22, "muted")],
    ("Stop", 145, "warning"),
    "No success claim before verification. Repeated taps cannot create another Start. Stop requests cancellation/return through the existing command owner.",
    category="Execution",
)
face(
    "active",
    "Air up: active",
    "Air up",
    [("4:32", 98, 36, "accent")],
    ("Stop", 145, "warning"),
    "Time remaining is the main value. Stop is immediate, with no second confirmation. Opening another page does not stop or extend the action.",
    category="Execution",
)
face(
    "active-other",
    "Browse while idle is active",
    "Winch",
    [("Air up running", 107, 15, "accent")],
    ("View Air up",),
    "Conflicting actions cannot start. This button returns to the active action and its Stop control. Independent future actions require an explicit compatibility definition.",
    index=1,
    category="Execution",
)
face(
    "stopping",
    "Return is being verified",
    "Air up",
    [("Stopping…", 108, 22, "muted")],
    None,
    "A Stop acknowledgement alone is not normal idle. The worker verifies actual restoration; no optimistic success or repeated write replay.",
    category="Execution",
)
face(
    "stopped",
    "Normal idle confirmed",
    "Air up",
    [("Stopped", 106, 25, "text")],
    ("Done",),
    "Shown only after normal-idle evidence. Done returns to Ready without restarting. No automatic restart after cooldown or reconnection.",
    category="Execution",
)
face(
    "hot",
    "Coolant protection",
    "Air up",
    [("Stopping…", 107, 22, "text")],
    None,
    "CEL remains above; the reason stays below the main action state. The protective Return is latched. It never claims the engine has cooled or idle has stopped before verification.",
    top="CEL  2",
    bottom="Coolant high",
    category="Protection & recovery",
)
face(
    "unknown",
    "Connection lost during control",
    "Air up",
    [("Not confirmed", 104, 20, "warning")],
    ("Retry Stop", 145, "warning"),
    "Persistent uncertainty, not a generic disconnect toast. Retry Stop requests reconciliation first; never blindly repeats an uncertain control write. If restoration cannot be established, App context explains the ignition recovery.",
    bottom="Check idle",
    category="Protection & recovery",
)
face(
    "no-coolant",
    "Missing protection setup",
    "Air up",
    [("Needs setup", 106, 22, "muted")],
    None,
    "Start is absent. Configure the coolant stop limit in the App; no interrupting prompt on every gauge visit. A separate stale-data state must never look ready.",
    bottom="Set coolant limit in App",
    category="Protection & recovery",
)
face(
    "no-data",
    "Waiting for fresh inputs",
    "Air up",
    [("Waiting for car", 106, 19, "muted")],
    None,
    "Automatic bounded read recovery; no Refresh control. Missing, stale or wrong-vehicle inputs cannot enable Start.",
    category="Protection & recovery",
)
face(
    "abs-off",
    "ABS + traction remain off",
    "ABS + traction",
    [("Off", 101, 30, "warning")],
    ("Restore", 145, "warning"),
    "Restore is an explicit request, not proof of restored safety systems. Actual state must be independently checked. Ignition-cycle guidance is retained when needed.",
    bottom="Restart may be needed",
    index=2,
    category="Protection & recovery",
)
face(
    "abs-cycle",
    "Restoration not yet confirmed",
    "ABS + traction",
    [("Check dash", 106, 24, "warning")],
    None,
    "A positive Return reply does not erase retained Off/Unknown state. Keep this instruction until independent restoration evidence is available.",
    bottom="Restart when parked",
    index=2,
    category="Protection & recovery",
)
face(
    "empty",
    "No configured action pages",
    "Actions",
    [("Add in the App", 108, 19, "muted")],
    ("Back to gauges",),
    "An existing legacy vehicle-actions binding with no migrated pages has a clear exit. No unsupported actions are invented.",
    category="Navigation",
)
face(
    "critical",
    "Critical alert preempts actions",
    "Coolant high",
    [("118 °C", 104, 30, "critical")],
    ("Acknowledge", 145, "critical"),
    "Use the shared critical-alert attention owner. Protective stopping proceeds independently. Acknowledgement returns to the active action state; it does not clear the condition or resume idle.",
    top="CEL  2",
    category="Protection & recovery",
)


def phone(key, title, heading, subtitle, sections, cta, note):
    a = Art(phone=True)
    a.text("9:41", 16, 12, "muted", x=42)
    a.text("eGauge", 48, 16, "accent")
    a.text("Gauge connected", 76, 11, "accent")
    a.text(heading, 101, 27, bold=True)
    a.text(subtitle, 143, 14, "muted")
    for y, main, detail, right in sections:
        a.d.rounded_rectangle(
            (18 * S, y * S, 342 * S, (y + 72) * S), radius=12 * S, fill=C["surface"]
        )
        a.text(main, y + 11, 18, x=170, bold=True)
        a.text(detail, y + 40, 13, "muted", x=170)
        if right:
            if right == "⋮":
                for dot_y in (y + 24, y + 31, y + 38):
                    a.d.ellipse((319 * S, dot_y * S, 322 * S, (dot_y + 3) * S), fill=C["muted"])
            else:
                a.text(right, y + 21, 18, "accent", x=321)
    a.button(cta, 528, width=308)
    a.text("Gauge       Car       Settings", 602, 14, "muted")
    a.save(key, title, "App configuration", note)


phone(
    "app-list",
    "App: action-page order",
    "Action pages",
    "Jeep · Round gauge",
    [
        (192, "1  Air up", "5 min · coolant stop 108 °C", "⋮"),
        (276, "2  Winch", "1 min · coolant stop 108 °C", "⋮"),
        (360, "3  ABS + traction", "Off / Restore", "⋮"),
    ],
    "Add action page",
    "Gauge tab > current gauge > Action pages. One list per gauge assignment, backed by the selected vehicle. Row opens Edit; overflow offers Move up, Move down, Remove with Undo. Existing connection pill remains in the App header.",
)
phone(
    "app-add",
    "App: choose a supported action",
    "Add action",
    "For this vehicle",
    [
        (192, "Air up", "Temporary raised idle", "+"),
        (276, "Winch", "Temporary raised idle", "+"),
        (360, "ABS + traction", "Turn off / restore request", "+"),
    ],
    "Back",
    "Curated, compatible actions only. Already-added entries are disabled. No raw commands, unsupported ECU guesses, Line Lock or arbitrary scripts. If none qualify, show a brief empty state rather than misleading controls.",
)
phone(
    "app-edit",
    "App: configure and save",
    "Air up",
    "Action page settings",
    [
        (192, "Duration", "1 min     [5 min]     10 min", ""),
        (276, "Coolant stop", "108 °C · uses coolant alert", "›"),
        (360, "Page label", "Air up", "›"),
    ],
    "Save to gauge",
    "Save is acknowledged only after the running configuration matches. Coolant links to the existing alert editor instead of creating another threshold. No Start here in the configuration flow. Duration values must come from the qualified definition.",
)
phone(
    "app-warning",
    "App: protection needs setup",
    "Air up",
    "Configure before use",
    [
        (192, "Duration", "5 min", ""),
        (276, "Coolant stop needed", "Set a coolant high alert", "›"),
        (360, "Start unavailable", "Until protection is confirmed", ""),
    ],
    "Save page",
    "The page can be saved while offline, but remains unavailable on the gauge until required protection/configuration is confirmed. Warn inline once; do not add a repeated modal.",
)
phone(
    "app-pending",
    "App: saved locally, not applied",
    "Action pages",
    "Saved on phone",
    [
        (192, "Waiting for gauge", "Will sync when connected", ""),
        (276, "3 action pages", "Your order is retained", ""),
        (360, "Gauge is unchanged", "Until the update is confirmed", ""),
    ],
    "Done",
    "Use existing durable setup and connection recovery. Pending configuration is not presented as installed. Never synchronize or replay a vehicle Start intent.",
)

manifest = dict(
    reviewId="gauge-action-pages-2026-10-19-r1",
    reviewRound=1,
    previousRound=0,
    status="proposed-not-implemented",
    logicalSize=[240, 240],
    essentialRadius=112,
    decorativeRadius=116,
    reviewAssets=["index.html", "prototype.js", "proposal.html", "mock-data.js", "../gallery.js"],
    screens=rows,
)
(OUT / "mock-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
(OUT / "mock-data.js").write_text(
    "window.gaugeMockScreens="
    + json.dumps(rows)
    + ";\nwindow.gaugeMockReview="
    + json.dumps(
        {
            k: manifest[k]
            for k in ["reviewRound", "previousRound", "essentialRadius", "decorativeRadius"]
        }
    )
    + ";\n"
)
print(f"Generated {len(rows)} examples; all gauge text/button bounds fit radius 112.")
