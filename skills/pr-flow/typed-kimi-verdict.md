# Candidate typed Kimi decision boundary

The [accepted plan](typed-kimi-verdict-plan.md) merged in PR48. The first
implementation slice is the pure `pr-flow.kimi-verdict` module and its registered
tests. It is not imported by the live CLI, publisher or admission law. Existing
v1 publications, wording rules and evidence remain unchanged.

`response-schema` binds a closed version 2 JSON response to trusted repository,
PR number, base/head SHAs, diff digest and ordered changed-file coverage. The
completed model response must explicitly supply `verdict`, `coverage`, `summary`
and `comments`. Execution identity and runtime provenance do not belong in this
model-owned decision object. Unknown fields and versions refuse.

`publication-intent` validates that response against the trusted coordinates and
returns either a typed refusal or a proposed native review event with
`:authority :proposal-only`:

| Decision | Coverage | Findings | Proposed event |
| --- | --- | --- | --- |
| `approve` | `complete` | none | `APPROVE` |
| `changes_requested` | `complete` | at least one | `REQUEST_CHANGES` |
| `comment` | either | zero or more | `COMMENT` |
| `approve` | `incomplete` | none | `COMMENT` |
| `changes_requested` | `incomplete` | at least one | `COMMENT` |

Approval with findings and an empty change request contradict their explicit
decisions and refuse. Finding paths must be covered tracked-source coordinates;
publication and changed-line attachment remain the publisher's separate work.
Blank or excessive text, malformed locations, excessive aggregate publication
size and duplicate/empty coverage refuse. Summary prose never selects an event.

The next owning Proxx adapter must require an actual completed authenticated
structured assistant response, preserve protected runtime and exact full-input
custody, keep helper-owned execution provenance separate, and use the pure law
before any App mutation. Idempotence, uncertain writes and native readback must
bind the version, decision and actual event. Canonical v2 admission must then be
reviewed independently and qualified with a real accepted invocation before use.
Neither the JSON schema nor this pure proposal establishes those facts. No
classifier output, native review, approval, settlement or deployment authority is
created by this implementation slice.
