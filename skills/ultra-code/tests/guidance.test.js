const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const read = (p) => fs.readFileSync(path.join(root, p), 'utf8');

test('local paths and task-wide bounded exit are explicit', () => {
  const skill = read('SKILL.md');
  const config = read('configs/native.edn');
  assert.match(skill, /Direct local/);
  assert.match(skill, /Parallel local/);
  assert.match(skill, /at most two review-driven repair passes for the entire task/);
  assert.match(skill, /budget never resets/);
  assert.match(skill, /stop the review\/repair loop/);
  assert.match(skill, /skeptic is optional/);
  assert.match(skill, /strict EDN plan, manifest and validator are optional/);
  assert.match(config, /:repair\/default-max-passes 2/);
  assert.match(config, /:repair\/budget-scope :entire-task-across-replanning-and-handoffs/);
  assert.match(config, /:repair\/exhaustion :stop-and-report-unresolved/);
});

test('strict qualification is optional and cannot waive real requirements', () => {
  const skill = read('SKILL.md');
  assert.match(read('configs/qualification.edn'), /:use :optional-strict-profile/);
  assert.match(read('references/native-schema.md'), /strict format is opt-in/);
  assert.match(skill, /Required checks and safety obligations are not waived/);
  assert.match(skill, /Do not fabricate approvals or bypass required controls/);
});
