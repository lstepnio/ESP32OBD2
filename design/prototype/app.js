"use strict";
const screens = {
  setup: "01 · Find your gauge",
  pair: "02 · Pair securely",
  gauge: "03 · Your gauge",
  readings: "04 · Choose readings",
  limits: "05 · Set limits",
  car: "06 · Check your car",
  update: "07 · Install update",
  recovery: "08 · Recover safely",
  settings: "Settings",
  expert: "Expert",
};
let route = location.hash.slice(1) || "gauge",
  theme = "dark",
  expanded = false,
  gallery = false,
  preview = "normal",
  advanced = false,
  page = 0;
const paths = {
  gauge: "M4 17a9 9 0 1 1 16 0M7 15l-2 1M17 15l2 1M12 4v3M12 14l4-4M9 20h6",
  car: "M4 11l2-6h12l2 6M3 11h18v8H3zM6 19v2M18 19v2M6 14h2M16 14h2",
  settings:
    "M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8M9 3h6l1 3 3 1 2 5-2 5-3 1-1 3H9l-1-3-3-1-2-5 2-5 3-1z",
  check: "M5 12l4 4L19 6",
  arrow: "M5 12h14M13 6l6 6-6 6",
  back: "M19 12H5M11 6l-6 6 6 6",
  info: "M12 11v6M12 7h.01M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0",
  warning: "M12 3L2 21h20L12 3zM12 10v4M12 17h.01",
  bluetooth: "M8 7l9 10-5 4V3l5 4L8 17",
  shield: "M12 2l8 4v7c0 5-8 9-8 9s-8-4-8-9V6l8-4M8 12l3 3 5-6",
  tools: "M4 20l8-8M14 3a6 6 0 0 0-4 8l3 3a6 6 0 0 0 8-4l-4 2-5-5 2-4",
  refresh: "M20 8a8 8 0 1 0 0 9M20 3v5h-5",
};
const icon = (name) =>
  `<svg class="icon" aria-hidden="true" viewBox="0 0 24 24"><path d="${paths[name] || paths.info}"/></svg>`;
const button = (label, action, kind = "primary") =>
  `<button class="${kind}" data-action="${action}">${label}</button>`;
const status = (title, detail, type = "info") =>
  `<div class="status ${type}"><div class="icon">${icon(type === "error" || type === "warning" ? "warning" : type === "success" ? "check" : "info")}</div><div><strong>${title}</strong><p>${detail}</p></div></div>`;
const head = (title, back = false, pill = "") =>
  `<div class="screen-head">${back ? button(icon("back"), "gauge", "back") : ""}<h1>${title}</h1>${pill ? `<span class="pill connected"><i class="tiny-dot"></i>${pill}</span>` : ""}</div>`;
const flow = (step, title) =>
  `${head(title, true)}<div class="step-label">Customize · ${step} of 4</div><div class="progress-track">${[1, 2, 3, 4].map((i) => `<i class="${i <= step ? "done" : ""}"></i>`).join("")}</div>`;
const gauge = (state = "normal", small = false) => {
  let val =
      state === "critical"
        ? "118"
        : state === "stale"
          ? "--"
          : ["2,840", "92", "64"][page],
    name =
      state === "critical"
        ? "Coolant"
        : ["Engine speed", "Coolant", "Speed"][page],
    unit = state === "critical" ? "°C" : ["rpm", "°C", "km/h"][page];
  return `<div class="round ${state} ${small ? "small" : ""}" role="img" aria-label="Preview: ${name}, ${val} ${unit}, ${state}"><svg viewBox="0 0 280 280" aria-hidden="true"><circle cx="140" cy="140" r="127" fill="none" stroke="#303730" stroke-width="5" stroke-dasharray="585 213" transform="rotate(138 140 140)"/><circle cx="140" cy="140" r="127" fill="none" stroke="${state === "critical" ? "#FFB4AB" : state === "stale" ? "#FFD28B" : "#D3F86B"}" stroke-width="5" stroke-linecap="round" stroke-dasharray="${state === "stale" ? 0 : state === "critical" ? 570 : 320} 800" transform="rotate(138 140 140)"/></svg><div class="preview-label">Preview</div><div class="reading-name">${name}</div><div class="value">${val}</div><div class="unit">${unit}</div><div class="round-state">${state === "critical" ? "COOLANT TOO HOT" : state === "stale" ? "NO RECENT READING" : "eGauge"}</div></div>`;
};
const nav = (active) =>
  `<nav class="nav" aria-label="Main navigation">${["gauge", "car", "settings", ...(advanced ? ["expert"] : [])].map((id) => `<button data-action="${id}" class="${id === active ? "selected" : ""}" ${id === active ? 'aria-current="page"' : ""}><span class="nav-icon">${icon(id === "expert" ? "tools" : id)}</span>${id === "gauge" ? "Gauge" : id[0].toUpperCase() + id.slice(1)}</button>`).join("")}</nav>`;
