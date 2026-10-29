async (page)=>{
 const cdp=await page.context().newCDPSession(page);
 const issues=[];cdp.on('Audits.issueAdded',e=>issues.push(e.issue));
 await cdp.send('Audits.enable');
 const result={contrast:[],reflow:[],browser:page.context().browser().version()};
 for(const path of ['/','/recovery/']){
  await page.setViewportSize({width:1440,height:1000});await page.goto('http://127.0.0.1:8772'+path);
  const start=issues.length;
  try {await cdp.send('Audits.checkContrast',{reportAAA:false});result.contrast.push({path,check:'Chrome DevTools contrast audit',issues:issues.slice(start)});} catch(e) {result.contrast.push({path,error:e.message});}
  await page.setViewportSize({width:320,height:900});
  result.reflow.push({path,...await page.evaluate(()=>({clientWidth:document.documentElement.clientWidth,scrollWidth:document.documentElement.scrollWidth}))});
 }
 await cdp.detach();return result;
}
