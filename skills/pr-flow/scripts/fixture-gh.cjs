#!/usr/bin/env node
// Test-only gh boundary. Never contacts GitHub; any unknown command fails.
const fs = require('node:fs');
const config = JSON.parse(fs.readFileSync(process.env.PR_FLOW_TEST_DATA, 'utf8'));
const statePath = `${process.env.PR_FLOW_TEST_DATA}.state`;
let state = fs.existsSync(statePath) ? JSON.parse(fs.readFileSync(statePath, 'utf8')) : {heads: 0};
const args = process.argv.slice(2);
const input = fs.readFileSync(0, 'utf8');
fs.appendFileSync(`${process.env.PR_FLOW_TEST_DATA}.calls`, `${JSON.stringify({args, input, hasToken: !!process.env.GH_TOKEN})}\n`);
const out = x => process.stdout.write(typeof x === 'string' ? x : JSON.stringify(x));
const persist = () => fs.writeFileSync(statePath, JSON.stringify(state));
if (args[0] === 'api' && args[1] === 'graphql') {
  if ((args.find(x => x.startsWith('query=')) || '').includes('mutation')) process.exit(77);
  const sequence = config.threadsSequence || [config.threads || []];
  const nodes = config.newThreadsAfterReviews && state.reviews ? config.newThreadsAfterReviews : sequence[Math.min(state.threads || 0, sequence.length - 1)];
  const threadPageIndex = state.threads || 0;
  state.threads = threadPageIndex + 1; persist();
  const response = config.threadPageResponses
    ? config.threadPageResponses[Math.min(threadPageIndex, config.threadPageResponses.length - 1)]
    : {data: {repository: {pullRequest: {isDraft: !!config.draft && !state.ready, reviewThreads: {pageInfo: {hasNextPage: false}, nodes}}}}};
  out(response);
  if (config.threadPageExitCode) {
    process.stderr.write('fixture GraphQL error reported by gh'); process.exit(config.threadPageExitCode);
  }
} else if (args[0] === 'api' && args[1].endsWith('/reviews')) {
  const sequence = config.reviewsSequence || [config.reviews || []];
  out([sequence[Math.min(state.reviews || 0, sequence.length - 1)]]);
  state.reviews = (state.reviews || 0) + 1; persist();
}
else if (args[0] === 'api' && args[1].endsWith('/comments')) out([config.comments || []]);
else if (args[0] === 'api' && args[1].includes('/commits/')) out({sha: config.resolvedCommit || config.head});
else if (args[0] === 'api' && args[1].includes('/collaborators/')) out({permission: config.authorized === false ? 'read' : 'admin'});
else if (args[0] === 'pr' && args[1] === 'view') {
  const heads = config.heads || [config.head];
  out(heads[Math.min(state.heads++, heads.length - 1)]);
  fs.writeFileSync(statePath, JSON.stringify(state));
} else if (args[0] === 'pr' && args[1] === 'checks') {
  const sequence = config.checksSequence || [config.checks || []];
  if (!args.includes('--required')) { state.checks = (state.checks || 0) + 1; persist(); }
  const checks = sequence[Math.min((state.checks || 1) - 1, sequence.length - 1)].filter(x => !args.includes('--required') || x.required);
  if (!checks.length && args.includes('--required')) {
    process.stderr.write(config.requiredMessage || 'no checks reported'); process.exit(1);
  }
  out(checks);
} else if (args[0] === 'pr' && args[1] === 'comment') {
  if (config.rejectCommentToken && process.env.GH_TOKEN) {
    process.stderr.write('Resource not accessible by personal access token'); process.exit(1);
  }
  out('https://example.invalid/review-request');
} else if (args[0] === 'pr' && args[1] === 'ready') { state.ready = true; persist(); out('fixture ready'); }
else if (args[0] === 'pr' && args[1] === 'merge') {
  const match = args.indexOf('--match-head-commit');
  if (match < 0 || args[match + 1] !== config.head) process.exit(78);
  out('fixture exact-head merge');
} else { process.stderr.write('Unknown fixture command'); process.exit(79); }