function content(id) {
  switch (id) {
    case "setup":
      return `${head("Set up gauge", true)}<div class="flow-main"><div class="intro-icon">${icon("gauge")}</div><h2 class="lead">Your display.<br>Your way.</h2><p class="body-copy">Power on your gauge and keep it near your phone.</p>${status("Your phone is the remote", "The gauge works on its own when you drive.")}${button("Find gauge", "pair")}${button("Explore the preview", "gauge", "secondary")}</div>`;
    case "pair":
      return `${head("Pair your gauge", true)}<div class="flow-main"><div class="intro-icon">${icon("shield")}</div><h2 class="lead">Check your display</h2><p class="body-copy">Open pairing on your gauge. Enter the code shown on its screen when Android asks.</p><div class="code">482 196</div><div class="example-note">Example code · Android handles real pairing</div>${status("Only your phone can make changes", "Keep the code private.")}${button("Continue example", "gauge")}${button("Find a different gauge", "setup", "secondary")}</div>`;
    case "gauge":
      return `${head("Your gauge", false, "Last checked")}<div class="split"><div class="hero">${gauge(preview)}<div class="pages">${["Engine", "Coolant", "Speed"].map((n, i) => `<button data-page="${i}" class="page ${i === page ? "selected" : ""}">${n}</button>`).join("")}</div></div><div>${status("Changes ready to send", "3 pages · Coolant warnings included")}${button("Send to gauge", "send")}${button("Customize", "readings", "secondary")}<div class="card"><div class="row"><span>My car<small>Adapter not connected</small></span>${icon("car")}</div></div>${button("Details", "details", "secondary")}</div></div>`;
    case "readings":
      return `${flow(1, "Choose readings")}<div class="split"><div><input class="search" type="search" placeholder="Search readings" aria-label="Search readings">${[
        ["Engine speed", "rpm"],
        ["Coolant temperature", "°C"],
        ["Vehicle speed", "km/h"],
        ["Engine load", "%"],
        ["Fuel level", "%"],
      ]
        .map(
          ([n, u], i) =>
            `<button class="row-button ${i < 3 ? "selected" : ""}" data-reading="${i}" aria-pressed="${i < 3}"><span class="index">${i < 3 ? "0" + (i + 1) : "+"}</span><span>${n}<small>${u}</small></span><span class="check">${icon(i < 3 ? "check" : "arrow")}</span></button>`,
        )
        .join(
          "",
        )}</div><div>${gauge("normal", true)}<p class="example-note">Preview · Car compatibility has not been checked</p>${button("Choose layouts", "layout")}${button("Details", "details", "secondary")}</div></div>`;
    case "limits":
      return `${flow(3, "Set your limits")}<div class="split"><div><p class="section-title">Coolant temperature</p><div class="limit"><div class="limit-header"><label for="warn">Warn above</label><strong id="warn-value">105 °C</strong></div><input id="warn" type="range" min="80" max="114" value="105"></div><div class="limit critical"><div class="limit-header"><label for="crit">Critical above</label><strong id="crit-value">115 °C</strong></div><input id="crit" type="range" min="106" max="130" value="115"></div>${button("Alert details", "limit-details", "secondary")}</div><div>${gauge(preview, true)}<div class="pages">${["normal", "warning", "critical", "stale"].map((v) => `<button class="page ${preview === v ? "selected" : ""}" data-preview="${v}">${v[0].toUpperCase() + v.slice(1)}</button>`).join("")}</div>${button("Review pages", "review")}</div></div>`;
    case "car":
      return `${head("My car")}<div class="split"><div><div class="car-drawing"><svg viewBox="0 0 240 95"><path d="M23 61l8-19 36-8 26-19h60l30 22 34 10 5 16-8 7h-15M53 70h119M80 35l19-15h51l22 17-92-2z"/><circle cx="41" cy="68" r="15"/><circle cx="188" cy="68" r="15"/><circle cx="41" cy="68" r="7"/><circle cx="188" cy="68" r="7"/></svg></div>${status("Check-engine light is on", "Example · Last checked just now", "warning")}<div class="card"><div class="row"><span><strong>P0301</strong><small>Engine misfire in cylinder 1</small></span>${icon("warning")}</div><p class="muted">A code points to a problem area. It does not identify the part to replace.</p></div></div><div><div class="card"><div class="row"><span>Vehicle adapter<small>Example · Connected</small></span>${icon("bluetooth")}</div><div class="row"><span>1 fault code<small>Example · Confirmed</small></span>${icon("info")}</div></div>${button("Check again", "example-check")}${button("Clear codes", "clear", "secondary")}${button("Details", "car-details", "secondary")}</div></div>`;
    case "update":
      return `${head("Updating gauge", true)}<div class="split"><div><div class="intro-icon">${icon("gauge")}</div><div class="update-number">64<span>%</span></div><p class="body-copy">Sending to gauge</p><div class="linear"><i style="width:64%"></i></div><p class="example-note">Example progress · No device connected</p></div><div><div class="card">${[
        ["done", "✓", "Downloading"],
        ["current", "2", "Sending to gauge"],
        ["", "3", "Restarting"],
        ["", "4", "Done"],
      ]
        .map(([c, n, l]) => `<div class="stage ${c}"><b>${n}</b>${l}</div>`)
        .join(
          "",
        )}</div><p class="body-copy">Keep your gauge powered and the app open. We will check it after it restarts.</p>${button("View your gauge", "gauge")}${button("Details", "update-details", "secondary")}</div></div>`;
    case "recovery":
      return `${head("Check your gauge", true)}<div class="flow-main"><div class="intro-icon">${icon("refresh")}</div><h2 class="lead">Let’s check what made it across</h2>${status("Your changes are unconfirmed", "The connection ended before your gauge confirmed the new settings.", "warning")}<p class="body-copy">Your choices are still on this phone. Keep your gauge powered and nearby.</p>${button("Check gauge", "recovery-check")}${button("Details", "details", "secondary")}</div>`;
    case "settings":
      return `${head("Settings")}<div class="split"><div><div class="card"><div class="row"><span>Gauge name<small>eGauge · This phone only</small></span>${icon("arrow")}</div><div class="row"><span>Brightness<small>Adjust on the gauge</small></span>${icon("gauge")}</div><div class="row"><span>Rotation<small>0°</small></span>${icon("arrow")}</div></div>${button("Check for updates", "stable-update")}</div><div><div class="card"><div class="row"><span>Use phone colours</span><button class="switch" data-action="dynamic" aria-label="Use phone colours"><i></i></button></div><div class="row"><span>Show advanced tools</span><button class="switch ${advanced ? "on" : ""}" data-action="advanced" aria-label="Show advanced tools" aria-pressed="${advanced}"><i></i></button></div><div class="row"><span>About eGauge<small>No account. No analytics.</small></span>${icon("info")}</div></div>${button("Details", "details", "secondary")}</div></div>`;
    case "expert":
      return `${head("Expert")}<p class="body-copy">Tools for exploring and testing.</p>${["PID explorer", "Custom PIDs and decoder lab", "Second adapter", "Diagnostics", "Wi-Fi security self-check", "Development updates"].map((n) => `<button class="row-button" data-expert="${n}"><span>${n}</span><span class="check">${icon("arrow")}</span></button>`).join("")}${button("Open PID explorer", "expert-info")}`;
    default:
      return content("gauge");
  }
}
function render() {
  document.documentElement.dataset.theme = theme;
  document.querySelector("#theme").textContent =
    theme === "dark" ? "Light mode" : "Dark mode";
  document.querySelector("#viewport").textContent = expanded
    ? "Compact"
    : "Expanded";
  document.querySelector("#gallery").textContent = gallery
    ? "Single screen"
    : "View all 8";
  document.querySelector("#screen").value = route;
  const review = document.querySelector("#review");
  review.className = gallery ? "gallery" : "";
  const ids = gallery ? Object.keys(screens).slice(0, 8) : [route];
  review.innerHTML = ids
    .map(
      (id) =>
        `<section>${gallery ? `<div class="gallery-label">${screens[id]}</div>` : ""}<div class="device ${expanded && !gallery ? "expanded" : "compact"}"><div class="system-bar"><span>9:41</span><span>●●●  ▰</span></div><div id="app"><div class="screen">${content(id)}</div>${nav(["gauge", "car", "settings", "expert"].includes(id) ? id : "gauge")}</div></div></section>`,
    )
    .join("");
}
function modal(title, body, cta = "Done", action = null) {
  document.querySelector("#details-content").innerHTML =
    `<h2>${title}</h2>${body}`;
  const close = document.querySelector("#close-details");
  close.textContent = cta;
  close.onclick = () => {
    document.querySelector("#details").close();
    if (action) action();
  };
  document.querySelector("#details").showModal();
}
function go(id) {
  route = id;
  gallery = false;
  location.hash = id;
  render();
}
document.querySelector("#screen").innerHTML = Object.entries(screens)
  .map(([id, name]) => `<option value="${id}">${name}</option>`)
  .join("");
