import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const source = readFileSync(new URL('../website/app.js', import.meta.url), 'utf8');
for (const formId of ['#interest-form','#custom-form']) {
  for (const scenario of ['html503','disconnect','timeout','invalid-json','validation','success']) {
    test(`${formId} ${scenario} preserves input unless acknowledged`, async () => {
      const nodes = new Map();
      function element(id) {
        if (!nodes.has(id)) nodes.set(id,{action:'/api/fixture',disabled:false,resetCount:0, classList:{add(){},remove(){}},
          querySelector:()=>element(id+'button'), addEventListener(_name,fn){this.submit=fn;},reset(){this.resetCount++;}});
        return nodes.get(id);
      }
      const fetch = async (_url, options) => {
        assert.ok(options.signal);
        if (scenario === 'disconnect') throw new Error('raw private network detail');
        if (scenario === 'timeout') throw new DOMException('raw timeout detail', 'TimeoutError');
        return {ok:scenario==='success', status:scenario==='validation'?400:503,
          headers:{get:()=>scenario==='html503'?'text/html':'application/json'},
          json:async()=>{if(scenario==='invalid-json')throw new SyntaxError('raw JSON detail');return scenario==='success'?{ok:true}:{error:'Review your email address.'};}};
      };
      new Function('document','fetch','FormData',source)(
        {querySelector:element,querySelectorAll:()=>[]},fetch,class{*[Symbol.iterator](){yield ['email','synthetic@example.invalid'];}});
      await element(formId).submit({preventDefault(){}});
      assert.equal(element(formId).resetCount, scenario==='success'?1:0);
      assert.equal(element(formId+'button').disabled,false);
      const text = element(formId==='#interest-form'?'#form-message':'#custom-message').textContent;
      if(scenario==='success') assert.match(text,/list|received/);
      else if(scenario==='validation') assert.equal(text,'Review your email address.');
      else {assert.match(text,/could not confirm/);assert.doesNotMatch(text,/raw|Unexpected token/);}
    });
  }
}
