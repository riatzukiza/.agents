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
const text = mode === 'invalid-json' ? 'not JSON' : JSON.stringify({request_id:'synthetic-request',units:[]});
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
