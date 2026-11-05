// SPDX-License-Identifier: GPL-3.0-or-later
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { createServer } from 'node:http';
import { spawn, spawnSync } from 'node:child_process';
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const hash = text => createHash('sha256').update(text, 'utf8').digest('hex');
const unicode = '😀é語'.repeat(9000); // 36,000 UTF-16 units, 81,000 UTF-8 bytes.
const fragmentedStdin = `
  import { Readable } from 'node:stream';
  import { readFileSync } from 'node:fs';
  import { pathToFileURL } from 'node:url';
  const input = readFileSync(process.env.CLASSIFIER_INPUT_FILE);
  const start = input.indexOf(Buffer.from('😀é語'));
  // Deterministic async stdin buffers ending inside 4-, 2-, and 3-byte scalars.
  const boundaries = [0, start + 1, start + 5, start + 7, input.length];
  Object.defineProperty(process, 'stdin', { value: Readable.from(
    boundaries.slice(1).map((end, i) => input.subarray(boundaries[i], end))) });
  await import(pathToFileURL(process.env.CLASSIFIER_ADAPTER_FILE));
`;

function invoke(adapter, temp, input, env = {}) {
  const file = join(temp, 'stdin.json');
  writeFileSync(file, JSON.stringify(input));
  const child = spawn(process.execPath, ['--input-type=module', '-e', fragmentedStdin], {
    env: { ...process.env, ...env, CLASSIFIER_INPUT_FILE: file,
      CLASSIFIER_ADAPTER_FILE: join(here, 'pr_flow', adapter) },
  });
  let stdout = '', stderr = '';
  child.stdout.setEncoding('utf8'); child.stderr.setEncoding('utf8');
  child.stdout.on('data', part => { stdout += part; });
  child.stderr.on('data', part => { stderr += part; });
  return new Promise((resolve, reject) => {
    child.on('error', reject);
    child.on('close', code => resolve({ code, stdout, stderr }));
  });
}

