async (page)=>{
 const result={pages:[]};
 await page.setViewportSize({width:1440,height:1000});
 for(const path of ['/','/recovery/']){
  await page.goto('http://127.0.0.1:8772'+path);
  const steps=[];
  for(let i=0;i<45;i++){
   await page.keyboard.press('Tab');
   const settled=await page.waitForFunction(()=>{const e=document.activeElement,r=e.getBoundingClientRect();return r.width>0&&r.height>0&&r.bottom>0&&r.top<innerHeight},{},{timeout:2000}).then(()=>true).catch(()=>false);
   steps.push(await page.evaluate(settled=>{let e=document.activeElement,r=e.getBoundingClientRect(),s=getComputedStyle(e);return {tag:e.tagName,text:(e.getAttribute('aria-label')||e.textContent||e.name||'').trim().slice(0,65),settledVisible:settled,top:r.top,bottom:r.bottom,outline:s.outlineStyle+' '+s.outlineWidth}},settled));
  }
  result.pages.push({path,steps});
 }
 return result;
}
