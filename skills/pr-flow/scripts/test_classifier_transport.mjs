// SPDX-License-Identifier: GPL-3.0-or-later
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { spawn } from 'node:child_process';
import { readFileSync, mkdtempSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const fixture = join(here, 'fixtures/native-receipt4-dl-review.json');
const capture = JSON.parse(readFileSync(fixture, 'utf8'));
const head = capture.review.commit_id;
const key = 'synthetic-classifier-test-credential';

async function execute(endpoint, input = fixture, protocol = 'chat') {
  const env = { ...process.env, REVIEW_CLASSIFIER_API_KEY: key };
  delete env.PROXX_API_KEY;
  const child = spawn('nbb', ['-cp', here, join(here, 'classify_review.cljs'),
    input, 'open-hax/receipt-river', '4', head, endpoint, 'synthetic-test-model', protocol], { env });
  let out = '', err = '';
  child.stdout.on('data', part => { out += part; });
  child.stderr.on('data', part => { err += part; });
  const code = await new Promise((resolve, reject) => {
    child.on('error', reject); child.on('close', resolve);
  });
  assert.equal(err, '');
  assert.ok(!out.includes(key));
  return { code, result: JSON.parse(out) };
}

async function withProvider(fn, run) {
  const requests = [];
  const server = createServer(async (req, res) => {
    assert.equal(req.headers.authorization, `Bearer ${key}`);
    let body = '';
    for await (const part of req) body += part;
    const payload = JSON.parse(body);
    requests.push(payload);
    const typed = Boolean(payload.questions);
    const input = typed ? { units: payload.state['review-units'] } : JSON.parse(payload.messages[1].content);
    assert.equal(input.units.map(unit => unit.text).join(''), capture.review.body);
    assert.equal(payload.model, 'synthetic-test-model');
    if (!typed) assert.equal(payload.response_format.json_schema.strict, true);
    const output = { request_id: input['request-id'], units: input.units.map(unit => ({
      id: unit.id, items: [{ classification: 'uncertain', reason: 'Synthetic transport test, no semantic judgment.', quote: unit.text }],
    })) };
    const response = typed ? { model: 'synthetic-systemone', answers: Object.fromEntries(input.units.map(unit =>
      [`unit_${unit.id}`, { type: 'choice', choice: 'actionable', confidence: 1,
        probabilities: { actionable: 1, informational: 0, uncertain: 0 } }])),
    } : { id: 'synthetic-response', model: 'synthetic-test-model', choices: [{
      finish_reason: 'stop', message: { content: JSON.stringify(output) },
    }] };
    fn({ response, output, req, res });
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  try { await run(`http://127.0.0.1:${server.address().port}/v1/chat/completions`, requests); }
  finally { await new Promise(resolve => server.close(resolve)); }
}

test('complete raw native input travels unchanged; uncertainty stays unresolved', async () => {
  await withProvider(({ response, res }) => res.end(JSON.stringify(response)), async (endpoint, requests) => {
    const { code, result } = await execute(endpoint);
    assert.equal(code, 2);
    assert.equal(result.status, 'uncertain');
    assert.equal(result.authority, 'triage-only');
    assert.equal(result.inference.reported_model, 'synthetic-test-model');
    assert.equal(result.inference.request_sha256.length, 64);
    assert.equal(result.items.length, result.request.units.length);
    assert.equal(requests.length, 1);
  });
});

test('valid classification has no approval or settlement credit', async () => {
  await withProvider(({ response, output, res }) => {
    output.units.forEach(unit => { unit.items[0].classification = 'actionable'; });
    response.choices[0].message.content = JSON.stringify(output);
    res.end(JSON.stringify(response));
  }, async endpoint => {
    const { code, result } = await execute(endpoint);
    assert.equal(code, 0);
    assert.equal(result.status, 'classified');
    assert.equal(result.authority, 'triage-only');
    assert.equal(result.approved, undefined);
    assert.equal(result.settled, undefined);
  });
});

test('typed System One response is validated without manufacturing prose or native response ID', async () => {
  await withProvider(({ response, res }) => res.end(JSON.stringify(response)), async (endpoint, requests) => {
    const { code, result } = await execute(endpoint, fixture, 'systemone');
    assert.equal(code, 0);
    assert.equal(result.status, 'classified');
    assert.equal(result.authority, 'triage-only');
    assert.equal(result.units.map(unit => unit.text).join(''), capture.review.body);
    assert.equal(result.inference.response_id, null);
    assert.equal(requests.length, 1);
  });
});

for (const failure of ['429', 'malformed-json', 'truncated', 'tool-call', 'missing-unit', 'wrong-hash']) {
  test(`refuse ${failure}; one call with no retry or model substitution`, async () => {
    await withProvider(({ response, output, res }) => {
      if (failure === '429') { res.writeHead(429); res.end('private provider diagnostic'); return; }
      if (failure === 'malformed-json') { res.end('{bad'); return; }
      if (failure === 'truncated') response.choices[0].finish_reason = 'length';
      if (failure === 'tool-call') response.choices[0].message.tool_calls = [{ id: 'synthetic' }];
      if (failure === 'missing-unit') output.units.pop();
      if (failure === 'wrong-hash') output.request_id = 'forged';
      response.choices[0].message.content = JSON.stringify(output);
      res.end(JSON.stringify(response));
    }, async (endpoint, requests) => {
      const { code, result } = await execute(endpoint);
      assert.equal(code, 2);
      assert.equal(result.status, 'uncertain');
      assert.equal(result.items, undefined);
      assert.equal(requests.length, 1);
    });
  });
}

test('changed native body refuses before inference', async () => {
  const temp = mkdtempSync(join(tmpdir(), 'raw-review-capture-'));
  try {
    const file = join(temp, 'capture.json');
    writeFileSync(file, JSON.stringify({ ...capture, review: { ...capture.review, body: 'edited' } }));
    await withProvider(({ res }) => res.end('{}'), async (endpoint, requests) => {
      assert.equal((await execute(endpoint, file)).result.status, 'uncertain');
      assert.equal(requests.length, 0);
    });
  } finally { rmSync(temp, { recursive: true }); }
});

test('endpoint credentials cannot enter request or provenance', async () => {
  const { result } = await execute('https://user:password@example.invalid/v1/chat/completions');
  assert.equal(result.status, 'uncertain');
  assert.equal(result.inference, null);
});
