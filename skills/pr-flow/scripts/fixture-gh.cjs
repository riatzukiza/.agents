#!/usr/bin/env node
// Test-only gh boundary. Never contacts GitHub; any unknown command fails.
const fs = require('node:fs');
const config = JSON.parse(fs.readFileSync(process.env.PR_FLOW_TEST_DATA, 'utf8'));
const statePath = `${process.env.PR_FLOW_TEST_DATA}.state`;
let state = fs.existsSync(statePath) ? JSON.parse(fs.readFileSync(statePath, 'utf8')) : {heads: 0};
const args = process.argv.slice(2);
const input = fs.readFileSync(0, 'utf8');
fs.appendFileSync(`${process.env.PR_FLOW_TEST_DATA}.calls`, `${JSON.stringify({args, input})}\n`);
const out = x => process.stdout.write(typeof x === 'string' ? x : JSON.stringify(x));
if (args[0] === 'api' && args[1] === 'graphql') {
  if ((args.find(x => x.startsWith('query=')) || '').includes('mutation')) process.exit(77);
  out({data: {repository: {pullRequest: {isDraft: false, reviewThreads: {pageInfo: {hasNextPage: false}, nodes: []}}}}});
} else if (args[0] === 'api' && args[1].endsWith('/reviews')) out([config.reviews || []]);
else if (args[0] === 'api' && args[1].endsWith('/comments')) out([config.comments || []]);
else if (args[0] === 'api' && args[1].includes('/commits/')) out({sha: config.resolvedCommit || config.head});
else if (args[0] === 'api' && args[1].includes('/collaborators/')) out({permission: config.authorized === false ? 'read' : 'admin'});
else if (args[0] === 'pr' && args[1] === 'view') {
  const heads = config.heads || [config.head];
  out(heads[Math.min(state.heads++, heads.length - 1)]);
  fs.writeFileSync(statePath, JSON.stringify(state));
} else if (args[0] === 'pr' && args[1] === 'checks') {
  const checks = (config.checks || []).filter(x => !args.includes('--required') || x.required);
  if (!checks.length && args.includes('--required')) {
    process.stderr.write('no checks reported'); process.exit(1);
  }
  out(checks);
} else if (args[0] === 'pr' && args[1] === 'comment') out('https://example.invalid/review-request');
else if (args[0] === 'pr' && args[1] === 'merge') {
  const match = args.indexOf('--match-head-commit');
  if (match < 0 || args[match + 1] !== config.head) process.exit(78);
  out('fixture exact-head merge');
} else { process.stderr.write('Unknown fixture command'); process.exit(79); }
