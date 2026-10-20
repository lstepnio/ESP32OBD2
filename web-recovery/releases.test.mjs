import test from 'node:test';
import assert from 'node:assert/strict';
import {createHash, generateKeyPairSync, sign, webcrypto} from 'node:crypto';
import {deflateRawSync, inflateRawSync} from 'node:zlib';
import {releaseChoices, signedApplication, unpack, signatureBytes, readBounded, MAX_PACKAGE} from './releases.mjs';
const sha = b => createHash('sha256').update(b).digest('hex');
const repo = 'https://github.com/lstepnio/ESP32OBD2', tag = 'dev-v0.2.0-dev.60';
const release = () => ({tag_name: tag, html_url: `${repo}/releases/tag/${tag}`, prerelease: true, assets: [{name: 'firmware.egauge-dev-update', size: 1234, digest: 'sha256:' + 'a'.repeat(64), browser_download_url: `${repo}/releases/download/${tag}/firmware.egauge-dev-update`}]});
test('release choices include prereleases, exclude drafts and reject foreign URLs', () => {
  const r = release(); const choices = releaseChoices([r, {...r, draft: true}]);
  assert.equal(choices.length, 1); assert.equal(choices[0].version, '0.2.0-dev.60'); assert.equal(choices[0].apk, null);
  assert.throws(() => releaseChoices([{...r, html_url: 'https://other.invalid'}]));
  r.assets[0].browser_download_url = 'https://other.invalid/firmware';
  assert.equal(releaseChoices([r]).length, 0);
});
test('APK detection requires a unique exact repository asset and bounded size', () => {
  const r = release(), name = 'egauge.apk';
  r.assets.push({name, size: 40000000, browser_download_url: `${repo}/releases/download/${tag}/${name}`});
  assert.equal(releaseChoices([r])[0].apk.name, name);
  r.assets.push({...r.assets[1]}); assert.equal(releaseChoices([r])[0].apk, null);
  assert.throws(() => releaseChoices(Array(101).fill(r)));
});
function zip(entries, method = 8) {
  const locals = [], central = []; let offset = 0;
  for (const [name, data] of Object.entries(entries)) {
    const n = Buffer.from(name), b = Buffer.from(data), c = method === 8 ? deflateRawSync(b) : b;
    const h = Buffer.alloc(30); h.writeUInt32LE(0x04034b50); h.writeUInt16LE(method, 8); h.writeUInt32LE(c.length, 18); h.writeUInt32LE(b.length, 22); h.writeUInt16LE(n.length, 26);
    const d = Buffer.alloc(46); d.writeUInt32LE(0x02014b50); d.writeUInt16LE(method, 10); d.writeUInt32LE(c.length, 20); d.writeUInt32LE(b.length, 24); d.writeUInt16LE(n.length, 28); d.writeUInt32LE(offset, 42);
    locals.push(h, n, c); central.push(d, n); offset += h.length + n.length + c.length;
  }
  const dir = Buffer.concat(central), end = Buffer.alloc(22); end.writeUInt32LE(0x06054b50); end.writeUInt16LE(2, 8); end.writeUInt16LE(2, 10); end.writeUInt32LE(dir.length, 12); end.writeUInt32LE(offset, 16);
  return new Uint8Array(Buffer.concat([...locals, dir, end]));
}
const decode = async (b, limit) => new Uint8Array(inflateRawSync(b, {maxOutputLength: limit}));
const keys = generateKeyPairSync('ec', {namedCurve: 'prime256v1'});
const publicKey = keys.publicKey.export({type: 'spki', format: 'pem'});
function fixture(change = {}) {
  const image = Buffer.alloc(1024); image.set([0xe9, 6, 2, 0x4f]); image[12] = 9; image.writeUInt32LE(0xabcd5432, 32);
  image.write('0.2.0-dev.60', 48); image.write('v5.4.1', 144);
  const message = Buffer.alloc(40); message.writeUInt32LE(0x31534745); message.writeUInt32LE(image.length, 4); Buffer.from(sha(image), 'hex').copy(message, 8);
  const metadata = {board: 'ESP32-S3-Touch-LCD-1.28', boardTag: 0x31534745, imageLength: image.length, sha256: sha(image), transferProtocol: 0, signatureDerHex: sign('sha256', message, keys.privateKey).toString('hex'), ...change};
  return {image, metadata};
}
const bundle = (f, method = 8) => zip({'metadata.json': JSON.stringify(f.metadata), 'firmware.bin': f.image}, method);
for (const method of [0, 8]) test(`signed package with ZIP method ${method} is verified before use`, async () => {
  const f = fixture(), bytes = bundle(f, method);
  const result = await signedApplication(bytes, webcrypto.subtle, publicKey, {size: bytes.length, sha256: sha(bytes), version: '0.2.0-dev.60'}, decode);
  assert.equal(result.version, '0.2.0-dev.60'); assert.deepEqual(Buffer.from(result.image), f.image);
});
test('wrong release hash/length/version and invalid signature never return image bytes', async () => {
  const bytes = bundle(fixture());
  for (const expected of [{size: 1}, {size: bytes.length, sha256: '0'.repeat(64)}, {size: bytes.length, version: '0.2.0-dev.59'}]) {
    await assert.rejects(signedApplication(bytes, webcrypto.subtle, publicKey, expected, decode));
  }
  for (const change of [{boardTag: 0}, {imageLength: 3}, {sha256: '0'.repeat(64)}, {signatureDerHex: '00'.repeat(70)}, {signatureDerHex: fixture().metadata.signatureDerHex + 'a'}]) {
    await assert.rejects(signedApplication(bundle(fixture(change)), webcrypto.subtle, publicKey, null, decode));
  }
  const other = generateKeyPairSync('ec', {namedCurve: 'prime256v1'}).publicKey.export({type: 'spki', format: 'pem'});
  await assert.rejects(signedApplication(bytes, webcrypto.subtle, other, null, decode), /signature/);
});
test('archive rejects paths, trailing bytes, encryption and excessive data', async () => {
  await assert.rejects(unpack(zip({'../metadata.json': '{}', 'firmware.bin': Buffer.alloc(1024)}), decode));
  const b = bundle(fixture()); const encrypted = b.slice(); encrypted[6] = 1;
  await assert.rejects(unpack(encrypted, decode));
  const wrongDirectory = b.slice(); wrongDirectory[wrongDirectory.length - 6] = 255;
  await assert.rejects(unpack(wrongDirectory, decode));
  await assert.rejects(unpack(new Uint8Array([...b, 0]), decode));
  await assert.rejects(unpack(new Uint8Array(MAX_PACKAGE + 1), decode));
  await assert.rejects(unpack(zip({'metadata.json': 'x'.repeat(4097), 'firmware.bin': Buffer.alloc(1024)}), decode));
});
test('network body limit applies to decoded bytes, not reported Content-Length', async () => {
  await assert.rejects(readBounded(new Response('x'.repeat(100)), 50), /allowed size/);
  assert.deepEqual(await readBounded(new Response('abc'), 3), new Uint8Array([97, 98, 99]));
  assert.throws(() => signatureBytes(new Uint8Array(72)));
});
