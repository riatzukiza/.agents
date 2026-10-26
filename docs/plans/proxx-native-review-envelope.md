# Preserve native Additional-comment intent across the new envelope

Dependency of [Proxx release PR 464](https://github.com/open-hax/proxx/pull/464).

Outcome: apply the existing bounded positive-body law to the observed native
CodeRabbit envelope without losing its unmarked corrective item.

Scope: pure parser compatibility, untouched native fixture, negative and
permission/head tests, and the existing CI law suite.

Non-goals: changing approval or quorum, settling product findings, installing
unreviewed policy, inventing reviewer identity, or accepting generic HTML.

Acceptance: the captured report yields 55 positive body items plus one P1
whole-body obligation. Valid synthetic acknowledgments clear only the 55;
wrong-head, unauthorized and unknown-bot fixtures clear none. Existing law,
policy and CLI suites retain their behavior.
