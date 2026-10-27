"""Reproducible action-page review art. Does not alter App or firmware assets."""

import json
import shutil
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
PREVIOUS = OUT / "v2"
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

    def button(self, label, y=150, color="accent", width=152):
        x = self.size[0] / 2
        box = [x - width / 2, y, x + width / 2, y + 48]
        if not self.phone:
            assert all(
                hypot(a - 120, b - 120) <= 112 for a in (box[0], box[2]) for b in (box[1], box[3])
            )
        self.d.rounded_rectangle(tuple(v * S for v in box), radius=12 * S, fill=C[color])
        self.text(label, y + 12, 16, "canvas", bold=True)

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
                changeSummary="Simpler hierarchy and fewer steps. Review this changed design before implementation.",
                bounds=self.bounds,
                logicalSize=list(self.size),
            )
        )


def face(
    key,
    title,
    heading,
    value,
    *,
    button=None,
    note="",
    color="text",
    index=0,
    category="Everyday actions",
    explanation="",
    value_size=44,
    button_color="accent",
):
    a = Art()
    a.d.line(
        [(112 * S, 25 * S), (120 * S, 31 * S), (128 * S, 25 * S)], fill=C["muted"], width=2 * S
    )
    a.text(heading, 49, 20, bold=True)
    a.text(value, 83 if value_size >= 36 else 91, value_size, color, bold=True)
    if note:
        a.text(note, 127, 14, "warning" if color == "warning" else "muted")
    if button:
        a.button(button, color=button_color)
    a.dots(index)
    a.save(key, title, category, explanation)


# Retain exact prior artwork and approvals only for unchanged navigation.
for key in ["reading", "entry", "entry-two", "entry-three"]:
    shutil.copy2(PREVIOUS / (key + ".png"), OUT / (key + ".png"))
    row = dict(PRIOR[key])
    row.update(isNew=False, revised=False, changeSummary="Existing arrow-only entry is retained.")
    rows.append(row)

