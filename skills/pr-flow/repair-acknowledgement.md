# Independently verified author repair acknowledgements

SPDX-License-Identifier: GPL-3.0-or-later

This is an upstream source proposal, **unadopted**. The parent owns commit,
publication, the existing hosted MiMo PR review, and separate adoption. Local
law tests supply no native verification or review credit.

A surviving author-authored repair statement can become the first available
comment without being a new finding. No missing original root, deleted Bot
identity, or deletion event is inferred from a 404. The observed first surviving
comment remains the root coordinate and its body, severity, enclosing review and
history remain intact. Ordinary opener self-settlement stays denied.

The separate `:repair-acknowledgement` proof requires all of the following:

- Complete native thread pagination/count/unique IDs, matching normalized raw
  bodies/URLs/times, known matching resolution/location flags, open nondraft PR,
  full current SHA, enclosing review bodies/actors/IDs/immutable commits/times,
  original-comment commit provenance, and the existing native issue collection.
- An authorized native User root with exactly the PR author's login/numeric/node
  identity, starting `Fixed: ` or `Fixed in <7–40 lowercase hex>: `. Quoted,
  generated, negative or unknown repair statements retain the default finding.
- A distinct physical configured native Bot, selected through the existing
  identity roster. Its enclosing COMMENTED review actor must match its native
  comment actor and its immutable `pullRequestReview.commit.oid` must equal the
  exact current PR head. Dynamic REST `comment.commit_id` is insufficient.
- The final native reply must have the exact five authored lines below. It must
  be unedited and strictly later than every prior comment and enclosing-review
  edit. The enclosing assessment review cannot have a later edit. The native
  and normalized thread must actually be resolved; no second human is required.
- Independent reasoning of at least 40 characters and an immutable same-repo
  current-head source URL with a nonempty repository-relative file path. Empty,
traversal, query and fragment-only paths refuse. Reasons containing explicit
defect, corrective or uncertain language also refuse even with a positive
Verified line. These are the Bot's own verification, never text an
  operator publishes under a Bot label. Generic acknowledgements do not qualify.

```text
Repair acknowledgement v1 for FULL_CURRENT_HEAD:
Finding: EXACT_THREAD_ID commentSURVIVING_ROOT_DATABASE_ID
Verified: complete-native-context/author-repair/no-unaddressed-finding
Reason: INDEPENDENTLY_WRITTEN_REASON_OF_AT_LEAST_40_CHARACTERS
Evidence: https://github.com/OWNER/REPO/blob/FULL_CURRENT_HEAD/PATH
```

Unwrapped replies must be exactly five lines. CodeRabbit may wrap those lines
with the exact observed `<!-- coderabbit-ai-generated:v1 -->` plus two LF bytes,
and its native sub/footer suffix, optionally including its known tweet block
and `✅ Review thread resolved.` line. The protocol must be first after the
prefix. Arbitrary HTML, analysis blocks, quoted/fenced examples, extra live
prose and mixed negatives do not supply a positive assessment. Raw bytes remain
stored; the parser only recognizes this bounded transport envelope.

A later thread append, late edit, push, or root-bound issue reply at/after the
assessment revokes admission. Unknown chronology in a root-bound issue reply
also refuses. Re-resolving cannot erase that history. Unrelated issue activity
and an ordinary request for fresh verification do not invalidate admission.
A new complete, explicit Bot assessment can supersede prior negative history
after independently addressing it. The proof supplies only scoped `:fixed`
settlement: no informational reclassification, rejection, PR approval,
coverage, round, quorum, required-check or source-adoption credit.

## Operational request on Foresight 144

The read-only native fixture captures these exact surviving coordinates:

| Thread | Surviving root | Historical Bot acknowledgement |
| --- | --- | --- |
| `PRRT_kwDOT3TWPM6vcq2j` | `4355453041` | `4355456723` |
| `PRRT_kwDOT3TWPM6vcq20` | `4355453881` | `4355476229` |

Both historical acknowledgements have enclosing review commit
`af114f393049dfb1c931fd00d57283a9ca3f0a21`, so neither is fresh evidence.
The captured current head is `f3491a43399b35d9fab4e9695d32fc5b08096436`.
Refresh the actual head before requesting; a later push requires its new full SHA.

After upstream review and adoption, use the existing CodeRabbit native in-thread
mention/reply surface on **each** surviving root. For example, send the following
request in thread `PRRT_kwDOT3TWPM6vcq2j`, replying to root `4355453041`:

```text
@coderabbitai Independently re-verify this author repair statement at f3491a43399b35d9fab4e9695d32fc5b08096436. Read the complete native thread and enclosing reviews, diff context, and relevant immutable source at this exact head. Do not infer a deleted original root. Determine whether the surviving author statement describes a verified repair with no unaddressed finding or request. If verified, return exactly these five authored lines, replacing REASON and PATH with your independent reasoning (at least 40 characters) and the actual source path; keep your normal native footer outside the five lines. Otherwise explain the remaining defect and do not emit the positive protocol. Do not prepend an analysis block or other live prose to a positive protocol.
Repair acknowledgement v1 for f3491a43399b35d9fab4e9695d32fc5b08096436:
Finding: PRRT_kwDOT3TWPM6vcq2j comment4355453041
Verified: complete-native-context/author-repair/no-unaddressed-finding
Reason: REASON
Evidence: https://github.com/open-hax/foresight/blob/f3491a43399b35d9fab4e9695d32fc5b08096436/PATH
```

For the second actual request, reply in `PRRT_kwDOT3TWPM6vcq20` to root
`4355453881`, with the same independently evaluated request and head, replacing
the Finding line with exactly:

```text
Finding: PRRT_kwDOT3TWPM6vcq20 comment4355453881
```

The existing native `addPullRequestReviewThreadReply`/CodeRabbit reply transport
is sufficient; no digest, new CLI, new actor or separate approval surface is
needed. An ordinary authorized request is not verification. Recollect with the
existing native-context collector and `pr.cljs threads ... --all`/`gate` after
actual Bot publication and resolution. Inspect the reply's enclosing immutable
review commit and actual readback. If the transport still attaches the old
review commit or emits unsupported live wrappers, admission remains denied:
request a properly current-head native Bot review/reply or report that concrete
transport blocker. Never rewrite the historical acknowledgement or substitute
REST comment commit metadata. This document does not authorize a source-only
child to publish requests or resolve conversations.
