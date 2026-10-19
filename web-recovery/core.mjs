/*! Adapted from Emviary recovery-core.mjs. Copyright (c) 2026 Emviary contributors.
 * MIT license: EMVIARY-LICENSE.txt */
// The firmware stays in separate regions so writes never span saved settings.
export class TargetError extends Error {
  constructor(message) { super(message); this.retryable = false; }
}
export const REGIONS = Object.freeze([
  {address: 0, limit: 0x8000}, {address: 0x8000, limit: 0x1000},
  {address: 0x10000, limit: 0x2000}, {address: 0x60000, limit: 0x300000},
]);
export const hex = bytes => Array.from(new Uint8Array(bytes), b => b.toString(16).padStart(2, '0')).join('');
export async function loadBundle(fetcher, digest, manifestURL, expectedHash, origin) {
  const get = async (url, limit) => {
    const response = await fetcher(url, {cache: 'no-store', signal: AbortSignal.timeout(20000)});
    if (!response.ok) throw new Error('The download is unavailable. Reload and try again.');
    // Bound decoded bytes even if a server omits or misreports Content-Length.
    const reader = response.body.getReader(), chunks = []; let size = 0;
    try {
      while (true) {
        const {value, done} = await reader.read(); if (done) break;
        size += value.length;
        if (size > limit) throw new TargetError('The download exceeds its allowed size.');
        chunks.push(value);
      }
    } finally { await reader.cancel().catch(() => {}); reader.releaseLock(); }
    const data = new Uint8Array(size); let offset = 0;
    for (const chunk of chunks) {data.set(chunk, offset); offset += chunk.length;}
    return data;
  };
  const manifestData = await get(manifestURL, 16384);
  if (hex(await digest('SHA-256', manifestData)) !== expectedHash) throw new TargetError('Package metadata verification failed. Reload this page.');
  const manifest = JSON.parse(new TextDecoder().decode(manifestData));
  if (manifest.board !== 'waveshare-esp32-s3-touch-lcd-1.28' || manifest.chip !== 'ESP32-S3' ||
      manifest.flashSize !== '16MB' || manifest.parts?.length !== 4) throw new TargetError('Unsupported recovery package.');
  const files = [];
  for (const [i, part] of manifest.parts.entries()) {
    const url = new URL(part.url, origin), region = REGIONS[i];
    if (url.origin !== origin || !/^\/downloads\/[a-zA-Z0-9._/-]+$/.test(url.pathname) ||
        url.search || url.hash || part.address !== region.address || !Number.isInteger(part.size) ||
        part.size <= 0 || part.size > region.limit || !/^[a-f0-9]{64}$/.test(part.sha256)) throw new TargetError('Invalid recovery layout.');
    const data = await get(url.href, part.size);
    if (data.length !== part.size || hex(await digest('SHA-256', data)) !== part.sha256) throw new TargetError('Firmware verification failed. Nothing was written.');
    files.push({address: part.address, data});
  }
  return {manifest, files};
}
export async function repair(loader, transport, files, md5, progress, {signal, identity, onIdentity} = {}) {
  const check = () => {if (signal?.aborted) throw signal.reason;};
  const write = transport.write.bind(transport);
  transport.write = async (...args) => {check(); return write(...args);};
  check();
  if (files.length !== 4 || files.some((file, i) => file.address !== REGIONS[i].address ||
      !(file.data instanceof Uint8Array) || file.data.length < 1 || file.data.length > REGIONS[i].limit)) throw new TargetError('Invalid flash regions.');
  await loader.main('default_reset'); check();
  if (loader.chip?.CHIP_NAME !== 'ESP32-S3' || loader.secureDownloadMode || !loader.IS_STUB ||
      await loader.detectFlashSize() !== '16MB') throw new TargetError('This is not a supported ESP32-S3 with 16 MB flash. Nothing was written.');
  check(); const security = await loader.getSecurityInfo(); check();
  if (security.parsedFlags.SECURE_BOOT_EN || security.flashCryptCnt !== 0) throw new TargetError('Secure boot or encrypted flash requires the original recovery method. Nothing was written.');
  const current = await loader.chip.readMac(loader); check();
  if (!current || (identity && current !== identity)) throw new TargetError('The connected board changed. Reload to select a new board.');
  onIdentity?.(current);
  // The stub sends one final raw MD5 packet after READ_FLASH. Consume it before
  // another command, otherwise it corrupts the next reply. Never read NVS.
  const sample = await loader.readFlash(0x60000, 16384); check();
  try {
    const digest = await transport.read(3000); check();
    if (sample.length !== 16384 || digest.length !== 16 || md5(sample) !== hex(digest)) throw new Error('USB read verification failed.');
  } finally { sample.fill(0); }
  // Activate slot zero last, after its complete image has passed verification.
  for (const index of [3, 0, 1, 2]) {
    const file = files[index], expected = md5(file.data); check();
    if (await loader.flashMd5sum(file.address, file.data.length) !== expected) {
      check();
      await loader.writeFlash({fileArray: [file], flashMode: 'keep', flashFreq: 'keep', flashSize: 'keep',
        eraseAll: false, compress: true, calculateMD5Hash: md5,
        reportProgress: (_i, written, total) => progress(index, written, total)});
      check();
      if (await loader.flashMd5sum(file.address, file.data.length) !== expected) throw new Error('Flash verification failed. Keep the board connected and retry.');
    }
    check(); progress(index, file.data.length, file.data.length);
  }
  check();
  try { await loader.after('hard_reset'); return {reset: true}; }
  catch { return {reset: false}; }
}

// A timed-out transport is closed before its successor can obtain the same port.
export async function attempt(create, files, md5, progress, options, timeoutMs = 120000) {
  const {loader, transport} = create(), abort = new AbortController();
  const stop = () => {abort.abort(options.signal.reason);};
  options.signal?.addEventListener('abort', stop, {once: true});
  if (options.signal?.aborted) stop();
  const timer = setTimeout(() => abort.abort(new Error('USB timed out. Check the cable and boot mode, then retry.')), timeoutMs);
  let closing;
  const close = () => closing ||= transport.disconnect();
  const cancelled = new Promise((_, reject) => abort.signal.addEventListener('abort', () => {
    close().catch(() => {}); reject(abort.signal.reason);
  }, {once: true}));
  try {
    if (abort.signal.aborted) throw abort.signal.reason;
    return await Promise.race([repair(loader, transport, files, md5, progress, {...options, signal: abort.signal}), cancelled]);
  } finally {
    clearTimeout(timer); options.signal?.removeEventListener('abort', stop);
    // Invalidate late library retries, including after a successful operation.
    abort.abort(new Error('USB session closed.'));
    let cleanupTimer;
    try {
      await Promise.race([close(), new Promise((_, reject) => {
        cleanupTimer = setTimeout(() => reject(new TargetError('USB did not close. Unplug the board and reload this page before retrying.')), 5000);
      })]);
    } finally {clearTimeout(cleanupTimer);}
  }
}
