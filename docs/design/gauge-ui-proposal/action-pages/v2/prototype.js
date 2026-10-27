/* Review-only navigation model. No transport, operation timers, persistence or command execution; only a brief entry-cue transition. */
(() => {
  const actions = ['air-ready', 'winch-ready', 'abs-ready'];
  let branch = false, index = 0, progress = 0, operation = null, confirming = false, lastUp = 0;
  let enterTimer = null;
  const image = document.getElementById('prototype-face');
  const status = document.getElementById('prototype-status');
  function render() {
    let key = progress === 3 ? 'entry-three' : progress === 2 ? 'entry-two' : progress === 1 ? 'entry' : 'reading';
    if (branch) key = confirming ? 'confirm' : operation && index === 0 ? operation : operation ? 'active-other' : actions[index];
    image.src = key + '.png';
    image.alt = window.gaugeMockScreens.find(s => s.id === key).title;
    status.textContent = branch ? `Actions · page ${index + 1} of 3 · ${image.alt}. Swipe down returns to readings.` : `Reading page · ${progress} of 3 upward swipes. ${operation ? 'Air up state retained: ' + operation + '.' : ''}`;
    const control = id => document.getElementById('proto-' + id);
    control('up').textContent = `Swipe up (${progress}/3)`;
    control('up').disabled = branch || enterTimer !== null;
    control('prev').disabled = control('next').disabled = !branch || confirming;
    control('down').disabled = !branch && progress === 0;
    control('open').disabled = branch || enterTimer !== null;
    control('tap').disabled = !branch || (!operation && index !== 0) || (index === 0 && ['stopping', 'hot', 'unknown'].includes(operation));
    control('tap').textContent = !branch ? 'Start Air up' : operation && index !== 0 ? 'Return to Air up' : index !== 0 ? 'See examples below' : confirming ? 'Confirm Start' : ({starting:'Stop',active:'Stop',fallback:'Stop',stopping:'Stopping…',hot:'Returning…',stopped:'Done',unknown:'Outcome unknown'}[operation] || 'Start Air up');
    control('advance').disabled = !['starting', 'stopping', 'hot'].includes(operation);
    control('advance').textContent = operation === 'starting' ? 'Show started response' : ['stopping', 'hot'].includes(operation) ? 'Show stopped response' : 'Show response';
    control('heat').disabled = !['active', 'starting', 'fallback'].includes(operation);
    control('coolant').disabled = operation !== 'active';
    control('loss').disabled = !['active', 'starting', 'fallback', 'stopping', 'hot'].includes(operation);
    if (!branch) status.textContent += ' Use Open actions, or make three upward swipes within five seconds.';
    else if (!operation && index !== 0) status.textContent += ' This action is illustrated in the gallery; the interactive walkthrough uses Air up.';
    else if (operation === 'starting' || operation === 'stopping' || operation === 'hot') status.textContent += ' Use Show response to simulate the vehicle reply.';
  }
  function event(kind) {
    if (['reset','open','down'].includes(kind)) {clearTimeout(enterTimer); enterTimer = null;}
    if (kind === 'reset') {branch = false; index = 0; progress = 0; operation = null; confirming = false; lastUp = 0;}
    else if (kind === 'open') {branch = true; index = 0; progress = 0; confirming = false;}
    else if (kind === 'up' && !branch && enterTimer === null) {if (progress === 0 || performance.now() - lastUp > 5000) {progress = 0; lastUp = performance.now();} if (++progress === 3) enterTimer = setTimeout(() => {enterTimer = null; branch = true; progress = 0; render();}, 350);}
    else if (kind === 'down') {if (confirming) confirming = false; else {branch = false; progress = 0;}}
    else if ((kind === 'next' || kind === 'prev') && branch && !confirming) index = (index + (kind === 'next' ? 1 : 2)) % 3;
    else if (kind === 'tap' && branch) {
      if (operation && index !== 0) index = 0;
      else if (operation === 'active' || operation === 'starting' || operation === 'fallback') operation = 'stopping';
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
    else if (kind === 'coolant' && operation === 'active') {operation = 'fallback'; index = 0;}
    else if (kind === 'heat' && ['active', 'starting', 'fallback'].includes(operation)) {operation = 'hot'; index = 0; confirming = false;}
    else if (kind === 'loss' && ['active', 'starting', 'fallback', 'stopping', 'hot'].includes(operation)) {operation = 'unknown'; index = 0; confirming = false;}
    render();
  }
  for (const [id, kind] of Object.entries({open:'open',up:'up',prev:'prev',next:'next',down:'down',tap:'tap',advance:'advance',heat:'heat',loss:'loss',coolant:'coolant',reset:'reset'})) document.getElementById('proto-' + id).onclick = () => event(kind);
  image.tabIndex = 0;
  image.setAttribute('aria-label', 'Interactive gauge. Three upward strokes enter actions. Left and right browse, down returns, Enter activates.');
  image.onkeydown = e => {if (e.repeat) return; const k = {ArrowUp:'up',ArrowDown:'down',ArrowLeft:'prev',ArrowRight:'next',Enter:'tap',Escape:'down'}[e.key]; if (k) {e.preventDefault(); event(k);}};
  let start = null;
  image.style.touchAction = 'none';
  image.onpointerdown = e => {start = [e.clientX, e.clientY]; image.setPointerCapture(e.pointerId);};
  image.onpointerup = e => {if (!start) return; const dx = e.clientX-start[0], dy = e.clientY-start[1]; start = null; if (Math.max(Math.abs(dx), Math.abs(dy)) < 30) event(e.offsetY < 50 ? 'down' : 'tap'); else event(Math.abs(dx) > Math.abs(dy) ? dx < 0 ? 'next' : 'prev' : dy < 0 ? 'up' : 'down');};
  image.onpointercancel = () => {start = null;};
  render();
})();
