import {ESPLoader, Transport} from 'esptool-js';
import SparkMD5 from 'spark-md5';
import {attempt, loadBundle} from './core.mjs';
const panel = document.querySelector('[data-recovery]');
const start = document.querySelector('#install'), consent = document.querySelector('#confirm-board');
const status = document.querySelector('#status'), progress = document.querySelector('#progress');
const supported = window.isSecureContext && 'serial' in navigator;
let bundle, busy = false, port, identity, blocked = false;
const show = message => {status.textContent = message;};
const update = () => {start.disabled = !supported || !bundle || !consent.checked || busy || blocked; consent.disabled = busy;};
const md5 = data => SparkMD5.ArrayBuffer.hash(data.buffer.slice(data.byteOffset, data.byteOffset + data.byteLength));
consent.addEventListener('change', update);
window.addEventListener('beforeunload', event => {if (busy) {event.preventDefault(); event.returnValue = '';}});
if (!supported) show('Use desktop Chrome or Edge over HTTPS with a USB data cable. This browser cannot access USB serial devices.');
else {
  show('Checking the firmware package…');
  loadBundle(fetch, crypto.subtle.digest.bind(crypto.subtle), panel.dataset.manifest, panel.dataset.sha256, location.origin)
    .then(result => {bundle = result; show('Firmware checked. Confirm your board, then connect and install.'); update();})
    .catch(error => {show(error.message + ' Reload to try again.');});
}
update();
start.addEventListener('click', async () => {
  if (start.disabled) return;
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
        }, bundle.files, md5, (file, written, total) => {
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
