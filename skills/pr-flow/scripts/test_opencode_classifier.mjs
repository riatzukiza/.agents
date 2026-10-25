// SPDX-License-Identifier: GPL-3.0-or-later
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, writeFileSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const fakeClient = `#!/usr/bin/env node
const fs = require('node:fs'), path = require('node:path');
const args = process.argv.slice(2), mode = process.env.CLASSIFIER_TEST_MODE;
fs.appendFileSync(process.env.CLASSIFIER_TEST_CALLS, JSON.stringify(args)+'\\n');
const config = JSON.parse(fs.readFileSync(path.join(process.env.XDG_CONFIG_HOME,'opencode/opencode.json')));
if (config.providers.opencode.models['glm-5.3-flash-free'].body.response_format.type !== 'json_object') process.exit(9);
if (JSON.stringify(config.agents['raw-review-classifier'].permissions) !== JSON.stringify([
  {action:'*',resource:'*',effect:'deny'}, {action:'shell',resource:'true',effect:'allow'}
])) process.exit(10);
let text = mode === 'invalid-json' ? 'not JSON' : JSON.stringify({request_id:'synthetic-request',units:[]});
const saved = path.join(process.env.XDG_CONFIG_HOME,'synthetic-response.json');
if (mode === 'cli') {
  if (args[0] === 'run') {
    const payload = JSON.parse(fs.readFileSync(args[args.indexOf('--file')+1]));
    text = JSON.stringify({request_id:payload.request['request-id'],units:payload.request.units.map(unit => ({
      id:unit.id,items:[{classification:'uncertain',reason:'Synthetic transport test only.',quote:unit.text}]
    }))});
    fs.writeFileSync(saved,text);
  } else text = fs.readFileSync(saved,'utf8');
}
if (args[0] === 'run') {
  console.log(JSON.stringify({type:'step_start',sessionID:'ses_synthetic'}));
  console.log(JSON.stringify({type:'text',sessionID:'ses_synthetic',part:{text}}));
} else if (args[0] === 'session' && args[1] === 'export') {
  console.log(JSON.stringify({info:{id:'ses_synthetic',outcome:'succeeded',cost:mode==='charged'?0.1:0,tokens:{}},messages:[
    {id:'msg_synthetic',type:'assistant',finish:'stop',model:{providerID:'opencode',id:'glm-5.3-flash-free'},
     content:mode==='tool-call'?[{type:'tool'}]:[{type:'text',text}]}]}));
} else process.exit(8);
`;

for (const mode of ['valid', 'invalid-json', 'charged', 'tool-call', 'paid-model']) {
  test(`native-client adapter ${mode}; synthetic client never invokes a provider`, () => {
    const temp = mkdtempSync(join(tmpdir(), 'classifier-client-test-'));
    try {
      writeFileSync(join(temp, 'opencode'), fakeClient, { mode: 0o700 });
      const calls = join(temp, 'calls.jsonl');
      const result = spawnSync(process.execPath, [join(here, 'pr_flow/opencode_classifier.mjs')], {
        input: JSON.stringify({ payload: { model: mode === 'paid-model' ? 'opencode/gpt-6-luna' : 'opencode/glm-5.3-flash-free',
          instruction: 'Synthetic test instruction', request: {}, response_schema: {} } }),
        env: { ...process.env, PATH: `${temp}:${process.env.PATH}`, CLASSIFIER_TEST_MODE: mode,
          CLASSIFIER_TEST_CALLS: calls }, encoding: 'utf8', timeout: 5000,
      });
      assert.equal(result.stderr, '');
      const output = JSON.parse(result.stdout);
      if (mode === 'valid') {
        assert.equal(result.status, 0);
        assert.equal(output.output.request_id, 'synthetic-request');
        assert.equal(output.inference.reported_model, 'opencode/glm-5.3-flash-free');
        assert.equal(output.inference.cost, 0);
      } else {
        assert.equal(result.status, 1);
        assert.equal(output.output, undefined);
        if (mode === 'invalid-json') {
          assert.equal(output.unvalidated_output, 'not JSON');
          assert.equal(output.inference.cost, 0);
        }
      }
      if (mode !== 'paid-model') {
        const invocations = readFileSync(calls, 'utf8').trim().split('\n').map(JSON.parse);
        assert.equal(invocations.filter(args => args[0] === 'run').length, 1);
        assert.equal(invocations.length, 2);
      }
    } finally { rmSync(temp, { recursive: true }); }
  });
}

