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
PREVIOUS = OUT / "v1"
PRIOR = {s["id"]: s for s in json.loads((PREVIOUS / "mock-manifest.json").read_text())["screens"]}


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
                isNew=key not in PRIOR,
                revised=(
                    key not in PRIOR
                    or not (PREVIOUS / (key + ".png")).exists()
                    or (PREVIOUS / (key + ".png")).read_bytes()
                    != (OUT / (key + ".png")).read_bytes()
                    or PRIOR[key]["note"] != note
                    or PRIOR[key]["title"] != title
                ),
                changeSummary="Updated for owner feedback, configurable timers and coolant fallback. New or revised examples need approval; no device commands are enabled.",
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


def entry(key, count, title):
    a = Art()
    a.text("RPM", 65, 16, "muted")
    a.text("675", 91, 60, bold=True)
    for i in range(count):
        x = 120 + (i - (count - 1) / 2) * 20
        a.d.line([(x * S, 188 * S), (x * S, 173 * S)], fill=C["accent"], width=2 * S)
        a.d.line(
            [((x - 5) * S, 178 * S), (x * S, 173 * S), ((x + 5) * S, 178 * S)],
            fill=C["accent"],
            width=2 * S,
        )
    a.save(
        key,
        title,
        "Navigation",
        "Arrow count alone shows progress: one, two, then three completed upward strokes within five seconds. No Actions text or numeric fraction. The third cue opens actions without executing a command.",
    )


entry("reading", 0, "Reading page before a gesture")
entry("entry", 1, "First upward swipe")
entry("entry-two", 2, "Second upward swipe")
entry("entry-three", 3, "Third upward swipe")

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
    "Disable ABS + TCS",
    "Disable ABS + TCS",
    [("Enabled", 103, 28, "text")],
    ("Disable", 145, "warning"),
    "TCS means traction control, not the torque-converter clutch. Explicit Disable action for the qualified Jeep; a parked ignition cycle is the observed restoration path. Line Lock is excluded.",
    index=2,
)
face(
    "abs-confirm",
    "Confirm ABS + TCS off",
    "Disable both?",
    [("ABS + TCS", 105, 16, "muted")],
    ("Disable", 145, "warning"),
    "Named confirmation states the tested Jeep restoration requirement before execution. Other profiles must provide their own qualified restoration semantics; Line Lock stays excluded.",
    bottom="Restart to restore",
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
    "Automatic bounded read recovery; no Refresh control. Missing non-coolant guards, wrong-vehicle inputs or a lost adapter cannot enable Start. Missing coolant alone uses the distinct bounded fallback design.",
    category="Protection & recovery",
)
face(
    "abs-off",
    "ABS + TCS disabled",
    "ABS + TCS",
    [
        ("Disabled", 101, 28, "warning"),
        ("Ignition cycle", 148, 16, "muted"),
        ("to restore", 172, 16, "muted"),
    ],
    None,
    "No misleading Restore button. On the tested Jeep, restart when parked is the restoration path. Keep Disabled or Unknown until fresh controller evidence confirms the result. This single state replaces the redundant restoration screen.",
    bottom="Restart when parked",
    index=2,
    category="Protection & recovery",
)

face(
    "coolant-unavailable",
    "Start with a coolant fallback timer",
    "Air up",
    [("1:00 max", 101, 28, "text")],
    ("Start",),
    "Missing or stale coolant permits only a deliberate physical-gauge Start, capped at 60 seconds or the shorter configured timer. Other vehicle guards and the configured coolant limit still apply. App Start is unavailable; this is not an overheat override.",
    bottom="Coolant unavailable",
    category="Protection & recovery",
)
face(
    "fallback",
    "Coolant lost during Air up",
    "Air up",
    [("0:42", 98, 36, "warning")],
    ("Stop", 145, "warning"),
    "First coolant loss latches at most 60 seconds remaining, bounded by the original expiry. Recovery or repeated loss cannot extend the timer. Fresh high coolant still stops immediately. Another session requires a new physical-gauge Start, with no automatic restart.",
    bottom="Coolant unavailable",
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
        (192, "1  Air up", "5 min · edit timer or PSI preset", "⋮"),
        (276, "2  Winch", "1 min · edit duration", "⋮"),
        (360, "3  ABS + TCS", "Disable · restart to restore", "⋮"),
    ],
    "Add action page",
    "Gauge tab > current gauge > Action pages. Air up and Winch each expose an editable duration; Air up also supports the four-tire pressure-based estimate. One list per gauge assignment, backed by the selected vehicle. Row opens Edit; overflow offers Move up, Move down, Remove with Undo. Existing connection pill remains in the App header.",
)
phone(
    "app-add",
    "App: choose a supported action",
    "Add action",
    "For this vehicle",
    [
        (192, "Air up", "Temporary raised idle", "+"),
        (276, "Winch", "Temporary raised idle", "+"),
        (360, "ABS + TCS", "Disable · ignition cycle to restore", "+"),
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
        (192, "Time estimate", "5 min · tap to change", "›"),
        (276, "Coolant stop", "108 °C · uses coolant alert", "›"),
        (360, "Four-tire preset", "18 to 32 PSI · tap to edit", "›"),
    ],
    "Save to gauge",
    "Air up permits a saved fixed duration or a calibrated four-tire PSI estimate, plus a one-session timer override. The estimate is not live pressure. Resolved duration stays within 10..600 seconds. Save is acknowledged only after the running configuration matches. Coolant links to the existing alert editor instead of creating another threshold. No Start here in the configuration flow. Duration values must come from the qualified definition.",
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

phone(
    "app-winch-edit",
    "App: configure Winch duration",
    "Winch",
    "Action page settings",
    [
        (192, "Duration", "1 min · tap to change", "›"),
        (276, "Coolant stop", "108 °C · uses coolant alert", "›"),
        (360, "No coolant reading", "Gauge Start capped at 1 min", ""),
    ],
    "Save to gauge",
    "Winch has its own editable saved duration within the matched definition's bounds, independent of Air up. Missing coolant uses the same physical-only, non-extending one-minute fallback. No arbitrary RPM editor.",
)
phone(
    "app-air-preset",
    "App: four-tire Air up estimate",
    "Air up preset",
    "All four tires together",
    [
        (192, "Pressure range", "18 to 32 PSI · tap to edit", "›"),
        (276, "Measured reference fill", "14 PSI in 5 min · tap to edit", "›"),
        (360, "Estimated duration", "5 min · override for this session", "›"),
    ],
    "Save preset",
    "Use starting/target PSI and a measured four-tire fill to estimate time. This is a calibrated timer, not live pressure or a target-reached promise. Require valid calibration and 10..600 seconds; ask for a valid override rather than silently clamping. Existing coolant/fallback protection may shorten the session.",
)

manifest = dict(
    reviewId="gauge-action-pages-2026-10-20-r2",
    reviewRound=2,
    previousRound=1,
    status="proposed-not-implemented",
    logicalSize=[240, 240],
    essentialRadius=112,
    decorativeRadius=116,
    reviewAssets=[
        "index.html",
        "prototype.js",
        "proposal.html",
        "mock-data.js",
        "../gallery.js",
        "v1/mock-manifest.json",
    ]
    + ["v1/" + row["id"] + ".png" for row in rows if row["revised"] and not row["isNew"]],
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