face(
    "air-ready",
    "Air up: hold once to start",
    "Air up",
    "5:00",
    button="Hold to start",
    note="Park + brake set",
    explanation="Replace Start then Confirm with one deliberate 0.9-second hold. The line above the control is the local Park/brake acknowledgment. Releasing early or moving cancels. Recheck conditions when the hold completes.",
)
face(
    "winch-ready",
    "Winch: the same control",
    "Winch",
    "1:00",
    button="Hold to start",
    note="Park + brake set",
    index=1,
    explanation="Same layout and deliberate hold as Air up. The App supplies the saved duration. One control per screen; no repeated confirmation page.",
)
face(
    "starting",
    "Waiting for the Start response",
    "Air up",
    "Starting",
    button="Stop",
    value_size=26,
    explanation="Show Starting until a real response arrives. Stop is immediately available. No optimistic countdown or extra confirmation.",
)
face(
    "active",
    "Air up: time first",
    "Air up",
    "4:32",
    button="Stop",
    explanation="One large countdown and one Stop control. Remove routine status prose and the extra Active label. Stop is a single tap, always in the same location.",
)
face(
    "stopping",
    "Returning to normal idle",
    "Air up",
    "Stopping",
    value_size=26,
    explanation="Stopping covers Return and its verification on the display. Internal states stay distinct. Do not show success before normal idle is verified.",
    category="Protection and recovery",
)
face(
    "stopped",
    "Verified normal idle",
    "Air up",
    "Normal idle",
    value_size=25,
    explanation="After verified restoration, show this feedback for two seconds then return to the ready action page. Remove the compulsory Done tap. Never automatically start again.",
)
face(
    "coolant-unavailable",
    "No coolant before Start",
    "Air up",
    "1:00",
    button="Continue",
    note="No coolant · 1 min max",
    color="warning",
    explanation="The shorter duration and reason are visible before commitment. In this exceptional case retain a compact Park/brake confirmation before the hold, so the local acknowledgment is not hidden. A missing display page does not cause this state.",
    category="Protection and recovery",
)
face(
    "fallback-confirm",
    "Fallback: local acknowledgment",
    "Air up",
    "1:00",
    button="Hold to start",
    note="Park + brake set",
    color="warning",
    explanation="Only the missing-coolant exception needs this additional step: the preceding screen explained the one-minute cap. Tap Continue there, then hold here. Revising a timer or recovering coolant never silently extends the committed duration.",
    category="Protection and recovery",
)
face(
    "fallback",
    "Coolant lost while running",
    "Air up",
    "0:42",
    button="Stop",
    note="No coolant",
    color="warning",
    explanation="Keep the same timer and Stop positions. Show only the actionable exception. The fallback deadline never extends when coolant returns.",
    category="Protection and recovery",
)
face(
    "hot",
    "Temperature protection is stopping",
    "Air up",
    "Stopping",
    value_size=26,
    note="Coolant high",
    color="warning",
    explanation="Make both action and cause explicit. Protective Return happens automatically. Remain on Stopping until verified; high coolant cannot be bypassed.",
    category="Protection and recovery",
)
face(
    "unknown",
    "An uncertain result stays visible",
    "Air up",
    "Check idle",
    value_size=27,
    note="Status unknown",
    color="warning",
    explanation="Keep this persistent when an operation outcome is unknown. Do not claim Stopped, restart, or add a blind retry. Normal reconciliation may resolve it. No automatic exit.",
    category="Protection and recovery",
)
face(
    "no-data",
    "Quiet connection recovery",
    "Air up",
    "Connecting",
    value_size=25,
    explanation="One clear reason replaces Unavailable plus Not available yet. Automatic retries continue. This state applies only when there is no unresolved operation.",
    category="Protection and recovery",
)
face(
    "no-coolant",
    "Protection needs setup",
    "Air up",
    "Set coolant limit",
    value_size=19,
    note="In the App",
    explanation="Keep the one required next step. No disabled Start button or duplicated warning. The setting belongs in the App, not on this small display.",
    category="Protection and recovery",
)
face(
    "active-other",
    "Stop without an extra detour",
    "Winch",
    "4:32",
    button="Stop Air up",
    note="Air up running",
    index=1,
    explanation="Browsing to Winch during Air up keeps a direct, explicitly named Stop. Never start a second idle action. Removes the View Air up then Stop detour.",
)
face(
    "abs-ready",
    "ABS + traction: available design",
    "ABS + TCS",
    "Enabled",
    button="Disable",
    value_size=27,
    index=2,
    button_color="warning",
    explanation="Short stable action name; Enabled is shown only from confirmed state. This is a future qualified flow. ABS/TCS execution remains disabled on the installed firmware.",
    category="ABS and traction",
)
face(
    "abs-confirm",
    "Keep the consequential confirmation",
    "ABS + TCS",
    "Disable both?",
    button="Disable",
    value_size=23,
    note="Restart to restore",
    index=2,
    button_color="warning",
    explanation="Retain this explicit confirmation because restoration requires an ignition cycle for the captured Jeep behavior. The timer-style idle shortcut does not apply to ABS/TCS.",
    category="ABS and traction",
)
face(
    "abs-off",
    "Confirmed disabled state",
    "ABS + TCS",
    "Disabled",
    value_size=28,
    note="Restart to restore",
    index=2,
    color="warning",
    explanation="No misleading Enable button. Show Disabled only after confirmation; the guidance remains visible. A restart alone does not prove restoration until checked.",
    category="ABS and traction",
)
face(
    "abs-unavailable",
    "Unqualified action",
    "ABS + TCS",
    "Not supported",
    value_size=23,
    index=2,
    explanation="A saved action can remain in the user-selected order, with one honest state and no control. Capability qualification remains unchanged.",
    category="ABS and traction",
)
face(
    "empty",
    "No configured actions",
    "Actions",
    "Add in App",
    value_size=27,
    explanation="One short instruction. Swipe down returns to readings. Do not add an empty pager or a Start control.",
    category="Protection and recovery",
)
# Empty pages do not imply an action carousel.
a = Art()
a.text("Actions", 49, 20, bold=True)
a.text("Add in App", 91, 27, bold=True)
a.d.line([(112 * S, 25 * S), (120 * S, 31 * S), (128 * S, 25 * S)], fill=C["muted"], width=2 * S)
a.im.resize(a.size, Image.Resampling.LANCZOS).save(OUT / "empty.png")
rows[-1]["bounds"] = a.bounds
face(
    "critical",
    "Critical alert preempts the action",
    "Coolant high",
    "Stopping",
    value_size=28,
    note="Check engine",
    color="critical",
    explanation="Critical attention takes priority. Preserve the protective command owner underneath, and never imply normal idle before verification. Full alert context remains in the App.",
    category="Protection and recovery",
)


def app(key, title, heading, items, description, button="Save to gauge"):
    a = Art(phone=True)
    a.text("9:41", 14, 12, x=40)
    a.text("eGauge", 45, 16, bold=True)
    a.text(
        "Connecting" if key == "app-pending" else "Gauge ready",
        74,
        12,
        "warning" if key == "app-pending" else "accent",
    )
    a.text(heading, 113, 27, bold=True)
    for dot in [48, 54, 60]:
        a.d.ellipse((327 * S, (dot - 1) * S, 330 * S, (dot + 2) * S), fill=C["muted"])
    y = 175
    for label, value in items:
        a.d.rounded_rectangle(
            (20 * S, y * S, 340 * S, (y + 69) * S), radius=12 * S, fill=C["surfaceRaised"]
        )
        font = ImageFont.truetype(str(FONT / "NotoSans-SemiBold.ttf"), 17 * S)
        a.text(label, y + 12, 17, x=34 + font.getlength(label) / (2 * S), bold=True)
        font = ImageFont.truetype(str(FONT / "NotoSans-Medium.ttf"), 14 * S)
        a.text(
            value,
            y + 39,
            14,
            "warning" if value == "Set limit" else "muted",
            x=34 + font.getlength(value) / (2 * S),
        )
        if label != "Saved on phone":
            a.text("›", y + 23, 20, "muted", x=320)
        y += 82
    a.button(button, 548, width=300)
    a.text("Gauge       Car       Settings", 607, 14, "muted")
    a.save(key, title, "App configuration", description)


