const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '../../..');
const read = p => fs.readFileSync(path.join(root, p), 'utf8');

test('direct PR entry points select a profile before legacy policy', () => {
  for (const dir of ['pr-review-settlement', 'pr-review-to-merge']) {
    const skill = read(`skills/${dir}/SKILL.md`);
    assert.match(skill, /including direct invocation|including on direct invocation/);
    assert.match(skill, /native-swarm\.md/);
    assert.match(skill, /two\s+review-driven\s+repair\s+passes/);
    assert.match(skill, /entire task|whole task/);
    assert.match(skill, /hosted-provider\.md/);
    assert.doesNotMatch(skill, /Five is a \*\*soft minimum\*\*/);
    assert.match(read(`skills/${dir}/hosted-provider.md`), /explicitly selected hosted-provider compatibility profile/);
  }
  const hosted = read('skills/pr-review-settlement/hosted-provider.md');
  assert.match(hosted, /Five is a \*\*soft minimum\*\*/);
  assert.match(hosted, /required gates must pass/);
});

test('native review cannot substitute for GitHub requirements or merge authorization', () => {
  const skill = read('skills/pr-review-to-merge/SKILL.md');
  const contract = read('skills/pr-review-to-merge/CONTRACT.edn');
  assert.match(skill, /does not itself authorize a merge/);
  assert.match(skill, /Local review evidence is not a GitHub approval/);
  assert.match(skill, /exact-head guard/);
  assert.match(contract, /\(hosted-provider-compatibility\s+\(one-active-pr true\)/);
  assert.match(contract, /\(native-review "One proportionate combined-tree review; at most two/);
  assert.match(contract, /\(require-passing-required-checks true\)/);
  assert.match(contract, /\(allow-protection-bypass false\)/);
});