test('HTTP stdin preserves every Unicode scalar and the exact request digest across buffers', async () => {
  const temp = mkdtempSync(join(tmpdir(), 'classifier-unicode-http-'));
  const payload = { model: 'synthetic', messages: [{ role: 'user', content: unicode }] };
  const requests = [];
  const server = createServer(async (req, res) => {
    req.setEncoding('utf8');
    let body = '';
    for await (const part of req) body += part;
    requests.push(body);
    res.end(JSON.stringify({ id: 'synthetic', model: 'synthetic', choices: [{
      finish_reason: 'stop', message: { content: '{}' },
    }] }));
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  try {
    const result = await invoke('classifier_transport.mjs', temp, {
      endpoint: `http://127.0.0.1:${server.address().port}/v1/chat/completions`, payload,
    }, { REVIEW_CLASSIFIER_API_KEY: 'synthetic' });
    assert.equal(result.stderr, ''); assert.equal(result.code, 0);
    assert.equal(JSON.parse(result.stdout).inference.request_sha256, hash(JSON.stringify(payload)));
    assert.deepEqual(requests, [JSON.stringify(payload)]);
  } finally {
    await new Promise(resolve => server.close(resolve));
    rmSync(temp, { recursive: true });
  }
});

test('native OpenCode stdin and attachment preserve the exact Unicode payload and digest', async () => {
  const temp = mkdtempSync(join(tmpdir(), 'classifier-unicode-native-'));
  const observed = join(temp, 'attachment.json');
  const payload = { model: 'opencode/glm-5.3-flash-free', instruction: 'Synthetic only',
    request: { units: [{ id: 0, text: unicode }] }, response_schema: {} };
  writeFileSync(join(temp, 'opencode'), `#!/usr/bin/env node
    const fs = require('node:fs'), args = process.argv.slice(2);
    const text = '{}';
    if (args[0] === 'run') {
      fs.copyFileSync(args[args.indexOf('--file')+1], process.env.CLASSIFIER_ATTACHMENT);
      console.log(JSON.stringify({type:'text',sessionID:'synthetic',part:{text}}));
    } else console.log(JSON.stringify({info:{id:'synthetic',outcome:'succeeded',cost:0,tokens:{}},
      messages:[{id:'synthetic',type:'assistant',finish:'stop',model:{providerID:'opencode',id:'glm-5.3-flash-free'},
        content:[{type:'text',text}]}]}));
  `, { mode: 0o700 });
  try {
    const result = await invoke('opencode_classifier.mjs', temp, { payload }, {
      PATH: `${temp}:${process.env.PATH}`, CLASSIFIER_ATTACHMENT: observed,
    });
    assert.equal(result.stderr, ''); assert.equal(result.code, 0);
    assert.equal(JSON.parse(result.stdout).inference.request_sha256, hash(JSON.stringify(payload)));
    assert.equal(readFileSync(observed, 'utf8'), JSON.stringify(payload));
  } finally { rmSync(temp, { recursive: true }); }
});

test('standalone raw-review law assertions fail the actual nbb process', () => {
  const temp = mkdtempSync(join(tmpdir(), 'classifier-law-exit-'));
  try {
    const source = readFileSync(join(here, 'test_raw_review.cljs'), 'utf8');
    const failing = source.replace('(run-tests)',
      '(deftest synthetic-failure (is false "Synthetic assertion failure"))\n(run-tests)');
    assert.notEqual(failing, source);
    const file = join(temp, 'failing_raw_review.cljs');
    writeFileSync(file, failing);
    const result = spawnSync('nbb', ['-cp', here, file], { encoding: 'utf8', timeout: 15000 });
    assert.equal(result.stderr, '');
    assert.match(result.stdout, /Synthetic assertion failure/);
    assert.equal(result.status, 1, 'a printed failed assertion must fail CI');
  } finally { rmSync(temp, { recursive: true }); }
});

for (const protocol of ['chat', 'systemone', 'opencode']) {
  test(`parent rejects altered request digest before accepting ${protocol} output`, () => {
    const temp = mkdtempSync(join(tmpdir(), 'classifier-parent-proof-'));
    try {
      // Run the real parent with a synthetic child, without invoking any provider.
      writeFileSync(join(temp, 'classify_review.cljs'), readFileSync(join(here, 'classify_review.cljs')));
      mkdirSync(join(temp, 'pr_flow'));
      const child = `
        process.stdin.setEncoding('utf8');
        let text=''; for await (const part of process.stdin) text+=part;
        const { payload }=JSON.parse(text);
        const request=payload.request || (payload.state ? null : JSON.parse(payload.messages[1].content));
        const units=request ? request.units : payload.state['review-units'];
        const output=payload.state ? { model:'synthetic', answers:Object.fromEntries(units.map(u =>
          ['unit_'+u.id,{type:'choice',choice:'actionable',confidence:1,
            probabilities:{actionable:1,informational:0,uncertain:0}}])) } : {
          request_id:request['request-id'],units:units.map(u => ({id:u.id,items:[{
            classification:'actionable',reason:'Synthetic proof test',quote:u.text}]})) };
        console.log(JSON.stringify({output,inference:{request_sha256:'0'.repeat(64)}}));
      `;
      writeFileSync(join(temp, 'pr_flow', protocol === 'opencode' ? 'opencode_classifier.mjs' : 'classifier_transport.mjs'), child);
      const fixture = join(here, 'fixtures/native-receipt4-dl-review.json');
      const capture = JSON.parse(readFileSync(fixture, 'utf8'));
      const result = spawnSync('nbb', ['-cp', here, join(temp, 'classify_review.cljs'), fixture,
        'open-hax/receipt-river', '4', capture.review.commit_id,
        protocol === 'opencode' ? 'opencode' : 'http://127.0.0.1:12345', 'synthetic', protocol],
      { encoding: 'utf8', timeout: 15000 });
      assert.equal(result.stderr, '');
      assert.equal(result.status, 2);
      const output = JSON.parse(result.stdout);
      assert.equal(output.status, 'uncertain');
      assert.equal(output.reason, 'inference-request-mismatch');
      assert.equal(output.authority, 'triage-only');
      assert.equal(output.items, undefined);
    } finally { rmSync(temp, { recursive: true }); }
  });
}