document.querySelector("#screen").onchange = (e) => go(e.target.value);
document.querySelector("#theme").onclick = () => {
  theme = theme === "dark" ? "light" : "dark";
  render();
};
document.querySelector("#viewport").onclick = () => {
  expanded = !expanded;
  gallery = false;
  render();
};
document.querySelector("#gallery").onclick = () => {
  gallery = !gallery;
  render();
};
document.addEventListener("click", (e) => {
  const el = e.target.closest("button");
  if (!el) return;
  if (el.dataset.page !== undefined) {
    page = +el.dataset.page;
    render();
    return;
  }
  if (el.dataset.preview) {
    preview = el.dataset.preview;
    render();
    return;
  }
  if (el.dataset.reading !== undefined) {
    el.classList.toggle("selected");
    el.setAttribute("aria-pressed", el.classList.contains("selected"));
    return;
  }
  if (el.dataset.expert) {
    modal(
      el.dataset.expert,
      "<p>Example tool destination. The native app retains the existing read-only tools and signed development updates here.</p>",
    );
    return;
  }
  const a = el.dataset.action;
  if (screens[a]) {
    go(a);
    return;
  }
  switch (a) {
    case "send":
      go("recovery");
      break;
    case "layout":
      modal(
        "Choose a layout",
        '<p>Example · Select a style for each page.</p><div class="pages">' +
          ["Numeric", "Arc", "Bar", "Trend", "Dual"]
            .map(
              (n) =>
                `<button class="page" onclick="this.classList.toggle('selected')">${n}</button>`,
            )
            .join("") +
          "</div>",
        "Set limits",
        () => go("limits"),
      );
      break;
    case "review":
      modal(
        "Ready to send",
        '<p>3 pages: Engine speed, Coolant temperature, Vehicle speed.</p><p>Warn above 105 °C. Critical above 115 °C.</p><p class="muted">Example review. Nothing will be sent.</p>',
        "Send example",
        () => go("recovery"),
      );
      break;
    case "clear":
      modal(
        "Clear fault codes?",
        '<p>Clearing codes erases diagnostic information and may reset emissions checks. It does not fix the cause. Permanent codes may remain.</p><p class="muted">Example only. Native code clearing is unavailable.</p>',
        "Close example",
      );
      break;
    case "details":
      modal(
        "Details",
        "<p>Example data · These identifiers are not from your gauge.</p><pre>Stored revision: 12\nRunning revision: 12\nTrial: clear\nBoard: Waveshare ESP32-S3\nSHA-256: example only\nSaved settings comparison: 3 pages</pre>",
      );
      break;
    case "limit-details":
      modal(
        "Alert behaviour",
        '<p>Warn when coolant stays above 105 °C for 1 second. Clear after it stays 3 °C below the limit for 2 seconds.</p><p class="muted">Only coolant limits are supported by the current sender.</p>',
      );
      break;
    case "car-details":
      modal(
        "Fault details",
        "<p>Example · P0301 · Confirmed<br>Source: ECM<br>Other categories: not checked</p>",
      );
      break;
    case "update-details":
      modal(
        "Update details",
        "<p>Example · Signed package<br>Board, partition, signature identity and full digest are copyable in the native Details sheet.</p>",
      );
      break;
    case "stable-update":
      modal(
        "Updates are not available yet",
        "<p>This gauge currently supports development updates only. You can find them in Expert after turning on Show advanced tools.</p>",
      );
      break;
    case "recovery-check":
      modal(
        "Earlier settings are still running",
        "<p>Example recovery result. Review your pages before sending again.</p>",
        "Review pages",
        () => go("readings"),
      );
      break;
    case "example-check":
      modal(
        "Example check complete",
        "<p>This is a design simulation. No vehicle was queried.</p>",
      );
      break;
    case "advanced":
      advanced = !advanced;
      render();
      break;
    case "dynamic":
      el.classList.toggle("on");
      break;
    case "expert-info":
      modal(
        "PID explorer",
        "<p>Example standard requests, source details and raw responses belong here.</p>",
      );
      break;
  }
});
document.addEventListener("input", (e) => {
  if (e.target.id === "warn" || e.target.id === "crit")
    document.querySelector("#" + e.target.id + "-value").textContent =
      e.target.value + " °C";
  if (e.target.type === "search") {
    document
      .querySelectorAll("[data-reading]")
      .forEach((el) =>
        el.classList.toggle(
          "hide",
          !el.textContent.toLowerCase().includes(e.target.value.toLowerCase()),
        ),
      );
  }
});
render();
