async(page)=>{
 const out={entry:[],recoveryMissingFeed:null};
 for(const width of [375,768,1440]){
  await page.setViewportSize({width,height:1000});await page.goto('http://127.0.0.1:8772/');
  out.entry.push({page:'home',width,...await page.evaluate(()=>({recoveryLinks:[...document.querySelectorAll('a[href="/recovery/"]')].filter(e=>{let r=e.getBoundingClientRect();return r.width&&r.height}).map(e=>({text:e.textContent.trim(),pageY:Math.round(e.getBoundingClientRect().top+scrollY)})),totalHeight:document.documentElement.scrollHeight}))});
  await page.goto('http://127.0.0.1:8772/recovery/');
  out.entry.push({page:'recovery',width,...await page.evaluate(()=>({androidSectionY:Math.round(document.querySelector('#android').getBoundingClientRect().top+scrollY),totalHeight:document.documentElement.scrollHeight}))});
 }
 await page.route('**/downloads/releases.json',r=>r.fulfill({status:503,body:'Synthetic unavailable'}));
 await page.setViewportSize({width:375,height:1024});await page.goto('http://127.0.0.1:8772/recovery/');
 await page.locator('#release-status').getByText('could not',{exact:false}).waitFor().catch(()=>{});
 out.recoveryMissingFeed=await page.evaluate(()=>({firmwareStatus:document.querySelector('#release-status').textContent,apkStatus:document.querySelector('#apk-status').textContent,choices:[...document.querySelectorAll('#release-choice option')].map(x=>x.textContent),firmwareInstallEnabled:!document.querySelector('#install').disabled}));
 await page.screenshot({path:'/Users/lukasz.stepniowski/.codex/worktrees/idle-coolant-guard/ESP32OBD2/review/screens/web/web_recovery_feed-error_light_375_before.png',fullPage:true});
 await page.unroute('**/downloads/releases.json');return out;
}
