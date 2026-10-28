/* Review-only animation using the same edge-ring footprint as the static art. */
(() => {
  window.actionReviewRing = (parent, ring) => {
    let layer=parent.querySelector(':scope > .phase-ring');
    if(!ring){layer?.remove();return;}
    if(layer?.dataset.color===ring.color)return;
    layer?.remove();layer=document.createElement('div');layer.className='phase-ring';layer.dataset.color=ring.color;layer.setAttribute('aria-hidden','true');
    // Full track masks the first static frame; only the short arc rotates.
    layer.innerHTML=`<svg viewBox="0 0 240 240"><circle cx="120" cy="120" r="113" fill="none" stroke="#202C32" stroke-width="6"/><g class="spin"><circle cx="120" cy="120" r="113" fill="none" stroke="${ring.color}" stroke-width="6" stroke-linecap="round" stroke-dasharray="158 711" transform="rotate(-90 120 120)"/></g></svg>`;
    parent.appendChild(layer);
  };
  const update=()=>{for(const card of document.querySelectorAll('#cards > [data-screen]')){
    const item=window.gaugeMockScreens.find(s=>s.id===card.dataset.screen);
    window.actionReviewRing(card.querySelector(':scope > .face'),item?.ring);
  }};
  new MutationObserver(update).observe(document.getElementById('cards'),{childList:true});
  update();
})();
