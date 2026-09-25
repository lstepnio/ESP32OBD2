// Design simulation only. No Bluetooth, vehicle, firmware, or cloud API calls.
const $ = (s) => document.querySelector(s);
const paths = {
  design: "M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z",
  pids: "M4 7h16 M4 12h12 M4 17h8",
  alerts: "M12 3 2 21h20L12 3z M12 9v5 M12 17v1",
  diagnostics: "M7 6h10v3h4v9H3V9h4V6z M9 3h6 M1 11v5 M10 11h4v4h-4",
  updates: "M12 3v12 M7 10l5 5 5-5 M4 17v4h16v-4",
  garage: "M3 11l9-8 9 8 M5 10v11h14V10 M9 21v-7h6v7",
  device: "M8 2h8v20H8z M11 18h2",
  numeric: "M6 5h3v14 M13 5h5v6h-5v8h5",
  arc: "M4 19a10 10 0 1 1 16 0 M12 12l5-6",
  bar: "M3 7h18v10H3z M7 7v10 M11 7v10 M15 7v10",
  trend: "M3 19V5 M3 19h18 M5 15l4-5 4 3 6-8",
  dual: "M4 4h16v16H4z M4 12h16",
  search: "M20 20l-5-5 M17 10a7 7 0 1 1-14 0 7 7 0 0 1 14 0",
};
function icon(name) {
  return `<svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="${paths[name] || paths.design}"/></svg>`;
}
const sections = {
  design: [
    "01 / YOUR DASHBOARD",
    "Your vehicle.<br>In clear view.",
    "The data you need. The way you see it.<br>Build a view that feels like your own.",
  ],
  pids: [
    "02 / EXPLORE YOUR DATA",
    "Every signal.<br>A little clearer.",
    "Discover what your vehicle can tell you.<br>Know the source behind every reading.",
  ],
  alerts: [
    "03 / STAY AWARE",
    "A quiet view.<br>Until it matters.",
    "Set your limits. Let the gauge get your attention.<br>Warnings stay active without your phone.",
  ],
  diagnostics: [
    "04 / UNDERSTAND THE LIGHT",
    "More insight.<br>Less guesswork.",
    "See the codes, their status, and their source.<br>Clear with context. Verify the result.",
  ],
  updates: [
    "05 / BUILT TO EVOLVE",
    "Better over time.<br>Ready to recover.",
    "Clear progress from transfer to restart.<br>A working version to fall back to.",
  ],
  garage: [
    "00 / YOUR GARAGE",
    "Ready for<br>your next drive.",
    "Your vehicles, views, and signals.<br>Everything starts with a connection.",
  ],
};
const catalog = [
  {
    id: "rpm",
    name: "Engine RPM",
    pid: "01 0C",
    unit: "rpm",
    value: 2840,
    max: 8000,
    status: "Responding",
    bytes: "2C60",
  },
  {
    id: "coolant",
    name: "Coolant temperature",
    pid: "01 05",
    unit: "°C",
    value: 92,
    max: 130,
    status: "Responding",
    bytes: "84",
  },
  {
    id: "speed",
    name: "Vehicle speed",
    pid: "01 0D",
    unit: "km/h",
    value: 64,
    max: 180,
    status: "Responding",
    bytes: "40",
  },
  {
    id: "load",
    name: "Engine load",
    pid: "01 04",
    unit: "%",
    value: 38,
    max: 100,
    status: "Responding",
    bytes: "61",
  },
  {
    id: "intake",
    name: "Intake air temperature",
    pid: "01 0F",
    unit: "°C",
    value: 31,
    max: 100,
    status: "Advertised",
    bytes: "47",
  },
  {
    id: "voltage",
    name: "ECU supply voltage",
    pid: "01 42",
    unit: "V",
    value: 13.9,
    max: 18,
    status: "No response",
    bytes: "3658",
  },
  {
    id: "throttle",
    name: "Throttle position",
    pid: "01 11",
    unit: "%",
    value: 27,
    max: 100,
    status: "Advertised",
    bytes: "45",
  },
  {
    id: "fuel",
    name: "Fuel level",
    pid: "01 2F",
    unit: "%",
    value: 73,
    max: 100,
    status: "Responding",
    bytes: "BA",
  },
];
catalog.forEach((p) => (p.sourceId = "ecm"));
catalog.push({
  id: "tcm-speed",
  sourceId: "tcm",
  name: "TCM input speed (demo)",
  pid: "Synthetic profile",
  unit: "rpm",
  value: 2120,
  max: 8000,
  status: "Simulated",
  bytes: "2120",
});
const state = {
  section: "design",
  layout: "arc",
  pid: "rpm",
  secondary: "coolant",
  units: "metric",
  scenario: "live",
  revision: 0,
  dirty: false,
  query: "",
  filter: "All",
  lab: false,
  scan: false,
  scanned: false,
  scanProgress: 0,
  warning: 105,
  critical: 115,
  coolant: 92,
  ack: false,
  cleared: false,
  update: 0,
  updateStarted: false,
  updatePaused: false,
  updateDone: false,
};
let saved;
try {
  saved = JSON.parse(localStorage.getItem("egauge-concept-v1"));
} catch {}
if (
  saved &&
  catalog.some((p) => p.id === saved.pid) &&
  ["arc", "numeric", "bar", "trend", "dual"].includes(saved.layout) &&
  ["metric", "imperial"].includes(saved.units)
) {
  Object.assign(state, {
    pid: saved.pid,
    layout: saved.layout,
    units: saved.units,
    revision: Number.isInteger(saved.revision) ? saved.revision : 0,
  });
  if (catalog.some((p) => p.id === saved.secondary))
    state.secondary = saved.secondary;
  if (
    Number.isFinite(saved.warning) &&
    Number.isFinite(saved.critical) &&
    saved.warning < saved.critical
  )
    Object.assign(state, { warning: saved.warning, critical: saved.critical });
}
function toast(msg) {
  $("#toast").textContent = msg;
  $("#toast").classList.add("show");
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => $("#toast").classList.remove("show"), 3500);
}
function selected() {
  return catalog.find((p) => p.id === state.pid);
}
function valueFor(p, value = p.value) {
  if (state.units === "imperial" && p.unit === "°C")
    return Math.round((value * 9) / 5 + 32);
  if (state.units === "imperial" && p.unit === "km/h")
    return Math.round(value / 1.609344);
  return value;
}
function unitFor(p) {
  return state.units === "imperial"
    ? p.unit === "°C"
      ? "°F"
      : p.unit === "km/h"
        ? "mph"
        : p.unit
    : p.unit;
}
const fmt = (v) =>
  Number.isInteger(v) ? v.toLocaleString("en-US") : v.toFixed(1);
