import {ESPLoader, Transport} from 'esptool-js';
import SparkMD5 from 'spark-md5';
import {attempt, loadBundle} from './core.mjs';
import {releaseChoices, readBounded, signedApplication, MAX_PACKAGE} from './releases.mjs';
import publicKey from '../firmware/gauge/main/certs/dev-update-public.pem';
const panel = document.querySelector('[data-recovery]');
const start = document.querySelector('#install'), consent = document.querySelector('#confirm-board');
const status = document.querySelector('#status'), progress = document.querySelector('#progress');
const releaseSelect = document.querySelector('#release-choice'), releaseStatus = document.querySelector('#release-status');
const upload = document.querySelector('#release-upload'), fileInput = document.querySelector('#firmware-file');
const firmwareDownload = document.querySelector('#firmware-download'), notes = document.querySelector('#release-notes');
const apkSelect = document.querySelector('#apk-choice'), apkLink = document.querySelector('#apk-download');
const apkStatus = document.querySelector('#apk-status'), apkNotes = document.querySelector('#apk-notes');
const originalApk = {url: apkLink.getAttribute('href'), message: apkStatus.textContent};
const originalNotes = notes.href;
const supported = window.isSecureContext && 'serial' in navigator;
let bundle, busy = false, port, identity, blocked = false, selection = 0, releases = [], selectedRelease;
let touched = false;
const show = message => {status.textContent = message;};
const update = () => {
  start.disabled = !supported || !bundle || !consent.checked || busy || blocked;
  consent.disabled = busy; releaseSelect.disabled = busy; fileInput.disabled = busy;
};
const md5 = data => SparkMD5.ArrayBuffer.hash(data.buffer.slice(data.byteOffset, data.byteOffset + data.byteLength));
const baseline = loadBundle(fetch, crypto.subtle.digest.bind(crypto.subtle), panel.dataset.manifest, panel.dataset.sha256, location.origin);
// A rejected baseline must not become an unhandled rejection on mobile or during
// another selection. Each selected load reports its own actionable failure.
baseline.catch(() => {});
const noSerial = 'Downloads work here. To install firmware, open this page in desktop Chrome or Edge with a USB data cable.';
async function selectFirmware() {
  if (busy) return;
  const token = ++selection;
  bundle = undefined; selectedRelease = releases.find(r => r.tag === releaseSelect.value);
  fileInput.value = ''; consent.checked = false; progress.hidden = true; progress.value = 0;
  const local = releaseSelect.value === 'local';
  upload.hidden = !local && !selectedRelease;
  document.querySelector('#firmware-download-step').hidden = local || !selectedRelease?.firmware;
  if (selectedRelease?.firmware) firmwareDownload.href = selectedRelease.firmware.url;
  notes.href = selectedRelease?.url || originalNotes;
  update();
  if (local) {show(supported ? 'Choose your downloaded .egauge-dev-update package. It will be checked locally.' : noSerial); return;}
  if (selectedRelease && !selectedRelease.firmware) {show('This release has no firmware package. Choose another release or the verified recovery build.'); return;}
  show(supported ? 'Checking the selected firmware…' : noSerial);
  try {
    const verified = await baseline;
    if (token !== selection) return;
    if (selectedRelease && (selectedRelease.version !== verified.manifest.version ||
        (selectedRelease.firmware.sha256 && selectedRelease.firmware.sha256 !== verified.manifest.bundleSha256))) {
      show(supported ? 'Download the selected firmware from GitHub, then choose that file below.' : noSerial); return;
    }
    bundle = verified; upload.hidden = true;
    show(supported ? `Firmware ${verified.manifest.version} checked. Confirm your board, then connect and install.` : noSerial);
  } catch (error) {if (token === selection) show(error.message + ' Reload to try again.');}
  finally {if (token === selection) update();}
}
releaseSelect.addEventListener('change', () => {touched = true; selectFirmware();});
fileInput.addEventListener('change', async () => {
  if (busy) return;
  touched = true;
  const token = ++selection, file = fileInput.files[0], expected = selectedRelease;
  bundle = undefined; consent.checked = false; update();
  if (!file) return;
  try {
    if (!file.name.endsWith('.egauge-dev-update') || file.size > MAX_PACKAGE || !file.size) throw new Error('Choose the unopened .egauge-dev-update file, not source code, an APK or a raw .bin.');
    show('Checking firmware signature and board compatibility…');
    const application = await signedApplication(new Uint8Array(await file.arrayBuffer()), crypto.subtle, publicKey,
      expected?.firmware ? {...expected.firmware, version: expected.version} : null);
    const verified = await baseline;
    if (token !== selection) return;
    bundle = {manifest: {...verified.manifest, version: application.version}, files: verified.files.map((f, i) => i === 3 ? {...f, data: application.image} : f)};
    show(supported ? `Firmware ${application.version} signature checked. Confirm your board, then connect and install.` : noSerial);
  } catch (error) {if (token === selection) show(error.message);}
  finally {if (token === selection) update();}
});
apkSelect.addEventListener('change', () => {
  const release = releases.find(r => r.tag === apkSelect.value);
  apkLink.href = release?.apk.url || originalApk.url;
  apkNotes.href = release?.url || 'https://github.com/lstepnio/ESP32OBD2/releases';
  apkStatus.textContent = release ? `App ${release.version} from GitHub. Read the release notes, then follow the installation steps below.` : originalApk.message;
});
async function loadReleases() {
  try {
    const response = await fetch(panel.dataset.releases, {signal: AbortSignal.timeout(12000)});
    releases = releaseChoices(JSON.parse(new TextDecoder().decode(await readBounded(response, 2 * 1024 * 1024))));
    if (!releases.length) throw new Error('No downloadable GitHub releases were found.');
    for (const release of releases) {
      const label = `${release.version}${release.prerelease ? ' · Development' : ''}`;
      if (release.firmware) releaseSelect.add(new Option(label, release.tag));
      if (release.apk) apkSelect.add(new Option(label, release.tag));
    }
    releaseStatus.textContent = 'Published releases from GitHub. The list refreshes automatically, including development releases.';
    const latestFirmware = releases.find(r => r.firmware), latestApk = releases.find(r => r.apk);
    if (!busy && !touched && latestFirmware) {releaseSelect.value = latestFirmware.tag; selectFirmware();}
    if (latestApk) {apkSelect.value = latestApk.tag; apkSelect.dispatchEvent(new Event('change'));}
    else apkStatus.textContent = 'No APKs are published on GitHub yet. The verified hosted App is available below; new release APKs will appear automatically.';
  } catch (_) {
    releaseStatus.textContent = 'GitHub releases could not be loaded. Use the verified recovery build, open a downloaded package, or browse GitHub below.';
    apkStatus.textContent = originalApk.message + ' The GitHub list is temporarily unavailable.';
  } finally {apkSelect.disabled = false; update();}
}
consent.addEventListener('change', update);
window.addEventListener('beforeunload', event => {if (busy) {event.preventDefault(); event.returnValue = '';}});
selectFirmware(); loadReleases();
start.addEventListener('click', async () => {
  if (start.disabled) return;
  const installBundle = bundle;
  touched = true;
  busy = true; panel.setAttribute('aria-busy', 'true'); update();
  try {
    // Keep requestPort in the click gesture. No automatic port selection or scan.
    if (!port || port.connected === false) port = await navigator.serial.requestPort();
    const completed = new Map();
    let result;
    for (const [index, rate] of [460800, 230400, 115200].entries()) {
      show(`Connecting and checking the board (${index + 1}/3). Keep USB connected.`);
      try {
        result = await attempt(() => {
          const transport = new Transport(port, false);
          const loader = new ESPLoader({transport, baudrate: rate, debugLogging: false,
            terminal: {clean() {}, write() {}, writeLine() {}}});
          return {loader, transport};
        }, installBundle.files, md5, (file, written, total) => {
          completed.set(file, Math.min(1, written / total));
          progress.hidden = false;
          progress.value = [...completed.values()].reduce((sum, value) => sum + value, 0) * 25;
          show('Installing and verifying firmware. Keep this tab open and USB connected.');
        }, {identity, onIdentity: value => {identity = value;}}, 120000);
        break;
      } catch (error) {
        if (error.retryable === false) {blocked = true; throw error;}
        if (index === 2) throw error;
        show('Retrying on the same board at a slower USB speed…');
      }
    }
    progress.value = 100;
    show(`Flash verified.${result.reset ? '' : ' Press RESET once.'} Check that the gauge boots and responds to swipes. Then open eGauge on Android to pair. This page cannot verify display, touch or Bluetooth.`);
    start.textContent = 'Install again';
  } catch (error) {
    if (error.name === 'NotFoundError') show('No board selected. Connect a USB data cable and try again.');
    else show(error.message + (blocked ? '' : ' Close serial monitors, check BOOT/RESET instructions below, and retry on this board.'));
  } finally {
    busy = false; panel.setAttribute('aria-busy', 'false'); update();
  }
});
