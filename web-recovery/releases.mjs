import {hex, TargetError} from './core.mjs';

const REPO = 'https://github.com/lstepnio/ESP32OBD2';
export const MAX_PACKAGE = 0x310000;
const fail = message => {throw new TargetError(message);};
export function releaseChoices(raw) {
  if (!Array.isArray(raw) || raw.length > 100) fail('Release list is invalid. Use the GitHub link below.');
  return raw.filter(r => !r.draft && /^dev-v\d+\.\d+\.\d+-dev\.\d+$/.test(r.tag_name)).map(r => {
    const url = `${REPO}/releases/tag/${r.tag_name}`;
    if (r.html_url !== url || !Array.isArray(r.assets)) fail('Release links are invalid.');
    const asset = suffix => {
      const matches = r.assets.filter(a => typeof a.name === 'string' && a.name.endsWith(suffix));
      if (matches.length !== 1) return null;
      const a = matches[0];
      if (!/^[a-zA-Z0-9._-]+$/.test(a.name) || a.browser_download_url !== `${REPO}/releases/download/${r.tag_name}/${a.name}` ||
          !Number.isSafeInteger(a.size) || a.size < 1 || a.size > (suffix === '.apk' ? 150 * 1024 * 1024 : MAX_PACKAGE)) return null;
      return {name: a.name, url: a.browser_download_url, size: a.size,
        sha256: /^sha256:[a-f0-9]{64}$/.test(a.digest) ? a.digest.slice(7) : null};
    };
    return {tag: r.tag_name, version: r.tag_name.slice(5), url, prerelease: !!r.prerelease,
      firmware: asset('.egauge-dev-update'), apk: asset('.apk')};
  }).filter(r => r.firmware || r.apk);
}
export async function readBounded(response, limit) {
  if (!response.ok || !response.body) fail('Release list is unavailable. Use GitHub or the verified download below.');
  const reader = response.body.getReader(), chunks = []; let size = 0;
  try {
    for (;;) {
      const {value, done} = await reader.read(); if (done) break;
      size += value.length; if (size > limit) fail('The file exceeds its allowed size.');
      chunks.push(value);
    }
  } finally {await reader.cancel().catch(() => {}); reader.releaseLock();}
  const data = new Uint8Array(size); let offset = 0;
  for (const part of chunks) {data.set(part, offset); offset += part.length;}
  return data;
}
async function inflate(bytes, limit) {
  if (typeof DecompressionStream !== 'function') fail('Open recovery in an updated desktop Chrome or Edge browser.');
  const stream = new Blob([bytes]).stream().pipeThrough(new DecompressionStream('deflate-raw'));
  return readBounded(new Response(stream), limit);
}
// Accept only our two-entry ZIP envelope. No paths, encryption, ZIP64, trailing
// entries or unbounded decompression. This never extracts files to disk.
export async function unpack(bytes, decode = inflate) {
  if (!(bytes instanceof Uint8Array) || bytes.length < 22 || bytes.length > MAX_PACKAGE) fail('Choose a valid .egauge-dev-update package.');
  const v = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength), end = bytes.length - 22;
  const u16 = i => v.getUint16(i, true), u32 = i => v.getUint32(i, true);
  if (u32(end) !== 0x06054b50 || u16(end + 4) || u16(end + 6) || u16(end + 8) !== 2 || u16(end + 10) !== 2 || u16(end + 20)) fail('Unsupported package archive.');
  const directory = u32(end + 16);
  if (directory + u32(end + 12) !== end) fail('Invalid package directory.');
  const result = {}, ranges = []; let at = directory;
  for (let i = 0; i < 2; i++) {
    if (at + 46 > end || u32(at) !== 0x02014b50) fail('Invalid package entry.');
    const flags = u16(at + 8), method = u16(at + 10), compressed = u32(at + 20), size = u32(at + 24);
    const n = u16(at + 28), extra = u16(at + 30), comment = u16(at + 32), local = u32(at + 42);
    if (at + 46 + n + extra + comment > end || local + 30 > directory) fail('Invalid package bounds.');
    const name = new TextDecoder('utf-8', {fatal: true}).decode(bytes.subarray(at + 46, at + 46 + n));
    const limit = name === 'metadata.json' ? 4096 : name === 'firmware.bin' ? 0x300000 : 0;
    if (!limit || result[name] || flags & ~0x800 || ![0, 8].includes(method) || !size || size > limit || compressed > MAX_PACKAGE || u16(at + 34)) fail('Unsupported package contents.');
    if (u32(local) !== 0x04034b50 || u16(local + 6) !== flags || u16(local + 8) !== method ||
        u32(local + 14) !== u32(at + 16) || u32(local + 18) !== compressed || u32(local + 22) !== size || u16(local + 26) !== n) fail('Package headers disagree.');
    const dataAt = local + 30 + n + u16(local + 28), dataEnd = dataAt + compressed;
    if (dataEnd > directory || !bytes.subarray(local + 30, local + 30 + n).every((b, j) => b === bytes[at + 46 + j])) fail('Invalid package data.');
    const data = bytes.slice(dataAt, dataEnd);
    result[name] = method === 0 ? data : await decode(data, limit);
    if (result[name].length !== size) fail('Package size verification failed.');
    ranges.push([local, dataEnd]); at += 46 + n + extra + comment;
  }
  ranges.sort((a, b) => a[0] - b[0]);
  if (at !== end || ranges[0][0] !== 0 || ranges[0][1] !== ranges[1][0] || ranges[1][1] !== directory) fail('Unexpected package data.');
  return result;
}
export function signatureBytes(der) {
  if (der.length < 8 || der.length > 72 || der[0] !== 0x30 || der[1] !== der.length - 2) fail('Invalid firmware signature.');
  const out = new Uint8Array(64); let at = 2;
  for (let i = 0; i < 2; i++) {
    if (der[at++] !== 2) fail('Invalid firmware signature.');
    const size = der[at++]; let part = der.subarray(at, at + size); at += size;
    if (!size || size > 33 || part.length !== size || part[0] & 0x80 || (size > 1 && part[0] === 0 && !(part[1] & 0x80))) fail('Invalid firmware signature.');
    if (part.length === 33) {if (part[0] !== 0) fail('Invalid firmware signature.'); part = part.subarray(1);}
    out.set(part, i * 32 + 32 - part.length);
  }
  if (at !== der.length) fail('Invalid firmware signature.');
  return out;
}
export async function signedApplication(bytes, subtle, pem, expected = null, decode) {
  if (expected && (bytes.length !== expected.size || (expected.sha256 && hex(await subtle.digest('SHA-256', bytes)) !== expected.sha256))) fail('This file does not match the selected GitHub release. Download that release again.');
  const entries = await unpack(bytes, decode), image = entries['firmware.bin'];
  const metadata = JSON.parse(new TextDecoder('utf-8', {fatal: true}).decode(entries['metadata.json']));
  const hash = new Uint8Array(await subtle.digest('SHA-256', image));
  if (metadata.board !== 'ESP32-S3-Touch-LCD-1.28' || metadata.boardTag !== 0x31534745 || metadata.transferProtocol !== 0 ||
      metadata.imageLength !== image.length || image.length < 1024 || metadata.sha256 !== hex(hash) || !/^(?:[a-f0-9]{2}){64,72}$/.test(metadata.signatureDerHex)) fail('The firmware does not match this board or its signed metadata.');
  const spki = Uint8Array.from(atob(pem.replace(/-----[^-]+-----|\s/g, '')), c => c.charCodeAt(0));
  const key = await subtle.importKey('spki', spki, {name: 'ECDSA', namedCurve: 'P-256'}, false, ['verify']);
  const message = new Uint8Array(40), view = new DataView(message.buffer);
  view.setUint32(0, 0x31534745, true); view.setUint32(4, image.length, true); message.set(hash, 8);
  const der = Uint8Array.from(metadata.signatureDerHex.match(/../g), b => parseInt(b, 16));
  if (!await subtle.verify({name: 'ECDSA', hash: 'SHA-256'}, key, signatureBytes(der), message)) fail('Firmware signature verification failed. Nothing was written.');
  const text = (at, n) => new TextDecoder('utf-8', {fatal: true}).decode(image.subarray(at, at + n)).split('\0')[0];
  const version = text(48, 32), idf = text(144, 32);
  // This support image is reviewed for the existing 16 MB layout and IDF 5.4.1.
  // Future layouts/toolchains need a matching bootstrap, not guessed offsets.
  if (image[0] !== 0xe9 || image[2] !== 2 || image[3] !== 0x4f || image[12] !== 9 || image[13] !== 0 ||
      new DataView(image.buffer, image.byteOffset).getUint32(32, true) !== 0xabcd5432 ||
      !/^0\.2\.0-dev\.\d+$/.test(version) || !/^v5\.4\.1(?:-|$)/.test(idf) || (expected?.version && expected.version !== version)) fail('This version needs a different USB bootstrap. Use the App update flow or a matching recovery package.');
  return {image, version};
}
