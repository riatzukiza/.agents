# Native evidence format v1

The coordinator writes a fixed plan as an EDN map before dispatch and records returned evidence as a separate EDN vector. The CLI computes SHA-256 over the exact plan file bytes and uses that as every receipt's expected `:input-hash`. The initial roster includes reviewer-scoped skeptics even when reviewers may find nothing. Findings do not add jobs or modify the plan.

```edn
;; PLAN.edn
{:snapshot "<64 lowercase hex SHA-256 of the immutable snapshot manifest>"
 :round 1 :capacity 4 :limited? false
 :inputs {:prompt "<digest or immutable content>" :roles "<digest or immutable content>"
          :schemas "<digest or immutable content>" :packets "<digest or immutable content>"
          :gates "<digest or immutable content>" :model "<host model identity>"}
 :jobs [{:id "review" :kind :review :deps [] :writes [] :attempt 1}
        {:id "gate" :kind :gate :deps [] :writes [] :attempt 1}
        {:id "skeptic" :kind :skeptic :deps ["review"] :writes [] :attempt 1 :author-job "review"}]}
;; RECEIPTS.edn -- input-hash is SHA-256 over the exact plan file bytes
[{:job-id "review" :attempt 1 :snapshot "<snapshot digest>" :input-hash "<plan SHA-256>"
  :round 1 :context-id "review-context" :context-mode :fresh :spawn-ref "review-spawn"
  :status :complete :output {:scope-complete true :findings []}}
 {:job-id "gate" :attempt 1 :snapshot "<snapshot digest>" :input-hash "<plan SHA-256>"
  :round 1 :context-id "gate-execution" :status :complete :exit-code 0 :output {:passed true}}
 {:job-id "skeptic" :attempt 1 :snapshot "<snapshot digest>" :input-hash "<plan SHA-256>"
  :round 1 :context-id "skeptic-context" :context-mode :fresh :spawn-ref "skeptic-spawn"
  :status :complete :upstream-hashes {"review" "<review-output SHA-256>"}
  :output {:scope-complete true :additional-findings [] :votes []}}]
```

`sha256 FILE` computes a file digest. `output-hash RECEIPTS.edn JOB-ID` parses the file, finds the sole receipt for the job, and computes SHA-256 over the UTF-8 `pr-str` representation of its EDN `:output`. Supply that digest to the skeptic as the binding for the review output it receives. `check PLAN.edn RECEIPTS.edn [RESULT.edn]` independently recomputes each output digest from the actual supplied receipts. Inputs must contain exactly one EDN form, with no trailing forms or garbage. With no result path, it prints the report; with a path, it publishes atomically using a create-only operation. Existing files, aliases of inputs, and symlink destinations are not overwritten. Exit code 0 means `:complete-clean`, 1 means another qualification result, and 2 means malformed input or CLI error. Use the same CLI/runtime for hash generation and qualification, as printed EDN map ordering is part of this representation.

The snapshot manifest should identify the repository, base and head, tracked tree, and any uncommitted or untracked content under review; the coordinator must produce and verify this manifest and its digest. The plan must pin or include all prompts, role instructions, output schemas, packets, gates, models, and other material inputs; resolve mutable references to verified immutable content before dispatch. The validator checks the digest's shape and receipt equality, but cannot establish that the digest represents the actual checkout or supplied references match their contents. Any plan-byte change, including a new round, invalidates old receipts.

A qualifying wave contains only `:review`, `:skeptic`, and `:gate` jobs against one frozen snapshot. An `:implement` job may appear for advisory planning, but always yields `:incomplete` with `:unfrozen-wave`; implementation and integration must finish before a separate immutable review round. Reviewers and skeptics have a read-only role; use available enforced read-only controls or snapshots, and record shared permissions when stronger enforcement is unavailable. Verify that the reviewed source remains unchanged. Gates must run against the frozen source; any test artifacts must be written outside that source snapshot. All jobs are required. IDs are unique nonempty strings; dependency IDs must exist and form a DAG. `:writes` lists literal normalized repository-relative path prefixes, without glob syntax, `.` or `..` segments. Only implementation jobs may declare writes; overlapping prefixes require dependency order, but a wave containing any implementation job cannot qualify clean. Dependencies indicate successful completion. The coordinator owns write-boundary enforcement and records its actual mechanism and limits. `:limited? true` is always incomplete. At least one review and gate are required, and each review must have one or more predeclared independent skeptic jobs, each with `:author-job` naming the review and a dependency path to it.

Completed output shapes are review `{:scope-complete true :findings [{:id "unique-id" :title "claim" :detail "evidence"}]}` and gate `{:passed true}`. Missing or false reviewer scope completion is incomplete. A skeptic output is `{:scope-complete true :additional-findings [] :votes [...]}`. Each vote is `{:finding-id "unique-id" :verdict :confirmed|:refuted|:uncertain :reason "evidence"}`. Every skeptic must provide exactly one vote for every finding from its assigned review, with no extra or duplicate votes. Empty votes are valid only when that review found nothing. Incomplete scope, uncertainty, missing output, invalid findings, missing votes, or invalid coverage is incomplete. Skeptics must independently inspect the full assigned scope and place any additional findings in `:additional-findings` using the same ID/title/detail shape. A confirmed vote or any additional finding yields `:findings`, requiring a new repair/review round. All-refuted or zero-finding reviews can qualify clean only with all required skeptics complete. False gate/implement results, failed status, or supplied nonzero exit code yield `:failed`. A mismatched plan, snapshot, round, attempt or previously recorded reviewer output hash is `:stale`.

Receipts carry job ID, attempt, snapshot, plan hash, round, context ID and status (`:complete`, `:failed`, `:timeout` or `:running`). Review and skeptic receipts also require `:context-mode :fresh` and a nonempty `:spawn-ref`. Review and skeptic contexts and spawn references must each be unique across those jobs; contexts must also differ from every implementation context. A spawn reference identifies an actual native task invocation, not a generic tool URL. A completed skeptic receipt must bind exactly its author review's output digest in `:upstream-hashes`.

The report includes status, problems, and ready job IDs constrained by capacity and successful dependencies. Readiness is advisory; failed jobs block dependents, while independent eligible jobs can remain ready. The validator does not spawn, await, cancel or impersonate an agent, nor authenticate claimed context or execution. It cannot enforce OS permissions, establish isolation, prove tests ran, assess findings' truth, or grant GitHub approval or merge authorization. The coordinator must independently verify these properties.