function gaugeSvg(mini = false) {
  let p = state.scenario === "warning" ? catalog[1] : selected(),
    scenario = state.scenario,
    v = valueFor(p),
    unit = unitFor(p),
    label = p.name.toUpperCase();
  if (scenario === "warning")
    v = valueFor(catalog[1], Math.max(state.coolant, state.warning + 1));
  const color =
    scenario === "critical"
      ? "#FF7E79"
      : scenario === "stale"
        ? "#A9BABD"
        : "#B7F36B";
  let body = "";
  let status =
    scenario === "stale"
      ? "STALE · 8s old"
      : scenario === "offline"
        ? "ADAPTER OFFLINE"
        : "SIMULATED";
  const text = (x, y, t, size, fill = "#F2F5EF", weight = 500) =>
    `<text x="${x}" y="${y}" text-anchor="middle" font-family="Arial, sans-serif" font-size="${size}" font-weight="${weight}" fill="${fill}">${t}</text>`;
  if (state.updateStarted && !state.updateDone) {
    body = `${text(120, 62, "FIRMWARE UPDATE", 15, "#B7F36B")}${text(120, 130, Math.round(state.update) + "%", 56)}${text(120, 163, state.updatePaused ? "Transfer paused" : "Demo maintenance", 15, "#A9BABD")}${text(120, 189, "READINGS PAUSED", 11, "#FFCB66")}`;
  } else if (scenario === "critical" && !state.ack) {
    body = `<circle cx="120" cy="120" r="111" fill="none" stroke="#FF7E79" stroke-width="5"/>${text(120, 62, "HIGH COOLANT", 16, "#FF7E79", 700)}${text(120, 134, valueFor(catalog[1], Math.max(state.coolant, state.critical + 3)), 62)}${text(120, 159, unitFor(catalog[1]), 18)}${text(120, 187, "Tap to acknowledge", 13, "#FF7E79")}`;
  } else if (scenario === "cel") {
    body = `${text(120, 56, "CHECK ENGINE", 16, "#FFCB66", 700)}${text(120, 113, state.cleared ? "P0420" : "P0300", 40)}${text(120, 146, state.cleared ? "Permanent code" : "Random misfire", 16)}${text(120, 170, state.cleared ? "ECU must verify repair" : "Confirmed · ECU 7E8", 13, "#A9BABD")}${text(120, 192, "DEMO DIAGNOSTICS", 10, "#A9BABD")}`;
  } else {
    const value = scenario === "offline" ? "--" : fmt(v);
    const valueColor = scenario === "stale" ? "#A9BABD" : "#F2F5EF";
    if (state.layout === "arc")
      body = `<path d="M42 171 A94 94 0 1 1 198 171" fill="none" stroke="#29363B" stroke-width="7" stroke-linecap="round"/><path d="M42 171 A94 94 0 1 1 198 171" fill="none" stroke="${color}" stroke-width="7" stroke-linecap="round" pathLength="100" stroke-dasharray="${scenario === "offline" ? 0 : Math.min(100, (p.value / p.max) * 100)} 100"/>${text(120, 83, label, 13, "#A9BABD")}${text(120, 134, value, value.length > 4 ? 45 : 56, valueColor)}${text(120, 158, unit, 18, "#A9BABD")}`;
    else if (state.layout === "numeric")
      body = `${text(120, 67, label, 14, "#A9BABD")}${text(120, 132, value, value.length > 4 ? 54 : 66, valueColor)}${text(120, 163, unit, 20, "#B7F36B")}`;
    else if (state.layout === "bar")
      body = `${text(120, 69, label, 14, "#A9BABD")}${text(120, 123, value, 53, valueColor)}${text(120, 146, unit, 18, "#A9BABD")}<rect x="42" y="165" width="156" height="10" rx="5" fill="#29363B"/><rect x="42" y="165" width="${scenario === "offline" ? 0 : Math.min(156, (p.value / p.max) * 156)}" height="10" rx="5" fill="${color}"/>`;
    else if (state.layout === "trend")
      body = `${text(120, 61, label, 13, "#A9BABD")}${text(120, 108, value, 44, valueColor)}${text(120, 130, unit, 16, "#A9BABD")}<path d="M43 175H197" stroke="#344247"/>${scenario === "offline" ? "" : `<path d="M43 168 58 162 72 166 88 150 102 153 117 149 132 140 147 149 162 141 178 142 196 135" fill="none" stroke="${color}" stroke-width="2"/>`}${text(120, 193, "LAST 60 SECONDS · DEMO", 10, "#A9BABD")}`;
    else
      body = `${text(120, 56, label, 12, "#A9BABD")}${text(120, 93, value, 39, valueColor)}${text(120, 114, unit, 16, "#A9BABD")}<path d="M57 126H183" stroke="#344247"/>${text(120, 151, (catalog.find((p) => p.id === state.secondary).sourceId.toUpperCase() + " · " + (state.secondary === "tcm-speed" ? "INPUT SPEED" : catalog.find((p) => p.id === state.secondary).name)).toUpperCase(), 12, "#A9BABD")}${text(120, 184, scenario === "offline" ? "--" : valueFor(catalog.find((p) => p.id === state.secondary)) + " " + unitFor(catalog.find((p) => p.id === state.secondary)), 31, color)}`;
    if (scenario === "warning") {
      body +=
        '<circle cx="120" cy="120" r="111" fill="none" stroke="#FFCB66" stroke-width="3"/>';
      status = "HIGH COOLANT";
    }
    body += text(
      120,
      215,
      state.ack ? "ALERT ACTIVE" : status,
      9,
      state.ack ? "#FF7E79" : "#A9BABD",
    );
  }
  return `<svg viewBox="0 0 240 240" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><circle cx="120" cy="120" r="120" fill="#0C1114"/>${body}</svg>`;
}
function renderGauge() {
  $("#gauge").innerHTML = gaugeSvg();
  $("#gauge").setAttribute(
    "aria-label",
    state.scenario === "offline"
      ? "Simulated adapter disconnected. No data."
      : state.scenario === "critical"
        ? "Simulated critical coolant alert."
        : state.scenario === "cel"
          ? "Simulated check engine diagnostic code."
          : `Simulated ${state.scenario} gauge. ${selected().name}, ${fmt(valueFor(selected()))} ${unitFor(selected())}.`,
  );
  $("#gauge-title").textContent =
    state.scenario === "critical"
      ? "Critical alert / Coolant"
      : state.scenario === "cel"
        ? "Diagnostics / Check engine"
        : `${state.layout[0].toUpperCase() + state.layout.slice(1)} / ${selected().name}`;
  $("#gauge-subtitle").textContent =
    state.scenario === "critical"
      ? "Attention when it matters. Acknowledge without hiding the fault."
      : state.scenario === "stale"
        ? "Old data stays visibly old. Never mistaken for a fresh reading."
        : state.scenario === "offline"
          ? "Missing data is explicit, with a clear connection state."
          : "A clear reading, with context at a glance.";
  $("#scenario").value = state.scenario;
  $("#gauge-action").innerHTML =
    state.scenario === "critical" && !state.ack
      ? '<button id="acknowledge">Acknowledge preview alert</button>'
      : "";
  $("#acknowledge")?.addEventListener("click", () => {
    state.ack = true;
    renderGauge();
    toast("Acknowledged. The active alert badge remains.");
  });
}
function renderNav() {
  const names = {
    design: "Dashboards",
    pids: "PID explorer",
    alerts: "Threshold alerts",
    diagnostics: "Diagnostics",
    updates: "Firmware updates",
  };
  $("#studio-nav").innerHTML = Object.entries(names)
    .map(
      ([id, name]) =>
        `<button data-section="${id}" class="${state.section === id ? "active" : ""}" ${state.section === id ? 'aria-current="page"' : ""}>${icon(id)}${name}</button>`,
    )
    .join("");
  $("#phone-nav").innerHTML = [
    ["garage", "Garage"],
    ["design", "Design"],
    ["pids", "PIDs"],
    ["updates", "Device"],
  ]
    .map(
      ([id, label]) =>
        `<button data-section="${id}" class="${state.section === id || (id === "updates" && ["alerts", "diagnostics"].includes(state.section)) ? "active" : ""}">${icon(id === "updates" ? "device" : id)}${label}</button>`,
    )
    .join("");
  document
    .querySelectorAll("[data-section]")
    .forEach((b) =>
      b.addEventListener("click", () => navigate(b.dataset.section)),
    );
}
function navigate(section) {
  state.section = section;
  state.lab = false;
  const [number, headline, intro] = sections[section];
  $("#section-number").textContent = number;
  $("#headline").innerHTML = headline;
  $("#intro").innerHTML = intro;
  renderNav();
  renderPhone();
  $("#phone-content").scrollTop = 0;
  if (section === "diagnostics") {
    state.scenario = "cel";
    state.ack = false;
    renderGauge();
  } else if (state.scenario === "cel") {
    state.scenario = "live";
    renderGauge();
  }
}
const vehicleLine = () =>
  '<div class="vehicle-line"><b>●</b> Example vehicle <span>·</span> Demo gauge</div>';
