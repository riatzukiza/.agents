// SPDX-License-Identifier: GPL-3.0-or-later
// One bounded inference; stdout is a private parent-process protocol.
import { createHash } from 'node:crypto';

const sha256 = text => createHash('sha256').update(text, 'utf8').digest('hex');
const started = Date.now();
let phase = 'invalid-input';
try {
  const parts = [];
  let bytes = 0;
  for await (const part of process.stdin) {
    bytes += part.length;
    if (bytes > 262144) throw new Error('Classifier request too large');
    parts.push(part);
  }
  const input = new TextDecoder('utf-8', { fatal: true }).decode(Buffer.concat(parts, bytes));
  const { endpoint, payload, protocol = 'chat' } = JSON.parse(input);
  phase = 'invalid-endpoint';
  const url = new URL(endpoint);
  if (url.username || url.password || url.search || url.hash ||
      !(url.protocol === 'https:' || (url.protocol === 'http:' &&
        ['127.0.0.1', 'localhost', '[::1]'].includes(url.hostname)))) {
    throw new Error('Invalid classifier endpoint');
  }
  const key = process.env.REVIEW_CLASSIFIER_API_KEY ||
    (url.origin === 'http://127.0.0.1:8789' ? process.env.PROXX_API_KEY : undefined);
  phase = 'credential-unavailable';
  if (!key) throw new Error('Classifier credential unavailable');
  const body = JSON.stringify(payload);
  phase = 'request-failed-or-timed-out';
  const response = await fetch(url, {
    method: 'POST', redirect: 'error', signal: AbortSignal.timeout(60000),
    headers: { 'content-type': 'application/json', authorization: `Bearer ${key}` }, body,
  });
  // Never include remote error bodies, headers or credentials in diagnostics.
  if (!response.ok) {
    process.stdout.write(JSON.stringify({ error: 'provider-http-error', status: response.status }));
    process.exitCode = 1;
  } else {
    phase = 'response-read-or-json-error';
    let text = '';
    const decoder = new TextDecoder('utf-8', { fatal: true });
    for await (const part of response.body) {
      text += decoder.decode(part, { stream: true });
      if (Buffer.byteLength(text) > 1048576) throw new Error('Classifier response too large');
    }
    text += decoder.decode();
    const result = JSON.parse(text);
    const inference = { endpoint: url.href, configured_model: payload.model, reported_model: result.model,
      response_id: result.id ?? null, request_sha256: sha256(body), response_sha256: sha256(text),
      duration_ms: Date.now() - started, usage: result.usage ?? null };
    if (protocol === 'systemone') {
      phase = 'incomplete-systemone-response';
      if (typeof result.model !== 'string' || !result.answers || typeof result.answers !== 'object') {
        throw new Error('Incomplete System One response');
      }
      process.stdout.write(JSON.stringify({ output: result, inference }));
      process.exit(0);
    }
    if (protocol !== 'chat') throw new Error('Unknown classifier protocol');
    const choice = result.choices?.[0];
    phase = 'incomplete-classifier-response';
    if (result.choices?.length !== 1 || choice.finish_reason !== 'stop' ||
        choice.message?.tool_calls?.length || choice.message?.function_call ||
        choice.message?.refusal || typeof choice.message?.content !== 'string' ||
        typeof result.model !== 'string' || typeof result.id !== 'string') {
      throw new Error('Incomplete classifier response');
    }
    phase = 'invalid-classification-json';
    process.stdout.write(JSON.stringify({
      output: JSON.parse(choice.message.content),
      inference,
    }));
  }
} catch {
  process.stdout.write(JSON.stringify({ error: phase, duration_ms: Date.now() - started }));
  process.exitCode = 1;
}