app(
    "app-list",
    "App: one compact action list",
    "Action pages",
    [("Air up", "5 min"), ("Winch", "1 min"), ("ABS + TCS", "Not supported yet")],
    "Tap a compact row to edit; the editor contains Remove. Add opens the supported-action picker. Reorder stays in a named overflow action. Existing entry gesture settings stay under one optional row; no repeated instructions.",
    button="Add action",
)
app(
    "app-edit",
    "App: just the useful Air up settings",
    "Air up",
    [
        ("Duration", "5 min"),
        ("Coolant limit", "105 °C"),
        ("Estimate from tire pressure", "Optional"),
    ],
    "Duration and the existing shared coolant limit are the primary settings. Optional pressure-based estimation stays collapsed. Save retains the existing reviewed configuration transaction; no automatic vehicle Start.",
)
app(
    "app-warning",
    "App: inline protection setup",
    "Air up",
    [
        ("Duration", "5 min"),
        ("Coolant limit", "Set limit"),
        ("Estimate from tire pressure", "Optional"),
    ],
    "One inline missing-limit row replaces repeated Start unavailable warnings. Saving a page does not make it executable until protection is configured.",
    button="Save page",
)
app(
    "app-pending",
    "App: honest saved state",
    "Action pages",
    [("Air up", "5 min"), ("Winch", "1 min"), ("Saved on phone", "Waiting for gauge")],
    "Use the existing connection and pending-save state once. Do not repeat Waiting, Not applied and Gauge unchanged in separate cards. Never replay a Start after reconnection.",
    button="Done",
)

# The walkthrough reuses the same reviewed templates for Winch; keep these
# assets hashed without asking for duplicate per-state approvals.
variant_assets = []
for base, value, button, note, size, color in [
    ("starting", "Starting", "Stop", "", 26, "text"),
    ("active", "0:47", "Stop", "", 44, "text"),
    ("fallback", "0:42", "Stop", "No coolant", 44, "warning"),
    ("stopping", "Stopping", None, "", 26, "text"),
    ("hot", "Stopping", None, "Coolant high", 26, "warning"),
    ("unknown", "Check idle", None, "Status unknown", 27, "warning"),
    ("stopped", "Normal idle", None, "", 25, "text"),
    ("no-data", "Connecting", None, "", 25, "text"),
    ("coolant-unavailable", "1:00", "Continue", "No coolant · 1 min max", 44, "warning"),
    ("fallback-confirm", "1:00", "Hold to start", "Park + brake set", 44, "warning"),
]:
    key = "winch-" + base
    face(
        key,
        "Winch template",
        "Winch",
        value,
        button=button,
        note=note,
        value_size=size,
        color=color,
        index=1,
    )
    rows.pop()
    variant_assets.append(key + ".png")
face(
    "air-active-other",
    "Air up while Winch runs",
    "Air up",
    "0:47",
    button="Stop Winch",
    note="Winch running",
)
rows.pop()
variant_assets.append("air-active-other.png")

# The exception keeps both the reduced-protection reason and local acknowledgment
# visible at commitment. It is the only idle screen with two support lines.
for key, heading, index in [
    ("fallback-confirm", "Air up", 0),
    ("winch-fallback-confirm", "Winch", 1),
]:
    a = Art()
    a.d.line(
        [(112 * S, 25 * S), (120 * S, 31 * S), (128 * S, 25 * S)], fill=C["muted"], width=2 * S
    )
    a.text(heading, 42, 20, bold=True)
    a.text("No coolant", 69, 14, "warning")
    a.text("1:00", 88, 36, "warning", bold=True)
    a.text("Park + brake set", 130, 14, "muted")
    a.button("Hold to start")
    a.dots(index)
    a.im.resize(a.size, Image.Resampling.LANCZOS).save(OUT / (key + ".png"))
    for row in rows:
        if row["id"] == key:
            row["bounds"] = a.bounds
            row["note"] = (
                "Keep No coolant visible at the actual hold, alongside the local Park/brake acknowledgment and one-minute timer. This exceptional screen uses two short support lines. Recovery never extends the confirmed timer."
            )

manifest = dict(
    reviewId="gauge-action-pages-2026-10-27",
    reviewRound=3,
    previousRound=2,
    logicalSize=[240, 240],
    essentialRadius=112,
    decorativeRadius=116,
    reviewAssets=variant_assets
    + [
        "index.html",
        "prototype.js",
        "proposal.html",
        "mock-data.js",
        "../gallery.js",
        "v2/mock-manifest.json",
    ]
    + ["v2/" + r["id"] + ".png" for r in rows if r["revised"] and not r["isNew"]],
    screens=rows,
)
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
(OUT / "mock-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
print(f"Generated {len(rows)} screens with measured circular text and control bounds.")
