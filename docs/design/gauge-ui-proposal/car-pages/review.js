/* Car-only enhancements over the established review client. */
const scenario = document.getElementById('scenario');
const prototype = document.getElementById('prototype');
for (const screen of screens) {
  const option = document.createElement('option');
  option.value = screen.id;
  option.textContent = screen.title;
  scenario.appendChild(option);
}
scenario.value = 'app-attention';
function showScenario(id) {
  prototype.src = 'prototype.html?scenario=' + id + '#' + id;
  scenario.value = id;
}
scenario.addEventListener('change', () => showScenario(scenario.value));
document.querySelectorAll('[data-scenario]').forEach(button => {
  button.addEventListener('click', () => showScenario(button.dataset.scenario));
});
addEventListener('message', event => {
  if (event.origin !== location.origin || event.source !== prototype.contentWindow ||
      event.data?.type !== 'car-prototype-screen') return;
  const screen = screens.find(item => item.id === event.data.id);
  document.getElementById('flow-status').textContent = screen
    ? screen.note : 'Walkthrough only. Use Back to return to the previous step.';
});
const timers = new Map();
function flushFeedback(textarea) {
  const id = textarea.id.replace('feedback-', '');
  clearTimeout(timers.get(id));
  timers.delete(id);
  if (review && drafts[id] && !same(views[id], review.screens[id])) saveScreen(id);
}
document.addEventListener('input', event => {
  if (!event.target.matches('.feedback')) return;
  const id = event.target.id.replace('feedback-', '');
  clearTimeout(timers.get(id));
  timers.set(id, setTimeout(() => flushFeedback(event.target), 900));
});
document.addEventListener('focusout', event => {
  if (event.target.matches('.feedback')) flushFeedback(event.target);
});
document.getElementById('submit-review').addEventListener('click', () => {
  for (const timer of timers.values()) clearTimeout(timer);
  timers.clear();
}, true);
const originalApi = api;
api = async function(payload) {
  if (!payload) return originalApi();
  const latest = await originalApi();
  if (payload.action === 'submit') {
    payload.screens = payload.screens.map(entry => {
      const saved = latest.screens[entry.id];
      if (!drafts[entry.id] && saved) {
        views[entry.id] = {...saved};
        return {id: entry.id, ...saved};
      }
      return entry;
    });
  }
  return originalApi({...payload, baseRevision: latest.revision});
};
async function refreshSavedFeedback() {
  if (!review || pending || document.activeElement?.matches('.feedback')) return;
  try {
    const latest = await originalApi();
    if (latest.revision <= review.revision) return;
    review = latest;
    for (const screen of screens) {
      if (!drafts[screen.id]) views[screen.id] = {status:'unreviewed',feedback:'',...latest.screens[screen.id]};
    }
    draw();
    summary();
  } catch { /* The existing save control retains drafts and reports actionable failures. */ }
}
addEventListener('focus', refreshSavedFeedback);
document.addEventListener('visibilitychange', () => {
  if (document.visibilityState === 'visible') refreshSavedFeedback();
});

// This review auto-saves comments; older review clients still require explicit Save.
const guidance = document.getElementById('review-guidance');
const guidanceObserver = new MutationObserver(() => {
  if (!review) return;
  guidanceObserver.disconnect();
  guidance.textContent = 'Approve each screen or request changes. Comments save automatically after you pause typing or leave the field. Saved feedback is shared across browsers on this Mac. Submit review when finished; unreviewed screens remain unreviewed.';
});
guidanceObserver.observe(guidance, {childList: true});
