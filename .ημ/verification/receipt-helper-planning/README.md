# Receipt helper planning evidence

Run `python3 .ημ/verification/receipt-helper-planning/reproduce.py` from this
repository to repeat the bounded observation against its current helper source.
This planning harness reports outcomes; it is not a green implementation gate.

The recorded baseline at main `8a7e588b3f55e69c6aa9212c9679646e772e09a7`
contains 16 observations: five controls pass and eleven desired behaviors fail.
Both ordinary root finders and historical receipt prefixes pass. Omitted,
comma-separated, and explicit EDN collection encodings remain strings. Both
linked-worktree finders select disposable ancestor metadata; the receipt and
reflection CLI writers change only those fixture-owned ancestor sentinels.
Invalid Git markers also fall through to that disposable ancestor.

Fixtures use real temporary Git repositories/worktrees, record exact pre-call
sentinel bytes, and remove only their owned temporary directory. No real shared
ledger or global helper is changed. Temporary paths in the captured JSON are
historical fixture evidence and no longer exist.

An initial uncommitted private harness had a duplicated skill-directory segment
in its direct-finder calls. Its file-not-found outcomes were not root-discovery
evidence. That harness bug was fixed before this recorded baseline; the current
source and captured output use valid canonical helper paths.

## Child environment isolation, 2026-10-06

Native review comment `4196084622` identified that inherited Git variables could redirect fixture commands outside disposable roots. Every child command now receives a copied environment with all `GIT_*` overrides removed; unrelated variables are retained and the caller environment is unchanged. This is fixture safety, not a helper implementation fix.

`python3 -m unittest discover -s .ημ/verification/receipt-helper-planning -p test_reproduce.py -v` loads only the `run` function and intercepts both Git and helper subprocess calls. Before the fix it fails once; after the fix it passes. Re-running the full fixture normally and with nonexistent `GIT_DIR`, `GIT_WORK_TREE`, and `GIT_INDEX_FILE` produces the same 16 case outcomes: five controls pass and eleven desired helper contracts fail. Those remaining failures continue to describe the planned implementation, not successful repair. Historical baseline bytes remain unchanged.
