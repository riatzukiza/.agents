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
