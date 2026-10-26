<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# Semantic repair assessment

PR45 introduced model-based raw-review triage. This extension connects a distinct,
purpose-specific assessment to canonical settlement for a resolved PR-author
repair conversation with a current-head independent native Bot verification.
It does not turn an `informational` triage label into settlement authority.

The reproduced PR144 failure is an author-native root whose raw body CodeRabbit
wrapped in generated markup and a confirmation footer. The old repair parser
rejects its bare `Fixed` prefix test before considering the later verification.
The semantic route supplies all raw thread comments, enclosing reviews, complete
PR issue context and immutable source to a model. It asks whether the root states
a repair, the final native Bot independently verifies it, source supports it, and
any live concern remains. It does not reconstruct an unavailable original root.

After protected source review and adoption, explicitly opt in:

```sh
nbb -cp ~/.agents/skills/pr-flow/scripts ~/.agents/skills/pr-flow/scripts/pr.cljs \
  status OWNER/REPO PR --semantic-model opencode/glm-5.3-flash-free
```

Opting in sends all raw thread comments, enclosing review bodies, PR issue
comments, and the contents of up to eight repository files at the PR head to
the external OpenCode model provider. This data leaves the local environment;
the provider's retention policy is outside this repository's control.

`gate` and `threads` accept the same flag. Default behavior is unchanged. Only
an explicit supported free native model is accepted; there is no paid fallback.
This source candidate is not installed or operationally qualified by its tests.

The CLI owns native collection, canonical Bot allowlisting, Git-blob verification,
model invocation and fresh source readback. It does not load assessment JSON from
disk. Its in-process cache binds the full request digest and cannot survive a new
CLI invocation. Every semantic command recollects native context after inference and refuses any changed snapshot; existing status/gate final checks remain in force.
The pure law retains native identities, authorization, resolved state, complete
ordered context, immutable enclosing review commit, unedited final verification
and chronology. Model output must account for exact unique quotes from the root,
verification and every supplied source; contradictory, uncertain, missing or
malformed results supply no proof. A syntactically valid model can still make a
semantic mistake: these checks are custody controls, not a claim of calibrated
classifier accuracy or immunity to prompt injection.

A qualifying proof supplies only scoped repair settlement. Root identity,
severity and history remain intact. It supplies no PR approval, review coverage,
round, quorum or required-check credit. Genuine findings and unresolved threads
remain obligations. A changed head, source, comment, enclosing review or issue
context invalidates the binding; failures and unavailable inference stay open.

The semantic purpose has a separate complete-input budget: 750,000 Clojure
characters, at most eight source files of 65,536 bytes each, and a 1,000,000-byte
serialized payload. The shared native transport admits at most 1,048,576 bytes
for this purpose, while raw triage retains its 262,144-byte budget. Nothing is
truncated. The same isolated native client, zero reported cost, one assistant,
no-tool execution and session/export digest checks from PR45 remain required.

Captured-native fixtures and adversarial tests are source preparation. A live
captured-input evaluation on 2026-10-25 prepared the complete PR144 context. The
native provider returned HTTP 429 with `retry_after_ms=33967000` before inference. No
judgment, settlement, native-runtime qualification or paid fallback resulted.

## Relationship to the accepted body-intent plan

[Native review intent](native-review-intent-plan.md) specifies the separate NRI body-intent purpose and protected App publisher. This candidate does not implement that purpose, clear body items or enroll a new native assessor. Its narrower resolved-thread scope retains an already-published independent native Bot verification, with the model interpreting the full author-repair conversation and source. Body-intent publication and its nonce/generation/adoption requirements remain separate unfinished work. A generic informational label cannot enter this thread proof.