test('actual nbb CLI dispatches opencode and validates the complete synthetic response', () => {
  const temp = mkdtempSync(join(tmpdir(), 'classifier-cli-test-'));
  try {
    writeFileSync(join(temp, 'opencode'), fakeClient, { mode: 0o700 });
    const fixture = join(here, 'fixtures/native-receipt4-dl-review.json');
    const capture = JSON.parse(readFileSync(fixture, 'utf8'));
    const calls = join(temp, 'calls.jsonl');
    const result = spawnSync('nbb', ['-cp', here, join(here, 'classify_review.cljs'), fixture,
      'open-hax/receipt-river', '4', capture.review.commit_id,
      'opencode', 'opencode/glm-5.3-flash-free', 'opencode'], {
      env: { ...process.env, PATH: `${temp}:${process.env.PATH}`,
        CLASSIFIER_TEST_MODE: 'cli', CLASSIFIER_TEST_CALLS: calls },
      encoding: 'utf8', timeout: 15000,
    });
    assert.equal(result.stderr, '');
    assert.equal(result.status, 2, 'synthetic uncertainty cannot become success');
    const output = JSON.parse(result.stdout);
    assert.equal(output.status, 'uncertain');
    assert.equal(output.authority, 'triage-only');
    assert.equal(output.request.units.map(unit => unit.text).join(''), capture.review.body);
    assert.equal(output.items.length, output.request.units.length);
    assert.equal(output.inference.cost, 0);
    assert.equal(output.approved, undefined);
    assert.equal(output.settled, undefined);
    assert.equal(readFileSync(calls, 'utf8').trim().split('\n').length, 2);
  } finally { rmSync(temp, { recursive: true }); }
});

for (const elapsed of [65000, 70000]) {
  test(`native calls share one deadline after ${elapsed}ms; cleanup reserve survives`, () => {
    const temp = mkdtempSync(join(tmpdir(), 'classifier-deadline-test-'));
    try {
      writeFileSync(join(temp, 'opencode'), fakeClient, { mode: 0o700 });
      const clock = join(temp, 'clock');
      const budgets = join(temp, 'budgets.jsonl');
      const calls = join(temp, 'calls.jsonl');
      writeFileSync(clock, '0');
      const preload = join(temp, 'virtual-clock.cjs');
      writeFileSync(preload, `
        const fs=require('node:fs'), cp=require('node:child_process');
        const {performance}=require('node:perf_hooks');
        const {syncBuiltinESMExports}=require('node:module');
        const now=()=>Number(fs.readFileSync(process.env.CLASSIFIER_CLOCK,'utf8'));
        Date.now=now;
        Object.defineProperty(performance,'now',{value:now});
        const native=cp.spawnSync;
        cp.spawnSync=(exe,args,options)=>{
          if(exe!=='opencode')return native(exe,args,options);
          fs.appendFileSync(process.env.CLASSIFIER_BUDGETS,JSON.stringify({command:args[0],timeout:options.timeout})+'\\n');
          const result=native(exe,args,options);
          if(args[0]==='run')fs.writeFileSync(process.env.CLASSIFIER_CLOCK,process.env.CLASSIFIER_ELAPSED);
          return result;
        };
        syncBuiltinESMExports();
      `);
      const result = spawnSync(process.execPath, ['--require', preload, join(here, 'pr_flow/opencode_classifier.mjs')], {
        input: JSON.stringify({ payload: { model: 'opencode/glm-5.3-flash-free',
          instruction: 'Synthetic deadline test', request: {}, response_schema: {} } }),
        env: { ...process.env, PATH: `${temp}:${process.env.PATH}`, CLASSIFIER_TEST_MODE: 'valid',
          CLASSIFIER_TEST_CALLS: calls, CLASSIFIER_CLOCK: clock, CLASSIFIER_BUDGETS: budgets,
          CLASSIFIER_ELAPSED: String(elapsed) }, encoding: 'utf8', timeout: 5000,
      });
      assert.equal(result.stderr, '');
      const observed = readFileSync(budgets, 'utf8').trim().split('\n').map(JSON.parse);
      assert.equal(observed[0].timeout, 70000, 'initial call reserves five seconds of the 75-second lifecycle');
      const output = JSON.parse(result.stdout);
      if (elapsed === 65000) {
        assert.equal(result.status, 0);
        assert.deepEqual(observed, [{ command: 'run', timeout: 70000 }, { command: 'session', timeout: 5000 }]);
        assert.equal(output.inference.cost, 0);
      } else {
        assert.equal(result.status, 1);
        assert.equal(output.error, 'opencode-deadline-exhausted');
        assert.equal(observed.length, 1, 'export never starts without its cleanup reserve');
      }
      const invocation = JSON.parse(readFileSync(calls, 'utf8').split('\n')[0]);
      const attachment = invocation[invocation.indexOf('--file') + 1];
      assert.throws(() => readFileSync(attachment), { code: 'ENOENT' }, 'finally removed the owned attachment');
    } finally { rmSync(temp, { recursive: true }); }
  });
}
