#!/usr/bin/env node
// Test-only gh boundary. Never contacts GitHub; any unknown command fails.
const fs = require('node:fs');
const config = JSON.parse(fs.readFileSync(process.env.PR_FLOW_TEST_DATA, 'utf8'));
const statePath = `${process.env.PR_FLOW_TEST_DATA}.state`;
let state = fs.existsSync(statePath) ? JSON.parse(fs.readFileSync(statePath, 'utf8')) : {heads: 0};
const args = process.argv.slice(2);
const input = fs.readFileSync(0, 'utf8');
fs.appendFileSync(`${process.env.PR_FLOW_TEST_DATA}.calls`, `${JSON.stringify({args, input, hasToken: !!process.env.GH_TOKEN, hasGithubToken: !!process.env.GITHUB_TOKEN})}\n`);
const out = x => process.stdout.write(typeof x === 'string' ? x : JSON.stringify(x));
const persist = () => fs.writeFileSync(statePath, JSON.stringify(state));
if (args[0] === 'api' && Object.hasOwn(config.apiResponses || {}, args[1])) {
  const response = config.apiResponses[args[1]];
  if (response.binaryBase64) process.stdout.write(Buffer.from(response.binaryBase64, 'base64'));
  else out(args.includes('--slurp') && !Array.isArray(response) ? [response] : response);
} else if (args[0] === 'api' && args[1] === 'graphql') {
  const query = args.find(x => x.startsWith('query=')) || '';
  if (query.includes('mutation')) {
    if (!config.allowMutations) process.exit(77);
    if (query.includes('addPullRequestReviewThreadReply')) {
      out({data: {addPullRequestReviewThreadReply: {comment: {url: 'https://example.invalid/review#reply'}}}});
    } else if (query.includes('resolveReviewThread')) {
      out(config.resolveResponse || {data: {resolveReviewThread: {thread: {isResolved: true}}}});
      if (config.resolveExitCode) process.exit(config.resolveExitCode);
    } else process.exit(77);
    process.exit(0);
  }
  if (query.includes('node(id:$id)')) {
    if (!query.includes('author{__typename ... on Bot{id}}')) {
      process.stderr.write("Field 'id' doesn't exist on type 'Actor'; use the Bot fragment");
      process.exit(1);
    }
    const id = args.find(x => x.startsWith('id='))?.slice(3);
    out({data: {node: config.reviewUpdateResponses?.[id] ?? null}});
    process.exit(0);
  }
  // Match gh api -F scalar inference before GraphQL String/Int validation.
  const field = name => {
    const i = args.findIndex(x => x.startsWith(`${name}=`));
    const value = i < 0 ? undefined : args[i].slice(name.length + 1);
    if (args[i - 1] === '-F') {
      if (/^-?\d+$/.test(value)) return Number(value);
      if (value === 'true' || value === 'false') return value === 'true';
      if (value === 'null') return null;
    }
    return value;
  };
  if (typeof field('owner') !== 'string' || typeof field('name') !== 'string' || !Number.isInteger(field('n'))) {
    process.stderr.write('GraphQL owner/name require String; n requires Int'); process.exit(1);
  }
  const sequence = config.threadsSequence || [config.threads || []];
  const nodes = config.newThreadsAfterReviews && state.reviews ? config.newThreadsAfterReviews : sequence[Math.min(state.threads || 0, sequence.length - 1)];
  const threadPageIndex = state.threads || 0;
  state.threads = threadPageIndex + 1; persist();
  const response = config.threadPageResponses
    ? config.threadPageResponses[Math.min(threadPageIndex, config.threadPageResponses.length - 1)]
    : {data: {repository: {pullRequest: {author: {login: config.prAuthor || 'original-author'}, headRefOid: config.head, isDraft: !!config.draft && !state.ready, reviewThreads: {pageInfo: {hasNextPage: false}, nodes}}}}};
  if (config.historyChangeAtThreadRead === threadPageIndex) {
    // Owned mock ledger only. Never accesses the installed canonical ledger.
    const ledger = require('node:path').join(require('node:path').dirname(process.env.PR_FLOW_TEST_DATA), '.ημ', 'receipts.edn');
    if (config.historyFirstReceiptReplacementBase64) {
      const current = fs.readFileSync(ledger);
      const end = current.indexOf(10) + 1;
      if (!end) process.exit(79);
      fs.writeFileSync(ledger, Buffer.concat([Buffer.from(config.historyFirstReceiptReplacementBase64, 'base64'), current.subarray(end)]));
    } else if (config.historyReplacementBase64) fs.writeFileSync(ledger, Buffer.from(config.historyReplacementBase64, 'base64'));
    else fs.appendFileSync(ledger, config.historyChangeText || '{:origin "other"}\n');
  }
  out(response);
  if (config.threadPageExitCode) {
    process.stderr.write('fixture GraphQL error reported by gh'); process.exit(config.threadPageExitCode);
  }
} else if (args[0] === 'api' && args[1].endsWith('/reviews')) {
  const sequence = config.reviewsSequence || [config.reviews || []];
  out([sequence[Math.min(state.reviews || 0, sequence.length - 1)]]);
  state.reviews = (state.reviews || 0) + 1; persist();
}
else if (args[0] === 'api' && args[1].endsWith('/comments')) {
  const index = state.comments || 0;
  state.comments = index + 1; persist();
  if (config.commentPageResponses) {
    out(config.commentPageResponses[Math.min(index, config.commentPageResponses.length - 1)]);
  } else {
    const sequence = config.commentsSequence || [config.comments || []];
    out([sequence[Math.min(index, sequence.length - 1)]]);
  }
}
else if (args[0] === 'api' && args[1].endsWith('/statuses')) {
  const sha = args[1].split('/').at(-2);
  if ((config.unavailableCommitStatuses || []).includes(sha)) {
    process.stderr.write('Historical commit status history unavailable (HTTP 404)'); process.exit(1);
  }
  const sequence = config.commitStatusesSequence?.[sha] || [config.commitStatuses?.[sha] || []];
  const index = state.commitStatuses?.[sha] || 0;
  state.commitStatuses = {...state.commitStatuses, [sha]: index + 1}; persist();
  out(config.commitStatusPages && Object.hasOwn(config.commitStatusPages, sha)
    ? config.commitStatusPages[sha] : [sequence[Math.min(index, sequence.length - 1)]]);
}
else if (args[0] === 'api' && args[1].includes('/commits/')) {
  const ref = args[1].split('/').at(-1);
  out({sha: config.commitResponses?.[ref] || (/^[0-9a-f]{40}$/.test(ref) ? ref : (config.resolvedCommit || config.head))});
}
else if (args[0] === 'api' && args[1].includes('/collaborators/')) {
  out({permission: config.writerRoleAfterThreadReads != null && (state.threads || 0) >= 2
    ? config.writerRoleAfterThreadReads : config.authorized === false ? 'read' : 'admin'});
}
else if (args[0] === 'api' && args[1] === 'user') {
  const index = state.users || 0; state.users = index + 1; persist();
  const user = config.viewerUsers?.[Math.min(index, config.viewerUsers.length - 1)] || config.viewerUser || {login: config.viewer || 'riatzukiza'};
  out(args.includes('-q') || args.includes('--jq') ? user.login : user);
}
else if (args[0] === 'pr' && args[1] === 'view') {
  if (args.includes('author')) { out(config.prAuthor || 'original-author'); process.exit(0); }
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
