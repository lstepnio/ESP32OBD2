/* Review-only navigation model. No transport, timers, persistence or command execution. */
(() => {
  const actions = ['air-ready', 'winch-ready', 'abs-ready'];
  let branch = false, index = 0, progress = 0, operation = null, confirming = false, lastUp = 0;
  const image = document.getElementById('prototype-face');
  const status = document.getElementById('prototype-status');
  function render() {
    let key = 'entry';
    if (branch) key = confirming ? 'confirm' : operation && index === 0 ? operation : operation ? 'active-other' : actions[index];
    image.src = key + '.png';
    image.alt = window.gaugeMockScreens.find(s => s.id === key).title;
    status.textContent = branch ? `Actions · page ${index + 1} of 3 · ${image.alt}. Swipe down returns to readings.` : `Reading page · ${progress} of 3 upward swipes. ${operation ? 'Air up state retained: ' + operation + '.' : ''}`;
    document.getElementById('proto-advance').disabled = !['starting', 'stopping', 'hot'].includes(operation);
  }
  function event(kind) {
    if (kind === 'reset') {branch = false; index = 0; progress = 0; operation = null; confirming = false;}
    else if (kind === 'up' && !branch) {if (performance.now() - lastUp > 5000) progress = 0; lastUp = performance.now(); if (++progress === 3) {branch = true; progress = 0;}}
    else if (kind === 'down') {if (confirming) confirming = false; else {branch = false; progress = 0;}}
    else if ((kind === 'next' || kind === 'prev') && branch && !confirming) index = (index + (kind === 'next' ? 1 : 2)) % 3;
    else if (kind === 'tap' && branch) {
      if (operation && index !== 0) index = 0;
      else if (operation === 'active' || operation === 'starting') operation = 'stopping';
      else if (operation === 'stopped') operation = null;
      else if (operation === 'unknown') {status.textContent = 'Reconcile vehicle state before retrying Stop; outcome remains unknown.'; return;}
      else if (confirming) {confirming = false; operation = 'starting';}
      else if (index === 0) confirming = true;
      else {status.textContent = 'This interactive path focuses on Air up. Review the Winch and ABS + traction examples below.'; return;}
    }
    else if (kind === 'advance') {
      if (operation === 'starting') operation = 'active';
      else if (operation === 'stopping' || operation === 'hot') operation = 'stopped';
    }
    else if (kind === 'heat' && ['active', 'starting'].includes(operation)) {operation = 'hot'; confirming = false;}
    else if (kind === 'loss' && ['active', 'starting', 'stopping', 'hot'].includes(operation)) {operation = 'unknown'; confirming = false;}
    render();
  }
  for (const [id, kind] of Object.entries({up:'up',prev:'prev',next:'next',down:'down',tap:'tap',advance:'advance',heat:'heat',loss:'loss',reset:'reset'})) document.getElementById('proto-' + id).onclick = () => event(kind);
  image.tabIndex = 0;
  image.setAttribute('aria-label', 'Interactive gauge. Arrow up enters, left and right browse, down returns, Enter activates.');
  image.onkeydown = e => {if (e.repeat) return; const k = {ArrowUp:'up',ArrowDown:'down',ArrowLeft:'next',ArrowRight:'prev',Enter:'tap',Escape:'down'}[e.key]; if (k) {e.preventDefault(); event(k);}};
  let start = null;
  image.style.touchAction = 'none';
  image.onpointerdown = e => {start = [e.clientX, e.clientY]; image.setPointerCapture(e.pointerId);};
  image.onpointerup = e => {if (!start) return; const dx = e.clientX-start[0], dy = e.clientY-start[1]; start = null; if (Math.max(Math.abs(dx), Math.abs(dy)) < 30) event(e.offsetY < 50 ? 'down' : 'tap'); else event(Math.abs(dx) > Math.abs(dy) ? dx < 0 ? 'next' : 'prev' : dy < 0 ? 'up' : 'down');};
  image.onpointercancel = () => {start = null;};
  render();
})();
