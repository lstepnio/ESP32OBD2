/* Review-only interaction. Never imports a transport or sends device commands. */
(() => {
  const $ = id => document.getElementById(id);
  const actions = ['Air up', 'Winch', 'ABS + TCS'];
  const ready = ['air-ready', 'winch-ready', 'abs-ready'];
  let page = 0, branch = true, entry = 0, firstSwipe = 0, idle = null;
  let abs = 'ready', missing = false, fallbackConfirmed = false, cooldown = null;
  let hold = null, press = null, doneTimer = null, swipe = null;
  const image = $('prototype-face'), primary = $('proto-primary'), face = $('face-primary');
  function cancelHold() {
    clearTimeout(hold); hold = null; press = null;
    primary.classList.remove('holding'); face.classList.remove('holding');
  }
  function state() {
    if (!branch) return {key: entry ? ['reading', 'entry', 'entry-two', 'entry-three'][entry] : 'reading', label:'Open actions', disabled:true};
    if (idle && page < 2) {
      if (page !== idle.page && ['starting','active','fallback'].includes(idle.phase)) return {key:'active-other', label:'Stop '+actions[idle.page], action:'stop'};
      const map = {starting:['starting','Stop','stop'], active:['active','Stop','stop'], fallback:['fallback','Stop','stop'], stopping:['stopping','Stopping'], hot:['hot','Stopping'], unknown:['unknown','Check idle'], stopped:['stopped','Normal idle']};
      const [key,label,action] = map[idle.phase];
      return {key,label,action,disabled:!action};
    }
    if (page === 2) {
      return abs === 'ready' ? {key:'abs-ready',label:'Disable',action:'confirmAbs'} : abs === 'confirm' ? {key:'abs-confirm',label:'Disable',action:'disableAbs'} : {key:'abs-off',label:'Restart to restore',disabled:true};
    }
    if (cooldown === 'offline') return {key:'no-data',label:'Connecting',disabled:true};
    if (missing && !fallbackConfirmed) return {key:'coolant-unavailable',label:'Continue',action:'fallback'};
    return {key:missing?'fallback-confirm':ready[page],label:'Hold to start',action:'start',hold:true};
  }
  function render() {
    const s = state(); let art=s.key;
    if(page===1 && s.key!=='active-other' && !s.key.startsWith('abs-') && !['reading','entry','entry-two','entry-three','winch-ready'].includes(s.key))art='winch-'+s.key;
    if(s.key==='active-other' && idle?.page===1)art='air-active-other';
    image.src=art+'.png'; image.alt=actions[page]+': '+window.gaugeMockScreens.find(x=>x.id===s.key).title;
    primary.textContent=s.label; primary.disabled=!!s.disabled; face.disabled=!!s.disabled;
    face.hidden=!!s.disabled;face.setAttribute('aria-label',s.label+' '+actions[page]);
    $('proto-open').disabled=branch; $('proto-up').disabled=branch;
    $('proto-prev').disabled=$('proto-next').disabled=!branch || abs==='confirm';
    $('proto-down').textContent=abs==='confirm'?'Cancel':'Back to readings';
    $('proto-response').disabled=!(idle && ['starting','stopping','hot'].includes(idle.phase));
    $('proto-response').textContent=idle?.phase==='starting'?'Show started response':'Show normal idle response';
    $('proto-heat').disabled=!(idle && ['starting','active','fallback'].includes(idle.phase));
    $('proto-coolant').disabled=page===2 || !!idle && !['starting','active'].includes(idle.phase);
    $('proto-recover').disabled=!(cooldown || idle?.phase==='unknown' || missing);
    $('prototype-status').textContent=!branch?`Reading page. ${entry ? entry+' upward swipe'+(entry===1?'':'s')+'. ' : ''}${idle?'Action state is retained.':''}`:
      s.hold?'Hold confirms Park and parking brake set. Early release cancels.':
      idle?.phase==='unknown'?'Outcome remains unknown until a real status check resolves it. Do not automatically retry Start.':
      idle?.phase==='stopped'?'Verified normal idle. Returns to Ready automatically.':
      page===2?'Design preview for qualified ABS/TCS. This function is disabled on the installed gauge.':
      missing&&!idle?'Without coolant: one minute maximum. Continue to the local acknowledgment.':
      idle?.phase==='starting'?'Waiting for a vehicle response. Stop is available now.':
      idle?.phase==='active'||idle?.phase==='fallback'?'The countdown is the example remaining time. Tap Stop immediately when needed.':
      idle?.phase==='stopping'||idle?.phase==='hot'?'Normal idle is not claimed until verified. Use Show normal idle response.':'Swipe left or right to browse. Swipe down to return.';
  }
  function activate() {
    const s=state(); if(s.disabled)return;
    if(s.action==='start'){idle={page,phase:missing?'starting':'starting'};fallbackConfirmed=false;}
    else if(s.action==='stop'){page=idle.page;idle.phase='stopping';}
    else if(s.action==='fallback')fallbackConfirmed=true;
    else if(s.action==='confirmAbs')abs='confirm';
    else if(s.action==='disableAbs')abs='off';
    render();
  }
  function begin(e) {
    if(e.type==='keydown' && (![' ','Enter'].includes(e.key)||e.repeat))return;
    e.preventDefault(); cancelHold(); const s=state();if(s.disabled)return;
    press={action:s.action, hold:s.hold, committed:false, x:e.clientX, y:e.clientY};
    if(!s.hold)return;
    primary.classList.add('holding');face.classList.add('holding');
    $('prototype-status').textContent='Keep holding to confirm Park and parking brake set…';
    hold=setTimeout(()=>{hold=null;if(!press)return;press.committed=true;primary.classList.remove('holding');face.classList.remove('holding');activate();},900);
  }
  function end(e) {
    if(e.type==='keyup'&&![' ','Enter'].includes(e.key))return;
    e.preventDefault();const finished=press;cancelHold();
    if(!finished)return;
    if(finished.hold && !finished.committed){render();$('prototype-status').textContent='Cancelled. Release early never starts an action.';}
    else if(!finished.hold && state().action===finished.action)activate();
  }
  for(const el of [primary,face]) {
    el.addEventListener('pointerdown',e=>{if(e.button!==0)return;el.setPointerCapture(e.pointerId);begin(e);});
    el.addEventListener('pointerup',end);
    el.addEventListener('keydown',begin);el.addEventListener('keyup',end);
    el.addEventListener('pointercancel',()=>{cancelHold();render();});
    el.addEventListener('blur',()=>{cancelHold();render();});
    el.addEventListener('click',e=>e.preventDefault());
    el.addEventListener('pointermove',e=>{if(press && Math.hypot(e.clientX-press.x,e.clientY-press.y)>12){cancelHold();render();}});
  }
  function event(kind) {
    cancelHold();
    if(kind==='reset'){clearTimeout(doneTimer);page=0;branch=true;entry=0;idle=null;abs='ready';missing=false;fallbackConfirmed=false;cooldown=null;}
    else if(kind==='open'){branch=true;entry=0;page=idle?idle.page:0;}
    else if(kind==='up'&&!branch){if(!entry||performance.now()-firstSwipe>5000){firstSwipe=performance.now();entry=0;}entry++;if(entry===3){branch=true;entry=0;page=idle?idle.page:0;}}
    else if(kind==='down'){if(abs==='confirm')abs='ready';else{branch=false;entry=0;}fallbackConfirmed=false;}
    else if((kind==='prev'||kind==='next')&&branch){page=(page+(kind==='next'?1:2))%3;fallbackConfirmed=false;}
    else if(kind==='response'&&idle){if(idle.phase==='starting')idle.phase=missing?'fallback':'active';else if(['stopping','hot'].includes(idle.phase)){idle.phase='stopped';doneTimer=setTimeout(()=>{idle=null;render();},2000);}}
    else if(kind==='coolant'){missing=true;if(idle){idle.phase='fallback';page=idle.page;}}
    else if(kind==='heat'&&idle){idle.phase='hot';page=idle.page;}
    else if(kind==='loss'){if(idle){idle.phase='unknown';page=idle.page;}else cooldown='offline';}
    else if(kind==='recover'){if(idle?.phase==='unknown'){$('prototype-status').textContent='Connection restored, but the outcome still needs verified reconciliation. Unknown remains visible.';return;}cooldown=null;missing=false;}
    render();
  }
  for(const id of ['open','up','prev','next','down','response','coolant','heat','loss','recover','reset'])$('proto-'+id).onclick=()=>event(id);
  $('prototype-device').addEventListener('pointerdown',e=>{swipe={x:e.clientX,y:e.clientY};});
  $('prototype-device').addEventListener('pointerup',e=>{if(!swipe)return;const dx=e.clientX-swipe.x,dy=e.clientY-swipe.y;swipe=null;if(Math.max(Math.abs(dx),Math.abs(dy))<40)return;cancelHold();event(Math.abs(dx)>Math.abs(dy)?(dx<0?'next':'prev'):(dy<0?'up':'down'));});
  document.addEventListener('visibilitychange',()=>{cancelHold();});
  render();
})();
