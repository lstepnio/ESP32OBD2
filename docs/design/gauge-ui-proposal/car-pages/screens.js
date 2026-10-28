/* Review-only UI. No Bluetooth, device commands, configuration writes or live data. */
const paths = {
  back:'M15 18l-6-6 6-6', next:'M9 5l7 7-7 7', down:'M6 9l6 6 6-6',
  check:'M5 12l4 4L19 6', more:'M5 12h.01M12 12h.01M19 12h.01',
  car:'M5 17H3V9l2-5h14l2 5v8h-2M5 17v3m14-3v3M3 10h18M7 14h.01M17 14h.01M5 17h14',
  gauge:'M4 17a9 9 0 1 1 16 0M12 12l5-5M7 18h10',
  settings:'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8M12 2v3M12 19v3M2 12h3M19 12h3M5 5l2 2M17 17l2 2M5 19l2-2M17 7l2-2',
  bell:'M6 9a6 6 0 0 1 12 0v6l2 3H4l2-3V9M10 21h4',
  history:'M4 5v5h5M4 10a8 8 0 1 1 1 8M12 7v5l3 2',
  warning:'M12 3L2 21h20L12 3zM12 9v5M12 18h.01',
  engine:'M7 6h10v3h4v9H5v-3H2V8h3V6h2M9 3h6M12 3v3',
  temp:'M10 14V5a2 2 0 0 1 4 0v9a4 4 0 1 1-4 0M12 10v8M17 6h3M17 10h3',
  bolt:'M13 2L4 14h7l-1 8 10-13h-7l1-7z',
  link:'M10 13a5 5 0 0 0 7 0l3-3a5 5 0 0 0-7-7l-2 2M14 11a5 5 0 0 0-7 0l-3 3a5 5 0 0 0 7 7l2-2',
  phone:'M7 2h10v20H7zM11 18h2',
  search:'M16 16l5 5M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0',
  plus:'M12 5v14M5 12h14', eye:'M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12zM15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0',
  wifi:'M3 8a14 14 0 0 1 18 0M6 12a9 9 0 0 1 12 0M9 16a4 4 0 0 1 6 0M12 20h.01',
  battery:'M3 6h16v12H3zM22 10v4M6 9h10v6H6z',
};
const demoState = {
  currentRule: 'app-rule',
  connection: ['app-offline','app-stale'].includes(location.hash.slice(1)) ? 'Vehicle offline' : location.hash.slice(1)==='app-partial' ? 'Vehicle partly connected' : 'Connected',
  rules: {
    'app-rule': {name:'Coolant temperature',unit:'°F',limit:230,direction:'Above',enabled:true,delay:3,reset:225},
    'app-rule-tcm': {name:'Transmission temperature',unit:'°F',limit:220,direction:'Above',enabled:true,delay:3,reset:215},
    'app-rule-battery': {name:'Battery voltage',unit:'V',limit:12,direction:'Below',enabled:true,delay:3,reset:12.3},
    'app-rule-rpm': {name:'Engine speed',unit:'RPM',limit:4000,direction:'Above',enabled:true,delay:3,reset:3900},
    'app-rule-intake': {name:'Intake air temperature',unit:'°F',limit:130,direction:'Above',enabled:true,delay:3,reset:125},
    'app-rule-oil': {name:'Oil pressure',unit:'PSI',limit:10,direction:'Below',enabled:true,delay:3,reset:12},
  },
  drafts: {},
  activeRules: ['app-rule','app-rule-tcm','app-rule-battery'],
  hidden: [
    {code:'U0121',controller:'Transmission',meaning:'ABS communication'},
    {code:'U0140',controller:'Transmission',meaning:'Body control communication'},
    {code:'P1DCA',controller:'Transmission',meaning:''},
    {code:'P1DF3',controller:'Transmission',meaning:''},
  ],
  selectedHidden: {code:'U0140',controller:'Transmission'},
  lastHidden: null,
};
const hiddenCode = (code,controller) => demoState.hidden.some(rule=>rule.code===code&&rule.controller===controller);
const icon = (name, cls='') => '<svg class="'+cls+'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="'+paths[name]+'"/></svg>';
const go = (id) => ' data-go="'+id+'"';
const button = (text,id,kind='primary') => '<button class="'+kind+'"'+go(id)+'>'+text+'</button>';
const row = (title,sub,id,symbol='next',end='') => '<button class="row"'+go(id)+'>'+ (symbol!=='next'?icon(symbol,'row-icon'):'')+'<span class="row-copy"><span class="row-title">'+title+'</span>'+(sub?'<span class="row-sub">'+sub+'</span>':'')+'</span>'+(end?'<span class="row-end">'+end+'</span>':'')+icon('next','chevron')+'</button>';
const switchRow = (title,sub,on=true) => '<button class="row" role="switch" aria-checked="'+on+'" data-toggle><span class="row-copy"><span class="row-title">'+title+'</span>'+(sub?'<span class="row-sub">'+sub+'</span>':'')+'</span><span class="switch'+(on?'':' off')+'"></span></button>';
const issue = (title,meta,id,symbol='engine',value='',critical=false) => '<button class="issue'+(critical?' critical':'')+'"'+go(id)+'><span class="symbol">'+icon(symbol)+'</span><span class="issue-text"><span class="issue-title">'+title+'</span><span class="issue-meta">'+meta+'</span>'+(value?'<span class="issue-value">'+value+'</span>':'')+'</span>'+icon('next','chevron')+'</button>';
const faultIssue = () => hiddenCode('P0300','Engine') ? '' : issue('Engine misfire','P0300 · Engine','app-fault');
const hotIssue = () => issue('Coolant above your limit','248 °F · Limit 230 °F','app-threshold','temp');
const utilities = () => '<div class="card flat">'+row('Vehicle actions','Air up, Winch','app-actions','bolt')+row('Alert settings','','app-settings','bell')+row('History','','app-history','history')+'</div>';
const quiet = () => '<div class="card quiet-state"><div class="state-title">'+icon('check')+'No active alerts</div><p class="tiny">Checked just now</p></div>';
const tabs = (history=false) => '<div class="segmented"><button'+go('app-alerts')+' class="'+(!history?'selected':'')+'">Current</button><button'+go('app-history')+' class="'+(history?'selected':'')+'">History</button></div>';
const section = (title,body,extra='') => '<div class="section-heading"><h2>'+title+'</h2>'+extra+'</div><div class="card flat">'+body+'</div>';
const content = (html) => '<main class="content">'+html+'</main>';
const footer = (html) => '<div class="footeraction">'+html+'</div>';
const base = (title,body,{home=false,pill=demoState.connection,quietPill=false,back='app-attention',end='',nav=false,vehicle='Jeep Wrangler'}={}) =>
  '<div class="statusbar"><span>9:41</span><span class="status-icons">'+icon('wifi')+icon('battery')+'</span></div>'+
  '<header class="top"><div class="titlebar'+(home?'':' sub')+'">'+(!home?'<button class="iconbutton back"'+go(back)+' aria-label="Back">'+icon('back')+'</button>':'')+
  '<h1>'+title+'</h1>'+(end|| (home?'<button class="iconbutton"'+go('app-car-setup')+' aria-label="Car settings">'+icon('settings')+'</button>':''))+'</div>'+
  (home?'<button class="vehicle-select"'+go('app-vehicles')+'>' +vehicle+icon('down')+'</button>':'')+
  (pill?'<button class="connection'+(quietPill||pill!=='Connected'?' quiet':'')+'"'+go('app-connection')+'>'+icon(quietPill||pill!=='Connected'?'link':'check')+pill+'</button>':'')+'</header>'+body+
  (nav?'<nav class="bottomnav" aria-label="Main navigation"><div class="navitem"><span>'+icon('gauge')+'</span><span>Gauge</span></div><button class="navitem active"'+go('app-attention')+'><span>'+icon('car')+'</span><span>Car</span></button><div class="navitem"><span>'+icon('settings')+'</span><span>Settings</span></div></nav><div class="gesturebar"></div>':'');
