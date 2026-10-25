// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import {test} from 'node:test';
import http from 'node:http';
import {spawn} from 'node:child_process';
import {fileURLToPath} from 'node:url';
const scripts=fileURLToPath(new URL('./',import.meta.url));
async function run(endpoint,mode){
 const env={...process.env}; for(const key of ['GH_TOKEN','GITHUB_TOKEN','PROXX_API_KEY','REVIEW_CLASSIFIER_API_KEY'])delete env[key];
 env.REVIEW_CLASSIFIER_API_KEY='synthetic-local-classifier-key';
 const child=spawn('nbb',['-cp',scripts,`${scripts}fixtures/native_advisory_session.cljs`,endpoint,mode],{env,stdio:['ignore','pipe','pipe']});
 let out='',err='';child.stdout.on('data',b=>out+=b);child.stderr.on('data',b=>err+=b);
 const code=await new Promise((resolve,reject)=>{child.once('error',reject);child.once('exit',resolve)});
 assert.equal(code,0,err);return JSON.parse(out);
}
test('fresh native parent consumes the actual bounded HTTP transport exactly once',async()=>{
 let calls=0;
 const server=http.createServer(async(req,res)=>{
  calls++;assert.equal(req.headers.authorization,'Bearer synthetic-local-classifier-key');let text='';for await(const b of req)text+=b;
  const payload=JSON.parse(text),request=JSON.parse(payload.messages[1].content);
  assert.equal(request.version,'raw-review/v2');
  assert.match(request.custody.nonce,/^[0-9a-f]{64}$/);
  const output={request_id:request['request-id'],units:request.units.map(u=>({id:u.id,items:[{classification:'actionable',reason:'A real correction is still present.',quote:u.text}]}))};
  res.writeHead(200,{'content-type':'application/json'});res.end(JSON.stringify({id:'synthetic-response',model:payload.model,choices:[{finish_reason:'stop',message:{content:JSON.stringify(output)}}]}));
 });
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 try{
  const endpoint=`http://127.0.0.1:${server.address().port}/v1/chat/completions`;
  const valid=await run(endpoint,'valid');
  assert.equal(valid.result.status,'classified',JSON.stringify(valid));assert.equal(valid.result.authority,'triage-only');
  assert.equal(valid.repeated.reason,'challenge-consumed-or-foreign');assert.deepEqual(valid.phases,['attempt','consumed']);
  const altered=await run(endpoint,'altered-digest');
  assert.equal(altered.result.status,'uncertain');assert.equal(altered.result.reason,'inference-request-mismatch');
  assert.equal(altered.repeated.reason,'challenge-consumed-or-foreign');assert.deepEqual(altered.phases,['attempt','consumed']);
  assert.equal(calls,2,'one actual HTTP invocation for each independently started challenge');
 }finally{await new Promise(resolve=>server.close(resolve))}
});
