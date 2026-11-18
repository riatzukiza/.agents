const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const read = (p) => fs.readFileSync(path.join(root, p), 'utf8');

test('local progress is uncapped and hosted requests have a separate budget', () => {
  const skill = read('SKILL.md');
  const config = read('configs/native.edn');
  assert.match(skill, /Direct local/);
  assert.match(skill, /Parallel local/);
  assert.match(skill, /Local\/internal review and repair have no fixed pass cap/);
  assert.match(skill, /same failure recurs without progress/);
  assert.match(skill, /change the approach/);
  assert.match(skill, /continue independent work/);
  assert.match(skill, /existing repository and provider limits, quotas, cooldowns and pending-request guards/);
  assert.match(skill, /do not cap internal reviews, local repairs or test reruns/);
  assert.match(skill, /Do not invent a new task-wide or cross-PR request count/);
  assert.match(skill, /continue useful local work/);
  assert.doesNotMatch(skill, /at most two review-driven repair passes|budget never resets/);
  assert.match(skill, /skeptic is optional/);
  assert.match(skill, /strict EDN plan, manifest and validator are optional/);
  assert.match(config, /:repair\/local-max-passes nil/);
  assert.match(config, /:review\/github-request-controls :existing-repository-and-provider-limits/);
  assert.doesNotMatch(config, /:repair\/default-max-passes|github-default-max-rereview-cycles/);
});

test('strict qualification is optional and cannot waive real requirements', () => {
  const skill = read('SKILL.md');
  assert.match(read('configs/qualification.edn'), /:use :optional-strict-profile/);
  assert.match(read('references/native-schema.md'), /strict format is opt-in/);
  assert.match(skill, /Required checks and safety obligations are not waived/);
  assert.match(skill, /Do not fabricate approvals or bypass required controls/);
});
