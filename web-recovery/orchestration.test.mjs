import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const source = readFileSync(new URL('./recovery.js', import.meta.url), 'utf8').replace(/^import .*;\n/gm, '');
const tick = () => new Promise(resolve => setImmediate(resolve));
function deferred() { let resolve, reject; const promise = new Promise((yes, no) => {resolve = yes; reject = no;}); return {promise, resolve, reject}; }
class Element {
  constructor() { this.value = 'bundled'; this.checked = false; this.disabled = false; this.dataset = {manifest:'/manifest', sha256:'hash', releases:'/releases'}; this.href = '/original'; this.listeners = {}; this.files = []; }
  addEventListener(name, fn) { this.listeners[name] = fn; }
  setAttribute(name, value) { this[name] = value; }
  getAttribute(name) { return this[name]; }
  add() {}
  dispatchEvent(event) { return this.listeners[event.type]?.(event); }
}
for (const phase of ['port', 'write']) {
  test(`late metadata cannot change active bundle or consent during ${phase} and retry`, async () => {
    const elements = new Map();
    const element = name => {if (!elements.has(name)) elements.set(name, new Element()); return elements.get(name);};
    const metadata = deferred(), port = deferred(), write = deferred();
    const files = [{data: new Uint8Array([1,2,3])}];
    const received = [];
    const attempt = (_factory, selected) => {received.push(selected); return received.length === 1 ? write.promise : Promise.resolve({reset:true});};
    const run = new Function('ESPLoader','Transport','SparkMD5','attempt','loadBundle','releaseChoices','readBounded','signedApplication','MAX_PACKAGE','publicKey','document','window','navigator','location','fetch','Option',source);
    run(class{}, class{}, {}, attempt, async()=>({manifest:{version:'0.2.0-dev.60'},files}), x=>x,
      async response=>new TextEncoder().encode(JSON.stringify(response)), ()=>{}, 9999, '',
      {querySelector:element}, {isSecureContext:true,addEventListener(){}}, {serial:{requestPort:()=>port.promise}},
      {origin:'https://fixture.invalid'}, ()=>metadata.promise, class{});
    await tick();
    element('#confirm-board').checked = true; element('#confirm-board').listeners.change();
    assert.equal(element('#install').disabled, false);
    const installing = element('#install').listeners.click();
    if (phase === 'write') {port.resolve({connected:true}); await tick();}
    metadata.resolve([{tag:'new',version:'0.2.0-dev.61',firmware:{url:'https://fixture.invalid/new',sha256:'different'}}]);
    await tick();
    assert.equal(element('#release-choice').value, 'bundled');
    assert.equal(element('#confirm-board').checked, true);
    if (phase === 'port') {port.resolve({connected:true}); await tick();}
    write.reject(new Error('retryable connection loss'));
    await installing;
    assert.deepEqual(received, [files, files]);
    assert.match(element('#status').textContent, /^Flash verified/);
    assert.equal(element('[data-recovery]')['aria-busy'], 'false');
  });
}
