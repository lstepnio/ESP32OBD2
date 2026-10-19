import test from 'node:test';
import assert from 'node:assert/strict';
import {createHash, webcrypto} from 'node:crypto';
import {repair, attempt, loadBundle, REGIONS} from './core.mjs';
const md5 = bytes => createHash('md5').update(bytes).digest('hex');
const sha = bytes => createHash('sha256').update(bytes).digest('hex');
const files = REGIONS.map(({address}) => ({address, data: new Uint8Array([1, 2, 3])}));
function fixture(overrides = {}) {
  const written = [], hashes = new Map(), sample = new Uint8Array(16384);
  const transport = {write: async () => {}, read: async () => new Uint8Array(Buffer.from(md5(sample), 'hex')), disconnect: async () => {}};
  const loader = {main: async () => {}, chip: {CHIP_NAME: 'ESP32-S3', readMac: async () => 'test-local-identity'},
    IS_STUB: true, secureDownloadMode: false, detectFlashSize: async () => '16MB',
    getSecurityInfo: async () => ({parsedFlags: {SECURE_BOOT_EN: false}, flashCryptCnt: 0}),
    readFlash: async () => sample, flashMd5sum: async address => hashes.get(address) || 'different',
    writeFlash: async options => {
      assert.equal(options.eraseAll, false); assert.equal(options.flashMode, 'keep');
      const file = options.fileArray[0]; written.push(file.address); hashes.set(file.address, md5(file.data));
    }, after: async () => {}, ...overrides};
  return {loader, transport, written, hashes};
}
test('writes only public regions and activates recovered slot last', async () => {
  const f = fixture(); const result = await repair(f.loader, f.transport, files, md5, () => {});
  assert.equal(result.reset, true); assert.deepEqual(f.written, [0x60000, 0, 0x8000, 0x10000]);
});
test('already matching regions are skipped', async () => {
  const f = fixture(); for (const file of files) f.hashes.set(file.address, md5(file.data));
  await repair(f.loader, f.transport, files, md5, () => {}); assert.deepEqual(f.written, []);
});
for (const [label, override] of Object.entries({
  'wrong chip': {chip: {CHIP_NAME: 'ESP32'}}, 'wrong flash': {detectFlashSize: async () => '8MB'},
  'secure download': {secureDownloadMode: true}, 'missing stub': {IS_STUB: false},
  'secure boot': {getSecurityInfo: async () => ({parsedFlags: {SECURE_BOOT_EN: true}, flashCryptCnt: 0})},
  'encryption': {getSecurityInfo: async () => ({parsedFlags: {}, flashCryptCnt: 1})},
})) test(label + ' is rejected before writing', async () => {
  const f = fixture(override); await assert.rejects(repair(f.loader, f.transport, files, md5, () => {}), {retryable: false});
  assert.deepEqual(f.written, []);
});
test('a different board cannot receive a retry', async () => {
  const f = fixture(); await assert.rejects(repair(f.loader, f.transport, files, md5, () => {}, {identity: 'different'})); assert.deepEqual(f.written, []);
});
test('invalid region cannot touch settings', async () => {
  const f = fixture(); const invalid = files.map(file => ({...file})); invalid[0].address = 0x9000;
  await assert.rejects(repair(f.loader, f.transport, invalid, md5, () => {})); assert.deepEqual(f.written, []);
});
test('trailing read digest is checked before writes', async () => {
  const f = fixture(); f.transport.read = async () => new Uint8Array(16);
  await assert.rejects(repair(f.loader, f.transport, files, md5, () => {}), /USB read/); assert.deepEqual(f.written, []);
});
test('write mismatch prevents OTA activation', async () => {
  const f = fixture({flashMd5sum: async () => 'wrong'});
  await assert.rejects(repair(f.loader, f.transport, files, md5, () => {}), /Flash verification/);
  assert.deepEqual(f.written, [0x60000]);
});
test('reset failure preserves verified-write result', async () => {
  const f = fixture({after: async () => {throw new Error('reset');}});
  assert.deepEqual(await repair(f.loader, f.transport, files, md5, () => {}), {reset: false});
});
test('timeout closes transport and fences late write packets', async () => {
  let release, closed = false;
  const f = fixture({main: () => new Promise(resolve => {release = resolve;})});
  f.transport.disconnect = async () => {closed = true;};
  await assert.rejects(attempt(() => f, files, md5, () => {}, {}, 10), /timed out/);
  assert.equal(closed, true); await assert.rejects(f.transport.write(new Uint8Array()), /timed out/);
  release(); await new Promise(resolve => setTimeout(resolve, 0)); assert.deepEqual(f.written, []);
});
test('pre-cancelled work never opens the loader', async () => {
  let opened = false; const f = fixture({main: async () => {opened = true;}});
  const abort = new AbortController(); abort.abort(new Error('cancelled'));
  await assert.rejects(attempt(() => f, files, md5, () => {}, {signal: abort.signal}), /cancelled/); assert.equal(opened, false);
});
function downloads(mutate = () => {}) {
  const parts = files.map(file => ({address: file.address, size: file.data.length, sha256: sha(file.data), url: '/downloads/' + file.address + '.bin'}));
  const manifest = {board: 'waveshare-esp32-s3-touch-lcd-1.28', chip: 'ESP32-S3', flashSize: '16MB', parts};
  mutate(manifest); const raw = JSON.stringify(manifest);
  const fetcher = async url => new Response(url === '/manifest' ? raw : files.find(file => url.endsWith('/' + file.address + '.bin')).data);
  return {raw, fetcher};
}
test('all payload hashes pass before serial is requested', async () => {
  const f = downloads(); const result = await loadBundle(f.fetcher, webcrypto.subtle.digest.bind(webcrypto.subtle), '/manifest', sha(f.raw), 'https://example.com');
  assert.equal(result.files.length, 4);
});
for (const [label, mutate] of Object.entries({
  'cross-origin payload': m => {m.parts[0].url = 'https://evil.example/downloads/0.bin';},
  'changed payload': m => {m.parts[0].sha256 = '0'.repeat(64);},
  'oversized region': m => {m.parts[0].size = 0x8001;},
  'wrong layout': m => {m.parts[0].address = 0x9000;},
})) test(label + ' is rejected during preparation', async () => {
  const f = downloads(mutate);
  await assert.rejects(loadBundle(f.fetcher, webcrypto.subtle.digest.bind(webcrypto.subtle), '/manifest', sha(f.raw), 'https://example.com'));
});
test('changed manifest is rejected before fetching firmware', async () => {
  const f = downloads(); await assert.rejects(loadBundle(f.fetcher, webcrypto.subtle.digest.bind(webcrypto.subtle), '/manifest', '0'.repeat(64), 'https://example.com'), /metadata verification/);
});
