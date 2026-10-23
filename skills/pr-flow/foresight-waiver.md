# Foresight MiMo waiver

License: GPL-3.0-or-later.

Err reaffirmed on October 22, 2026 that the MiMo waiver for
`riatzukiza/foresight` must be reconciled with the gate without weakening
deterministic checks. The [October 21 merge record](https://github.com/riatzukiza/foresight/pull/47#issuecomment-6328083797)
records the earlier waiver and exact reviewed head
`65f4816b26b49f563518b4fa38c89f07b48f8e5c`.

## Acceptance criteria

- Exact `riatzukiza/foresight` scope; no basename matching or other-owner lift.
- Require both trusted native CodeRabbit and Codex current-head approvals.
- Remove only MiMo from the current review cohort, preserving observed
  availability, source and reason. Explicit mandatory requirements win.
- Keep the configured historical full-roster rounds. The existing completed
  current cohort can supply one round, never five artificial rounds.
- Keep every review finding and body settlement obligation, including MiMo's.
- Keep deterministic, unknown and required checks blocking on failure or
  pending state, and required skipped/cancelled checks blocking. Do not
  reclassify the aggregate evidence gate as a provider output.
- The upstream reusable workflow may omit only the waived model's aggregate
  success requirement. It must retain deterministic success/output,
  exact revision, clean-tree and compilation requirements. The MiMo job keeps
  its actual status and cannot grant approval by running or failing.

## Implementation and adoption

`flow.edn` owns the exact repository policy; pure laws resolve mandatory
requirements and cohort participation; the existing CLI consumes both.
Identity and review evidence collection are unchanged. No policy is loaded
from candidate repository content or PR comments. The comment above is
provenance, not an executable authorization parser.

This source change is a review candidate until its PR qualifies and merges.
Do not replace the live skill catalog with unreviewed source. Foresight also
needs a reviewed immutable reusable-workflow pin and a fresh successful
aggregate run. Updating source cannot retroactively qualify PR #47's failed
run or demonstrate protected-branch activation. Revoke the waiver explicitly
in the trusted catalog and restore hosted model qualification together once
MiMo is restored; a failed/passing model run does not silently change policy.

## Verification

The policy and isolated CLI suites exercise both approvals, a completed
current cohort, unrelated repository scope, mandatory MiMo restoration,
malformed policy, missing/stale approvals, findings, changed heads and
failed/pending/skipped required deterministic checks. Existing law and CLI
suites remain required. Hosted checks and independent native review are
separate qualification evidence.
