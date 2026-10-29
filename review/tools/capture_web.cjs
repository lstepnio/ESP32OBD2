async (page) => {
  const root = '/Users/lukasz.stepniowski/.codex/worktrees/idle-coolant-guard/ESP32OBD2/review/screens/web';
  const results = {viewports:[], layoutChoices:[], console:[], requests:[]};
  page.on('pageerror', e => results.console.push({type:'pageerror', text:e.message}));
  page.on('console', e => {if(['error','warning'].includes(e.type())) results.console.push({type:e.type(),text:e.text()});});
  page.on('response', r => {if(r.status()>=400) results.requests.push({url:r.url(),status:r.status()});});
  for (const width of [375,768,1440]) {
    const height = width===1440 ? 1000 : 1024;
    await page.setViewportSize({width,height});
    await page.goto('http://127.0.0.1:8772/');
    for (const img of await page.locator('img').all()) { await img.scrollIntoViewIfNeeded(); await img.evaluate(el => el.decode()); }
    await page.evaluate(()=>window.scrollTo(0,0));
    await page.screenshot({path:`${root}/web_home_light_${width}_before.png`,fullPage:true});
    const stats=await page.evaluate(()=>({scrollWidth:document.documentElement.scrollWidth,clientWidth:document.documentElement.clientWidth,images:[...document.images].filter(i=>!i.complete||i.naturalWidth===0).map(i=>i.alt),headings:[...document.querySelectorAll('h1,h2,h3,h4')].map(h=>({level:h.tagName,text:h.textContent.trim()})), smallTargets:[...document.querySelectorAll('a,button,summary')].filter(e=>{let r=e.getBoundingClientRect();return r.width&&r.height&&(r.height<24||r.width<24)}).map(e=>({text:e.textContent.trim(),height:e.getBoundingClientRect().height,width:e.getBoundingClientRect().width}))}));
    results.viewports.push({page:'home',width,height,...stats});
    for (const id of ['app','offroad-actions','hardware','custom-projects','availability']) {
      const section=page.locator(`#${id}`);
      await section.screenshot({path:`${root}/web_home_${id}_light_${width}_before.png`});
    }
    await page.goto('http://127.0.0.1:8772/recovery/');
    await page.screenshot({path:`${root}/web_recovery_light_${width}_before.png`,fullPage:true});
    results.viewports.push({page:'recovery',width,height,...await page.evaluate(()=>({scrollWidth:document.documentElement.scrollWidth,clientWidth:document.documentElement.clientWidth,images:[...document.images].filter(i=>!i.complete||!i.naturalWidth).map(i=>i.alt),firmwareChoices:[...document.querySelectorAll('#release-choice option')].map(e=>e.textContent),apkChoices:[...document.querySelectorAll('#apk-choice option')].map(e=>e.textContent)}))});
  }
  await page.setViewportSize({width:1440,height:1000});
  await page.goto('http://127.0.0.1:8772/');
  for (const name of ['Numeric','Arc','Bar','Trend','Dual','Alerts']) {
    await page.getByRole('button',{name,exact:true}).click();
    results.layoutChoices.push({name,pressed:await page.getByRole('button',{name,exact:true}).getAttribute('aria-pressed'),caption:await page.locator('#screen-caption').innerText(),imageAlt:await page.locator('#screen-image').getAttribute('alt')});
  }
  return results;
}
