async (page) => {
 const out='/Users/lukasz.stepniowski/.codex/worktrees/idle-coolant-guard/ESP32OBD2/review/screens/web';
 const results={contrast:[],keyboard:[],forms:[],reducedMotion:null};
 await page.goto('http://127.0.0.1:8772/');
 results.contrast=await page.evaluate(()=>{
   const rgb=s=>{const x=s.match(/[\d.]+/g)?.map(Number);return x&&x.length>=3?[...x.slice(0,3),x[3]??1]:null};
   const blend=(a,b)=>a.slice(0,3).map((v,i)=>v*a[3]+b[i]*(1-a[3]));
   const bg=e=>{const chain=[];for(let n=e;n;n=n.parentElement){let s=getComputedStyle(n);if(s.backgroundImage!=='none')return null;chain.unshift(rgb(s.backgroundColor));}let c=[255,255,255];for(const v of chain)if(v)c=blend(v,c);return c};
   const lum=c=>{let v=c.map(x=>{x/=255;return x<=0.04045?x/12.92:((x+.055)/1.055)**2.4});return .2126*v[0]+.7152*v[1]+.0722*v[2]};
   const records=[];
   for(const e of document.querySelectorAll('p,a,button,label,summary,h1,h2,h3,h4,figcaption,span')){
      const text=[...e.childNodes].filter(n=>n.nodeType===Node.TEXT_NODE).map(n=>n.textContent).join('').trim();
      const r=e.getBoundingClientRect(),s=getComputedStyle(e),background=bg(e),foreground=rgb(s.color);
      if(!text||!r.width||!r.height||!background||!foreground||e.disabled||e.closest('[aria-hidden="true"]')||e.closest('.brand'))continue;
      const c=blend(foreground,background),l=[lum(c),lum(background)].sort((a,b)=>b-a),ratio=(l[0]+.05)/(l[1]+.05);
      const large=parseFloat(s.fontSize)>=24||(parseFloat(s.fontSize)>=18.666&&parseInt(s.fontWeight)>=700);
      records.push({text:text.slice(0,110),tag:e.tagName,class:e.className,fontSize:s.fontSize,fontWeight:s.fontWeight,color:s.color,background,ratio:+ratio.toFixed(2),threshold:large?3:4.5,pass:ratio>=(large?3:4.5)});
   }
   return records;
 });
 await page.evaluate(()=>{document.activeElement?.blur();window.scrollTo(0,0);});
 for(let i=0;i<35;i++){
   await page.keyboard.press('Tab');
   results.keyboard.push(await page.evaluate(()=>{let e=document.activeElement,r=e.getBoundingClientRect(),s=getComputedStyle(e);return {tag:e.tagName,text:(e.getAttribute('aria-label')||e.textContent||e.name||'').trim().slice(0,65),top:r.top,bottom:r.bottom,viewport:innerHeight,outlineStyle:s.outlineStyle,outlineWidth:s.outlineWidth,visible:r.width>0&&r.height>0&&r.bottom>0&&r.top<innerHeight}}));
 }
 await page.screenshot({path:`${out}/web_keyboard_focus_light_1440_before.png`});
 await page.emulateMedia({reducedMotion:'reduce'});
 results.reducedMotion=await page.evaluate(()=>({scrollBehavior:getComputedStyle(document.documentElement).scrollBehavior,buttonTransition:getComputedStyle(document.querySelector('button')).transitionDuration}));
 await page.emulateMedia({reducedMotion:'no-preference'});
 for(const width of [375,1440]){
   await page.setViewportSize({width,height:1000});
   for(const name of ['interest','custom']){
     for(const mode of ['success','error']){
       await page.goto('http://127.0.0.1:8772/');
       await page.route(`**/api/${name}`,async route=>{if(mode==='success')await route.fulfill({status:200,contentType:'application/json',body:'{"ok":true}'});else await route.fulfill({status:503,contentType:'application/json',body:'{"error":"Synthetic unavailable"}'});});
       const form=page.locator(`#${name}-form`);
       await form.getByRole('textbox',{name:name==='interest'?'Get project updates':'Email',exact:true}).fill('review@example.invalid');
       if(name==='custom')await form.getByRole('textbox',{name:'What are you building?',exact:true}).fill('Synthetic website-review request. Not submitted to the real service.');
       await form.getByRole('button',{name:name==='interest'?'Keep me posted':'Send project request',exact:true}).click();
       const status=page.locator(name==='interest'?'#form-message':'#custom-message');
       await status.getByText(mode==='success'?(name==='interest'?'You are on the list.':'Request received.'):'We could not confirm your submission.',{exact:false}).waitFor();
       results.forms.push({name,mode,width,status:await status.innerText(),retained:await form.locator('input[type=email]').inputValue(),mockOnly:true});
       await form.screenshot({path:`${out}/web_${name}_${mode}_light_${width}_before.png`});
       await page.unroute(`**/api/${name}`);
     }
   }
 }
 return results;
}