function applyButton() {
  return `<button class="primary" id="apply">${state.dirty ? "Apply to demo gauge" : "Saved to demo gauge"} ${state.dirty ? "↗" : "✓"}</button><p class="apply-note">${state.dirty ? "Local draft · not applied" : `Demo revision ${state.revision} · stored in this browser`}</p>`;
}
function wireApply() {
  $("#apply")?.addEventListener("click", () => {
    state.revision++;
    state.dirty = false;
    try {
      localStorage.setItem(
        "egauge-concept-v1",
        JSON.stringify({
          layout: state.layout,
          pid: state.pid,
          units: state.units,
          revision: state.revision,
          secondary: state.secondary,
          warning: state.warning,
          critical: state.critical,
        }),
      );
      toast(`Demo configuration saved as revision ${state.revision}.`);
    } catch {
      toast("Demo applied for this session. Browser storage is unavailable.");
    }
    renderPhone();
  });
}
function designScreen() {
  return `${vehicleLine()}<h2>Make it yours.</h2><p class="app-copy">One page. Your most important reading.</p><div class="preview-card"><div class="mini-gauge">${gaugeSvg(true)}</div><div><span class="card-kicker">PAGE 01</span><strong>${selected().name}</strong><span class="pill">${state.dirty ? "Draft changes" : "Demo configuration"}</span></div></div><div class="row-head"><h3>Choose a layout</h3><small>5 views</small></div><div class="layout-grid">${["numeric", "arc", "bar", "trend", "dual"].map((x) => `<button class="layout-choice ${x === state.layout ? "selected" : ""}" data-layout="${x}" aria-pressed="${x === state.layout}">${icon(x)}${x[0].toUpperCase() + x.slice(1)}</button>`).join("")}</div><label class="field">Primary reading<select id="pid-select">${catalog.map((p) => `<option value="${p.id}" ${p.id === state.pid ? "selected" : ""}>${p.name}</option>`).join("")}</select></label>${state.layout === "dual" ? `<label class="field">Secondary reading<select id="secondary-pid">${catalog.map((p) => `<option value="${p.id}" ${p.id === state.secondary ? "selected" : ""}>${p.sourceId.toUpperCase()} · ${p.name}</option>`).join("")}</select></label>` : ""}<div class="two-fields"><label class="field">Units<select id="units"><option value="metric" ${state.units === "metric" ? "selected" : ""}>Metric</option><option value="imperial" ${state.units === "imperial" ? "selected" : ""}>Imperial</option></select></label><label class="field">Source<input readonly value="${selected().sourceId.toUpperCase()} · 7E8 · demo" aria-label="Example ECU source"></label></div>${applyButton()}<button class="subtle" data-go="alerts">Configure threshold alerts →</button>`;
}
function pidsScreen() {
  return `${vehicleLine()}<h2>${state.lab ? "PID lab." : "Meet your signals."}</h2><p class="app-copy">${state.lab ? "Start with bytes. See exactly how a value is decoded." : "Find what responds, see its source, and build your view."}</p><div class="lab-switch"><button class="${state.lab ? "secondary" : "primary"}" id="catalog-tab">Explorer</button><button class="${state.lab ? "primary" : "secondary"}" id="lab-tab">Custom lab</button></div>${state.lab ? `<div class="note-card"><strong>Example decoder · Engine RPM</strong>Service 01 / PID 0C · ECU 7E8<br>Big-endian 16-bit unsigned ÷ 4</div><label class="field">Response data bytes (prefix removed)<input id="hex-input" value="1A F8" maxlength="8" spellcheck="false"></label><button class="primary" id="decode">Decode sample</button><div class="decode-output" id="decoded">1726 rpm</div><p class="app-copy" id="decode-detail">0x1AF8 = 6904. Divide by 4.</p><div class="note-card">Production lab adds routing, bounded extraction, units, provenance, import validation, and live sample evidence. This preview evaluates only the documented RPM decoder.</div>` : `<label class="search">${icon("search")}<input id="pid-search" type="search" placeholder="Search readings or PID…" aria-label="Search PIDs"></label><div class="filter-row">${["All", "Responding", "Advertised", "No response"].map((f) => `<button data-filter="${f}" class="${state.filter === f ? "selected" : ""}" aria-pressed="${state.filter === f}">${f}</button>`).join("")}</div><button class="primary" id="scan">${state.scan ? "Cancel discovery" : state.scanned ? "Discover again" : "Discover available PIDs"}</button><div class="scan-progress" role="progressbar" aria-label="Simulated discovery" aria-valuemin="0" aria-valuemax="100" aria-valuenow="${state.scanProgress}"><span style="width:${state.scanProgress}%"></span></div><p class="app-copy" id="scan-caption">${state.scan ? "Querying example ECU 7E8…" : state.scanned ? "Complete · example responses only" : "Standard reads · ECU 7E8 · simulated evidence"}</p><div id="pid-results"></div><div class="note-card"><strong>Go beyond standard PIDs.</strong>Vehicle profiles and custom definitions will add documented manufacturer data. A timeout means no response, not unsupported.</div>`}`;
}
function renderPidResults() {
  const items = catalog.filter(
    (p) =>
      (state.filter === "All" || p.status === state.filter) &&
      `${p.name} ${p.pid}`.toLowerCase().includes(state.query.toLowerCase()),
  );
  $("#pid-results").innerHTML = items.length
    ? items
        .map(
          (p) =>
            `<div class="pid-row"><div><strong>${p.name}</strong><div class="pid-meta">${p.pid} · ${p.sourceId.toUpperCase()} / ECU 7E8 · ${unitFor(p)}</div><span class="pill ${p.status === "No response" ? "amber" : p.status === "Advertised" ? "muted" : ""}">${p.status}</span></div><button data-add="${p.id}" aria-label="Add ${p.name} to preview">+</button></div>`,
        )
        .join("")
    : '<div class="empty">No matching readings.<br>Try another filter or PID.</div>';
  document.querySelectorAll("[data-add]").forEach((b) =>
    b.addEventListener("click", () => {
      state.pid = b.dataset.add;
      state.dirty = true;
      navigate("design");
      renderGauge();
      toast("Reading added to the demo dashboard draft.");
    }),
  );
}
function alertsScreen() {
  const tempUnit = unitFor(catalog[1]);
  return `${vehicleLine()}<h2>Know your limits.</h2><p class="app-copy">Local warnings that get your attention, even when your phone is away.</p><div class="note-card"><strong>Coolant temperature · ECU 7E8</strong>Warning and critical levels are example values, not vehicle recommendations.</div><div class="two-fields"><label class="field">Warning (${tempUnit})<input type="number" id="warning" value="${valueFor(catalog[1], state.warning)}"></label><label class="field">Critical (${tempUnit})<input type="number" id="critical" value="${valueFor(catalog[1], state.critical)}"></label></div><div class="rule-line"><span>Hysteresis</span><strong>3 °C difference</strong></div><div class="rule-line"><span>Trigger / clear dwell</span><span>2 s / 5 s</span></div><div class="rule-line"><span>When data becomes stale</span><span>Keep alert + show status</span></div><p class="app-copy">Production editor will configure these settings. The values above demonstrate the rule.</p><button class="primary" id="save-alert">Save rule to draft</button><label class="field">Preview temperature: <span id="preview-temp">${valueFor(catalog[1], state.coolant)} ${tempUnit}</span><input type="range" id="temp-slider" min="70" max="130" value="${state.coolant}" aria-label="Simulated coolant temperature in Celsius"></label><button class="secondary" id="preview-alert" style="width:100%">Preview critical alert</button><p class="apply-note">Preview jumps directly to alert state; no real rule is running.</p><button class="subtle" data-go="design">Return to dashboard and apply →</button>`;
}
function diagnosticsScreen() {
  return `${vehicleLine()}<h2>Understand the light.</h2><p class="app-copy">Emissions diagnostics · ECU 7E8<br>Last read: just now in this simulation</p><div class="stat-grid"><div><strong>${state.cleared ? 0 : 1}</strong><small>Confirmed</small></div><div><strong>${state.cleared ? 0 : 1}</strong><small>Pending</small></div><div><strong>1</strong><small>Permanent</small></div></div>${state.cleared ? "" : `<div class="code-card"><div class="top"><span class="code">P0300</span><span class="pill amber">Confirmed</span></div><p>Random / multiple cylinder misfire detected</p><small>Example code · identifies a condition, not a failed part</small></div><div class="code-card"><div class="top"><span class="code">P0301</span><span class="pill muted">Pending</span></div><p>Cylinder 1 misfire detected</p><small>Example diagnostic response</small></div>`}<div class="code-card"><div class="top"><span class="code">P0420</span><span class="pill amber">Permanent</span></div><p>Catalyst system efficiency below threshold</p><small>Vehicle must verify repair before this code clears</small></div>${state.cleared ? '<div class="note-card"><strong>Demo readback complete</strong>Clear request acknowledged. Confirmed/pending codes are now empty; permanent code remains. Readiness monitors reset. This does not establish a repair.</div>' : ""}<button class="danger" id="clear-codes">Review clear-code action</button><p class="apply-note">Simulation only. No vehicle command is sent.</p>`;
}
function updatesScreen() {
  const progress = Math.round(state.update);
  return `${vehicleLine()}<h2>${state.updateDone ? "Ready for the road." : "Keep getting better."}</h2><p class="app-copy">${state.updateDone ? "Demo update confirmed. Your previous version remains available for recovery." : "A guided update, with a recovery path built in."}</p><div class="update-orbit"><strong>${state.updateDone ? "✓" : state.updateStarted ? progress + "%" : "0.2.0"}</strong><small>${state.updateDone ? "Demo complete" : state.updateStarted ? (state.updatePaused ? "Paused" : "Simulated transfer") : "Example release"}</small></div><div class="note-card"><strong>Transport-aware updates</strong>BLE for association and control. Wi-Fi for faster bulk transfer when available. Shared recovery state; hardware validation pending.</div><div class="note-card"><strong>Demo release 0.2.0</strong>More dashboard views. Clearer signal status.<br>Example installed version: ${state.updateDone ? "0.2.0" : "0.1.0"}<br>This is a UI simulation, not an available release.</div>${[
    ["Transfer", "Acknowledged image bytes"],
    ["Verify", "Image integrity and signature"],
    ["Restart & confirm", "Healthy boot or automatic rollback"],
  ]
    .map(
      ([x, y], i) =>
        `<div class="update-stage ${state.update >= (i === 0 ? 80 : i === 1 ? 90 : 100) ? "done" : ""}"><span class="step-number">${i + 1}</span><div>${x}<small>${y}</small></div></div>`,
    )
    .join(
      "",
    )}<button class="primary" id="update-action">${state.updateDone ? "Replay update preview" : !state.updateStarted ? "Preview firmware update" : state.updatePaused ? "Resume transfer" : "Pause transfer"}</button><p class="apply-note">Current hardware needs a USB OTA-layout migration first.</p><div class="filter-row"><button data-go="diagnostics">Diagnostics</button><button data-go="alerts">Threshold alerts</button></div>`;
}
function garageScreen() {
  return `${vehicleLine()}<h2>Your garage.</h2><p class="app-copy">One familiar view across every journey.</p><div class="garage-hero"><span class="pill">Example vehicle</span><h3>Everyday. Off-road.<br>Performance.</h3><p class="app-copy">A personal dashboard, with data you can trace to its source.</p><button class="primary" data-go="design">Design your dashboard ↗</button></div><div class="note-card"><strong>Two sources. One vehicle.</strong>ECM · Engine adapter · simulated<br>TCM · Transmission adapter · simulated<br>Three-link concurrency is a hardware TODO.</div><div class="garage-links">${[
    ["pids", "Explore vehicle signals"],
    ["diagnostics", "Check engine diagnostics"],
    ["alerts", "Set threshold alerts"],
    ["updates", "Device & firmware"],
  ]
    .map(
      ([id, label]) =>
        `<button data-go="${id}">${label}<span>↗</span></button>`,
    )
    .join(
      "",
    )}</div><div class="note-card"><strong>Connection flow planned</strong>Confirm your gauge → choose an adapter → discover responding data → apply a view. This concept starts with a simulated association.</div>`;
}
function renderPhone() {
  const screens = {
    design: designScreen,
    pids: pidsScreen,
    alerts: alertsScreen,
    diagnostics: diagnosticsScreen,
    updates: updatesScreen,
    garage: garageScreen,
  };
  $("#phone-content").innerHTML = screens[state.section]();
  document
    .querySelectorAll("[data-go]")
    .forEach((b) => b.addEventListener("click", () => navigate(b.dataset.go)));
  wireApply();
  if (state.section === "design") {
    $("#secondary-pid")?.addEventListener("change", (e) => {
      state.secondary = e.target.value;
      state.dirty = true;
      renderGauge();
      renderPhone();
    });
    document.querySelectorAll("[data-layout]").forEach((b) =>
      b.addEventListener("click", () => {
        state.layout = b.dataset.layout;
        state.dirty = true;
        renderGauge();
        renderPhone();
      }),
    );
    $("#pid-select").addEventListener("change", (e) => {
      state.pid = e.target.value;
      state.dirty = true;
      renderGauge();
      renderPhone();
    });
    $("#units").addEventListener("change", (e) => {
      state.units = e.target.value;
      state.dirty = true;
      renderGauge();
      renderPhone();
    });
  }
  if (state.section === "pids") {
    $("#catalog-tab").addEventListener("click", () => {
      state.lab = false;
      renderPhone();
    });
    $("#lab-tab").addEventListener("click", () => {
      state.lab = true;
      renderPhone();
    });
    if (state.lab) {
      $("#decode").addEventListener("click", () => {
        const raw = $("#hex-input").value.replace(/\s/g, "").toUpperCase();
        if (!/^[0-9A-F]{4}$/.test(raw)) {
          $("#decoded").textContent = "Invalid bytes";
          $("#decode-detail").textContent =
            "Enter exactly two hexadecimal data bytes, such as 1A F8.";
          return;
        }
        const value = parseInt(raw, 16);
        $("#decoded").textContent = value / 4 + " rpm";
        $("#decode-detail").textContent = `0x${raw} = ${value}. Divide by 4.`;
      });
    } else {
      $("#pid-search").value = state.query;
      $("#pid-search").addEventListener("input", (e) => {
        state.query = e.target.value;
        renderPidResults();
      });
      document.querySelectorAll("[data-filter]").forEach((b) =>
        b.addEventListener("click", () => {
          state.filter = b.dataset.filter;
          renderPhone();
        }),
      );
      $("#scan").addEventListener("click", () => {
        if (state.scan) {
          state.scan = false;
          toast("Discovery cancelled. Partial demo results remain.");
          renderPhone();
          return;
        }
        state.scan = true;
        state.scanProgress = 0;
        state.scanned = false;
        renderPhone();
      });
      renderPidResults();
    }
  }
  if (state.section === "alerts") {
    $("#save-alert").addEventListener("click", () => {
      const rawW = $("#warning").value,
        rawC = $("#critical").value;
      let w = Number(rawW),
        c = Number(rawC);
      if (state.units === "imperial") {
        w = ((w - 32) * 5) / 9;
        c = ((c - 32) * 5) / 9;
      }
      if (
        rawW === "" ||
        rawC === "" ||
        !Number.isFinite(w) ||
        !Number.isFinite(c) ||
        w >= c ||
        w < -37 ||
        c > 215
      ) {
        toast(
          "Use valid temperatures: warning must be below critical, within the supported range.",
        );
        return;
      }
      state.warning = w;
      state.critical = c;
      state.dirty = true;
      toast("Threshold rule saved to local draft. Apply it from Design.");
    });
    $("#preview-alert").addEventListener("click", () => {
      state.scenario = "critical";
      state.ack = false;
      renderGauge();
    });
    $("#temp-slider").addEventListener("input", (e) => {
      state.coolant = Number(e.target.value);
      $("#preview-temp").textContent =
        `${valueFor(catalog[1], state.coolant)} ${unitFor(catalog[1])}`;
      state.pid = "coolant";
      catalog[1].value = state.coolant;
      state.scenario =
        state.coolant >= state.critical
          ? "critical"
          : state.coolant >= state.warning
            ? "warning"
            : "live";
      state.ack = false;
      renderGauge();
    });
  }
  if (state.section === "diagnostics")
    $("#clear-codes").addEventListener("click", () => {
      $("#clear-confirm").checked = false;
      $("#confirm-clear").disabled = true;
      $("#clear-dialog").showModal();
    });
  if (state.section === "updates")
    $("#update-action").addEventListener("click", () => {
      if (state.updateDone) {
        state.update = 0;
        state.updateDone = false;
        state.updateStarted = false;
      }
      if (!state.updateStarted) {
        state.updateStarted = true;
        state.updatePaused = false;
      } else state.updatePaused = !state.updatePaused;
      renderPhone();
    });
}
$("#scenario").addEventListener("change", (e) => {
  state.scenario = e.target.value;
  state.ack = false;
  renderGauge();
  if (state.section === "design") renderPhone();
});
$("#clear-confirm").addEventListener(
  "change",
  (e) => ($("#confirm-clear").disabled = !e.target.checked),
);
$("#confirm-clear").addEventListener("click", () => {
  if (!$("#clear-confirm").checked) return;
  state.cleared = true;
  $("#clear-dialog").close();
  renderPhone();
  renderGauge();
  toast("Demo clear acknowledged. Permanent code remains after readback.");
});
setInterval(() => {
  if (state.scan) {
    state.scanProgress = Math.min(100, state.scanProgress + 10);
    if (state.scanProgress === 100) {
      state.scan = false;
      state.scanned = true;
      catalog.find((p) => p.id === "intake").status = "Responding";
      catalog.find((p) => p.id === "throttle").status = "Responding";
      toast("Demo discovery finished. Each result retains its evidence state.");
    }
    if (state.section === "pids" && !state.lab) renderPhone();
  }
  if (state.updateStarted && !state.updatePaused && !state.updateDone) {
    state.update = Math.min(100, state.update + 5);
    if (state.update === 100) {
      state.updateDone = true;
      toast("Demo firmware boot confirmed. Update simulation complete.");
    }
    renderGauge();
    if (state.section === "updates") renderPhone();
  }
}, 450);
navigate("design");
renderGauge();