const home = (body,opts={})=>base('Car',content(body),{home:true,nav:true,...opts});
const note = (text) => '<p class="help">'+text+'</p>';
const entries = {
  'app-quiet': () => home(quiet()+utilities()),
  'app-filtered': () => home(quiet()+utilities()),
  'app-attention': () => home(section('Needs attention',hotIssue()+faultIssue())+utilities()),
  'app-mixed-hidden': () => home(section('Needs attention',hotIssue())+utilities()),
  'app-startup': () => home(utilities(),{pill:'Connecting to vehicle',quietPill:true}),
  'app-offline': () => home('<div class="section-heading"><h2>Last drive</h2></div><div class="card flat">'+row('2 alerts recorded','Today, 10:42 AM','app-history','history')+'</div>'+utilities(),{pill:'Vehicle offline',quietPill:true}),
  'app-partial': () => home(section('Needs attention',faultIssue())+utilities(),{pill:'Vehicle partly connected',quietPill:true}),
  'app-alerts': () => base('Alerts',content(tabs()+(demoState.connection==='Vehicle offline' ? '<div class="card"><p>Previous alerts are in History.</p>'+button('View history','app-history','secondary')+'</div>' : section('Needs attention',hotIssue()+faultIssue()))),{back:'app-attention'}),
  'app-fault': () => base('Engine misfire',content('<div class="eyebrow">P0300 · Engine</div><div class="badgeline">'+icon('engine')+'Code reported by your car</div><p>The car detected uneven combustion in multiple or unidentified cylinders.</p><div class="card"><h2>What to do</h2><p>Arrange a diagnosis if this returns or the engine runs unevenly. This code identifies a condition, not a failed part.</p></div><hr class="divider"><div class="metric-line"><span class="muted">First recorded</span><span>Today, 10:42 AM</span></div><div class="metric-line"><span class="muted">Last checked</span><span>Just now</span></div>'), {end:'<button class="iconbutton"'+go('app-report')+' aria-label="Share fault report">'+icon('more')+'</button>'}),
  'app-unknown': () => base('Vehicle fault',content('<div class="eyebrow">P1ABC · Transmission</div><div class="badgeline">'+icon('engine')+'Code reported by your car</div><p>A description is not available for this vehicle.</p><div class="card"><h2>Keep the code with its context</h2><p>A diagnostic report includes the code, vehicle and the time it was recorded.</p>'+button('Share report','app-report','secondary')+'</div>'),{back:'app-alerts'}),
  'app-threshold': () => base('Coolant temperature',content('<div class="badgeline">'+icon('temp')+'Above your limit</div><div class="factgrid"><div><strong>248 °F</strong><span>At alert · 10:42 AM</span></div><div><strong>230 °F</strong><span>Your high limit</span></div></div><div class="card"><h2>At the time</h2><div class="metric-line"><span>Engine speed</span><strong>675 RPM</strong></div><div class="metric-line"><span>Vehicle speed</span><strong>0 MPH</strong></div></div>'+row('Edit coolant alert',demoState.rules['app-rule'].direction+' '+demoState.rules['app-rule'].limit+' °F','app-rule')), {back:'app-attention'})+footer('<div class="pair">'+button('Got it','app-acknowledged','secondary')+button('Snooze 5 min','app-snoozed','secondary')+'</div>'),
  'app-stale': () => base('Coolant temperature',content('<div class="eyebrow">Last recorded · Today, 10:42 AM</div><div class="factgrid"><div><strong>248 °F</strong><span>At alert</span></div><div><strong>230 °F</strong><span>High limit</span></div></div><div class="card"><p style="margin:0">Current temperature is unavailable.</p></div>'+row('Edit coolant alert',demoState.rules['app-rule'].direction+' '+demoState.rules['app-rule'].limit+' °F','app-rule')),{pill:'Vehicle offline',quietPill:true,back:'app-history'}),
  'app-history': () => base('Alerts',content(tabs(true)+'<div class="day">Today</div><div class="card flat">'+issue('Coolant above your limit','10:42 AM · Last recorded','app-stale','temp')+(hiddenCode('P0300','Engine')?'':issue('Engine misfire','10:42 AM · P0300 · Engine','app-fault'))+'</div><div class="day">Yesterday</div><div class="card flat">'+issue('Battery below your limit','6:18 PM · Returned to normal','app-voltage','bolt')+'</div>'),{end:'<button class="iconbutton"'+go('app-report')+' aria-label="History options">'+icon('more')+'</button>'}),
  'app-history-empty': () => base('Alerts',content(tabs(true)+'<div class="card quiet-state"><h2>No recorded alerts</h2><p>New alerts will appear here.</p></div>'),{}),
  'app-settings': () => base('Alert settings',content(settingsContent()),{}),
  'app-rule': () => ruleEditor('app-rule'),
  'app-reading-picker': () => base('Choose a reading',content('<label class="search">'+icon('search')+'<input placeholder="Search readings" aria-label="Search readings" data-search></label><div class="card flat" id="reading-choices">'+row('Coolant temperature','','app-rule')+row('Transmission temperature','','app-rule-tcm')+row('Battery voltage','','app-rule-battery')+row('Engine speed','','app-rule-rpm')+row('Intake air temperature','','app-rule-intake')+row('Oil pressure','','app-rule-oil')+'</div>'),{back:'app-settings'}),
  'app-hidden': () => base('Hidden fault codes',content(note('Hidden from eGauge screens, notifications and reports. Your car keeps the codes.')+'<div class="card flat">'+demoState.hidden.map(rule=>row(rule.code,rule.controller+(rule.meaning?' · '+rule.meaning:''),'app-unhide')).join('')+'</div>'+button('Hide a code','app-hide-form','secondary')+'<div class="scope">Applied to Cabin gauge</div>'),{back:'app-advanced'}),
  'app-hidden-pending': () => base('Hidden fault codes',content(note('Your changes are saved on this phone. Send them to each gauge to apply them there.')+'<div class="card flat">'+row('U0121','Transmission','app-unhide')+row('U0140','Transmission','app-unhide')+row('P1DCA','Transmission','app-unhide')+row('P1DF3','Transmission','app-unhide')+'</div><div class="section-heading"><h2>Assigned gauges</h2></div><div class="card flat">'+'<div class="row"><span class="row-copy"><span class="row-title">Cabin gauge</span><span class="row-sub">Applied</span></span></div><div class="row"><span class="row-copy"><span class="row-title">Spare gauge</span><span class="row-sub">Offline · Change waiting</span></span></div>'+'</div>'+note('An offline gauge keeps its last confirmed setup.')), {back:'app-advanced'}),
  'app-hide-form': () => base('Hide a fault code',content('<label class="formfield"><span>Code</span><input class="field" value="P0300" maxlength="5" aria-label="Fault code"></label><label class="formfield"><span>Reported by</span><select class="field" aria-label="Controller"><option>Engine</option><option>Transmission</option></select></label>'+note('Applies to Jeep Wrangler only. Matching codes disappear from eGauge, including History and reports. This does not clear the car’s code.')), {back:'app-hidden'})+footer(button('Hide and send to gauge','app-hide-result')),
  'app-hide-result': () => home(section('Needs attention',hotIssue()+faultIssue())+utilities()+'<div class="snackbar"><span>Code hidden in eGauge</span><button data-undo'+go('app-attention')+'>Undo</button></div>'),
  'app-vehicles': () => base('Your cars',content('<div class="card flat">'+row('Jeep Wrangler','Cabin gauge · Selected','app-attention','car')+row('Volvo C30','No gauge assigned','app-other-car','car')+row('Lexus GX470','Spare gauge','app-other-car','car')+'</div>'+button('Add car','app-add-car','secondary')),{back:'app-attention'}),
  'app-car-setup': () => base('Car settings',content('<div class="card flat">'+row('Name','Jeep Wrangler','app-name')+row('Gauge','Cabin gauge','app-gauges')+row('Adapter','Connected','app-adapter')+'</div><div class="card flat">'+row('Advanced','','app-advanced')+'</div><button class="textbutton danger"'+go('app-delete-car')+'>Delete car</button>'),{}),
  'app-advanced': () => base('Advanced',content('<div class="card flat">'+row('Hidden fault codes','','app-hidden')+row('Second adapter','Transmission · Connected','app-adapter-child')+'</div>'),{back:'app-car-setup'}),
  'app-connection': () => base('Connections',content('<div class="card flat">'+row('Cabin gauge',demoState.connection==='Gauge offline'?'Offline':'Connected','app-attention','gauge')+row('Vehicle',demoState.connection==='Vehicle offline'?'Offline':'Engine connected','app-attention','car')+row('Transmission adapter',demoState.connection==='Connected'?'Connected':'Reconnecting automatically','app-partial','link')+'</div>'),{pill:'',back:'app-attention'}),
  'app-notification': () => base('Phone notification',content('<div class="notification"><div class="notice-brand">'+icon('gauge')+'eGauge · Jeep Wrangler · now</div><div class="notice-title">Coolant above your limit</div><div class="noticebody">248 °F at alert · Limit 230 °F</div><button class="textbutton"'+go('app-threshold')+'>View alert</button></div>'+note('Tap the notification to open this alert directly.')),{pill:'',back:'app-attention'}),
  'app-voltage': () => base('Battery voltage',content('<div class="eyebrow">Yesterday, 6:18 PM</div><h2>Returned to normal</h2><div class="factgrid"><div><strong>11.8 V</strong><span>At alert</span></div><div><strong>12.0 V</strong><span>Low limit</span></div></div>'+row('Edit battery alert','Below 12.0 V','app-rule-battery')),{back:'app-history'}),
  'app-acknowledged': () => base('Coolant temperature',content('<div class="badgeline">'+icon('temp')+'Above your limit</div><div class="factgrid"><div><strong>248 °F</strong><span>At alert · 10:42 AM</span></div><div><strong>230 °F</strong><span>Your high limit</span></div></div><div class="snackbar"><span>Marked as seen</span></div>'+note('The alert stays active until a fresh reading returns within your limit.')+button('Back to Car','app-attention','secondary'))),
  'app-snoozed': () => base('Coolant temperature',content('<div class="badgeline">'+icon('temp')+'Above your limit</div><div class="factgrid"><div><strong>248 °F</strong><span>At alert · 10:42 AM</span></div><div><strong>230 °F</strong><span>Your high limit</span></div></div><div class="snackbar"><span>Snoozed for 5 minutes</span></div>'+button('Back to Car','app-attention','secondary'))),
  'app-rule-saved': () => base('Alert settings',content('<div class="snackbar"><span>Saved on gauge</span></div>'+settingsContent())),
  'app-rule-behavior': () => behaviorEditor(),
  'app-actions': () => base('Vehicle actions',content('<div class="card flat">'+row('Air up','5 minutes','app-action-info','bolt')+row('Winch','1 minute','app-action-info','bolt')+row('ABS / traction control','Not available for this setup','app-action-info','bolt')+'</div>'+note('The existing approved action controls and safeguards remain unchanged by this proposal.'))),
  'app-action-info': () => base('Vehicle actions',content('<div class="card"><p>Start and stop actions on the gauge. Configure durations through the existing action editor.</p></div>'),{back:'app-actions'}),
  'app-report': () => base('Share report',content('<div class="card"><h2>Jeep Wrangler</h2><p>Visible alerts and their recorded context.</p></div>'+note('The Android share sheet opens after the report is prepared.')), {back:'app-history'})+footer(button('Share','app-history')),
  'app-unhide': () => base('Show a code again',content('<div class="card"><h2>'+demoState.selectedHidden.code+' · '+demoState.selectedHidden.controller+'</h2><p>Show matching faults and their history again. Previous alerts will not send new notifications.</p></div>'),{back:'app-hidden'})+footer(button('Show and send to gauge','app-hidden')),
  'app-adapter': () => base('Vehicle adapter',content('<div class="card flat">'+row('Primary adapter','Vgate · Connected','app-car-setup','link')+'</div>'+button('Change adapter','app-adapter-search','secondary')),{back:'app-car-setup'}),
  'app-adapter-child': () => base('Second adapter',content('<div class="card"><h2>Transmission adapter</h2><p>Supplies extra readings for this car. Engine and transmission appear together throughout eGauge.</p></div><div class="card flat">'+row('Adapter','Vgate · Connected','app-adapter-search')+'</div>'),{back:'app-advanced'}),
  'app-adapter-search': () => base('Choose adapter',content('<div class="card flat">'+row('Vgate','Nearby','app-adapter')+'</div>'),{pill:'Searching nearby',quietPill:true,back:'app-adapter'}),
  'app-name': () => base('Car name',content('<label class="formfield"><span>Name</span><input class="field" value="Jeep Wrangler"></label>'),{back:'app-car-setup'})+footer(button('Save','app-car-setup')),
  'app-add-car': () => base('Add car',content('<label class="formfield"><span>Car name</span><input class="field" placeholder="Name your car"></label>'),{back:'app-vehicles'})+footer(button('Add car','app-vehicles')),
  'app-gauges': () => base('Assigned gauges',content('<div class="card flat">'+row('Cabin gauge','Connected · Current','app-car-setup','gauge')+row('Spare gauge','Offline','app-hidden-pending','gauge')+'</div>'),{back:'app-car-setup'}),
  'app-other-car': () => base('Car',content('<div class="card"><h2>Volvo C30</h2><p>No gauge is assigned.</p>'+button('Assign a gauge','app-gauges')+'</div>'),{home:true,pill:'Gauge not assigned',quietPill:true,nav:true,vehicle:'Volvo C30'}),
  'app-delete-car': () => base('Delete car?',content('<div class="card"><p>Remove Jeep Wrangler from this phone? Review the existing assignment and saved-data confirmation before deleting.</p></div>'),{back:'app-car-setup'})+footer(button('Keep car','app-car-setup','secondary')),
};
function settingsContent(){
  return '<div class="section-heading"><h2>Reading limits</h2></div><div class="card flat">'+demoState.activeRules.map(id=>{const r=demoState.rules[id];return row(r.name,r.enabled?r.direction+' '+r.limit+' '+r.unit:'Off',id,r.unit==='V'?'bolt':'temp')}).join('')+'</div>'+button('Add alert','app-reading-picker','secondary')+'<div class="section-heading" style="margin-top:28px"><h2>On this phone</h2></div><div class="card flat">'+switchRow('Notifications','Alerts from your connected gauge')+'</div>';
}
function ruleDraft(id){return demoState.drafts[id]??(demoState.drafts[id]={...demoState.rules[id]})}
function ruleEditor(id){
  demoState.currentRule=id;
  const r=ruleDraft(id);
  return base(r.name+' alert',content('<div class="card flat">'+switchRow('Alert enabled','',r.enabled)+'</div><label class="formfield"><span>Warn when reading is</span><select class="field" data-rule-field="direction"><option'+(r.direction==='Above'?' selected':'')+'>Above</option><option'+(r.direction==='Below'?' selected':'')+'>Below</option></select></label><label class="formfield"><span>Limit</span><div class="fieldwrap"><input class="field" type="number" required step="any" data-rule-field="limit" value="'+r.limit+'" aria-label="Alert limit"><span class="fieldunit">'+r.unit+'</span></div></label><div class="card flat">'+row('Alert behavior','Timing and reset','app-rule-behavior')+'</div>'),{back:'app-settings'})+footer(button('Save to gauge','app-rule-saved'));
}
function behaviorEditor(){
  const r=ruleDraft(demoState.currentRule);
  return base('Alert behavior',content('<label class="formfield"><span>Wait before alerting</span><div class="fieldwrap"><input class="field" value="'+r.delay+'" type="number" min="0" data-rule-field="delay"><span class="fieldunit">sec</span></div></label><label class="formfield"><span>Reset '+(r.direction==='Above'?'below':'above')+'</span><div class="fieldwrap"><input class="field" value="'+r.reset+'" type="number" step="any" data-rule-field="reset"><span class="fieldunit">'+r.unit+'</span></div></label>'+note('These values stay unchanged unless you edit them.')),{back:demoState.currentRule})+footer(button('Done',demoState.currentRule));
}
for(const id of Object.keys(demoState.rules))entries[id]=()=>ruleEditor(id);
entries['app-light']=entries['app-attention'];
entries['app-large-text']=entries['app-threshold'];
entries['app-large-editor']=()=>ruleEditor('app-rule');
entries['app-gauge-filtering']=()=>{
  const face=(lamp)=>'<svg viewBox="0 0 240 240" role="img" aria-label="'+(lamp?'RPM with one visible fault':'RPM with all reported codes hidden')+'" style="display:block;width:240px;height:240px;margin:12px auto"><circle cx="120" cy="120" r="120" fill="#05080a"/>'+(lamp?'<path d="M112 24h16v4h5v10h-23v-4h-4V27h6zM116 21h8" fill="none" stroke="#ff8a00" stroke-width="2"/>':'')+'<text x="120" y="85" fill="#9caab2" text-anchor="middle" font-size="18">RPM</text><text x="120" y="150" fill="#e4eaed" text-anchor="middle" font-size="60" font-weight="600">675</text><circle cx="112" cy="214" r="2" fill="#e4eaed"/><circle cx="124" cy="214" r="2" fill="#1b2a30"/></svg>';
  return base('Gauge filtering',content('<div class="smallcaps">All reported codes hidden</div>'+face(false)+'<div class="smallcaps" style="margin-top:22px">One unfiltered code with active MIL</div>'+face(true)),{pill:'',back:'app-hidden'});
};
const root=document.getElementById('phone');
const navigation=[];
const scrollPositions=new Map();
function render(){
  let id=location.hash.slice(1)||'app-attention';
  if(!entries[id])id='app-attention';
  if(id==='app-fault'&&hiddenCode('P0300','Engine'))id='app-alerts';
  document.body.classList.toggle('light',id==='app-light');
  document.body.classList.toggle('large-type',id==='app-large-text'||id==='app-large-editor');
  root.innerHTML=entries[id]();
  root.dataset.screen=id;
  const scroller=root.querySelector('.content');
  if(scroller)scroller.scrollTop=scrollPositions.get(id)||0;
  parent.postMessage({type:'car-prototype-screen',id},location.origin);
}
document.addEventListener('click',e=>{
  const target=e.target.closest('[data-go]');
  if(target){
    const current=root.dataset.screen;
    const next=target.dataset.go;
    if(next==='app-connection')demoState.connection=target.textContent.trim();
    if((demoState.rules[current]||current==='app-large-editor')&&next==='app-rule-saved'){
      const input=root.querySelector('input[type=number]');
      if(!input.reportValidity()||!input.value)return;
      const id=demoState.currentRule;demoState.rules[id]={...ruleDraft(id)};
      if(!demoState.activeRules.includes(id))demoState.activeRules.push(id);
    }
    if(current==='app-hide-form'&&next==='app-hide-result'){
      const input=root.querySelector('input');
      const code=input.value.trim().toUpperCase();
      const controller=root.querySelector('select').value;
      if(!/^[PCBU][0-3][0-9A-F]{3}$/.test(code)||code==='P0000'){
        input.setCustomValidity('Enter an exact five-character fault code.');
        input.reportValidity();return;
      }
      if(hiddenCode(code,controller)||demoState.hidden.length>=16){
        input.setCustomValidity('This code is already hidden, or all 16 slots are used.');
        input.reportValidity();return;
      }
      demoState.lastHidden={code,controller,meaning:''};
      demoState.hidden.push(demoState.lastHidden);
    }
    if((current==='app-hidden'||current==='app-hidden-pending')&&next==='app-unhide'){
      const code=target.querySelector('.row-title').textContent;
      const controller=target.querySelector('.row-sub').textContent.split(' · ')[0];
      demoState.selectedHidden={code,controller};
    }
    if((current==='app-unhide'&&next==='app-hidden'&&!target.classList.contains('back'))||target.hasAttribute('data-undo')){
      const selected=target.hasAttribute('data-undo')?demoState.lastHidden:demoState.selectedHidden;
      demoState.hidden=demoState.hidden.filter(rule=>rule.code!==selected?.code||rule.controller!==selected?.controller);
    }
    scrollPositions.set(current,root.querySelector('.content')?.scrollTop||0);
    if(target.classList.contains('back')&&navigation.length){location.hash=navigation.pop()}
    else{navigation.push(current);location.hash=next}
    return;
  }
  const toggle=e.target.closest('[data-toggle]');
  if(toggle){const next=toggle.getAttribute('aria-checked')!=='true';if(toggle.textContent.includes('Alert enabled'))ruleDraft(demoState.currentRule).enabled=next;toggle.setAttribute('aria-checked',String(next));toggle.querySelector('.switch').classList.toggle('off',!next)}
});
document.addEventListener('input',e=>{
  if(e.target.tagName==='INPUT')e.target.setCustomValidity('');
  if(e.target.dataset.ruleField)ruleDraft(demoState.currentRule)[e.target.dataset.ruleField]=Number(e.target.value);
  if(e.target.matches('[data-search]')){
    const query=e.target.value.toLowerCase();
    document.querySelectorAll('#reading-choices .row').forEach(el=>{el.hidden=!el.textContent.toLowerCase().includes(query)});
  }
});
document.addEventListener('change',e=>{if(e.target.dataset.ruleField==='direction')ruleDraft(demoState.currentRule).direction=e.target.value});
addEventListener('hashchange',render);
addEventListener('keydown',e=>{if(e.key==='Escape'){const back=root.querySelector('.back');if(back)back.click()}});
render();
