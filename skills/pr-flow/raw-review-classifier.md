<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# Raw review classifier

The user requested a fast classifier because review presentation changes too
often for HTML extraction to determine intent reliably. This candidate sends
the complete raw native review to a model and validates structured output.
It does not replace the installed gate or count as independent verification,
settlement, approval, review coverage, deployment evidence or source adoption.

The read-only CLI accepts a captured REST review plus matching GraphQL native
review in the same format as `scripts/fixtures/native-receipt4-dl-review.json`.
The fixture preserves native review 5641463808 and its exact original body.
It contains six acknowledgements and an unmarked provenance request. A useful
classifier must retain that request despite the adjacent LGTM text.

```sh
nbb -cp skills/pr-flow/scripts skills/pr-flow/scripts/classify_review.cljs \
  CAPTURE.json OWNER/REPO PR CURRENT_FULL_HEAD \
  http://127.0.0.1:8789/v1/chat/completions MODEL chat

nbb -cp skills/pr-flow/scripts skills/pr-flow/scripts/classify_review.cljs \
  CAPTURE.json OWNER/REPO PR CURRENT_FULL_HEAD \
  https://opencode.ai/inference/systemone/v1/systemone jev-1.13-free systemone
```

For an explicit remote endpoint, provide its credential as
`REVIEW_CLASSIFIER_API_KEY` through the caller's secret store. The existing
`PROXX_API_KEY` is used only for the exact local origin `http://127.0.0.1:8789`.
Credentials never enter arguments, output, captured source or provenance.
There is one bounded invocation, no automatic retry, model substitution or
paid fallback. Exit 0 means structurally valid classification; exit 2 means
uncertainty or failure. Neither code supplies gate authority.

Line packing preserves every character and all whitespace. Each unit is
classified in the context of the whole review. Input over 49,152 code units or
64 units refuses before inference. Nothing is silently truncated. Complete
native IDs, actor, source commit, latest edit, current head, body digest,
prompt and response schema bind the request. Captured identity consistency
is checked deterministically; a capture does not prove freshness or permission.

Chat output must account for each unit in order, with exact unique source
quotes and one of `actionable`, `informational`, `uncertain`. Unknown fields,
missing or duplicate units, ambiguous quotes, invented text and truncated
responses refuse. This checks custody and coverage; it cannot prove that a
model has found every semantic concern or resisted every injected instruction.

Jev evaluates one parallel typed Choice question per unit against the complete
raw state. The law validates every answer, option and probability, and retains
the full unit as evidence rather than manufacturing a model explanation.
Confidence below 0.9 or winning probability below 0.95 produces `uncertain`.
These thresholds are provisional, conservative routing settings, not measured
accuracy or a guarantee of calibrated correctness. Native evaluation and an
adversarial corpus remain required before gate integration.

As checked 2026-10-25, the [v2 Console documentation](https://opencode.ai/v2/docs/console/models)
lists Jev 1.13 Free and paid Jev at $0.042 per million input tokens with no
output charge. GPT-6 Luna is $0.10 input / $0.50 output per million tokens for
contexts at most 272K. The [harness documentation](https://opencode.ai/v2/docs/console/harnesses)
restricts free chat models to the actual OpenCode client. Do not spoof a client
or tunnel that restriction through Proxx. Free-model availability can change.
Consult the [typed Choice API](https://docs.typesafe.ai/primitives/choice) for
the native System One response shape.

Initial live qualification is incomplete: local Proxx returned 429 for
GPT-6.1 Sol and GPT-6 Luna, and 502 for MiMo Flash; the local Gemma call produced no usable
response. Actual OpenCode v2.1.3 reached the free-model provider but returned
401 `api_key_expired`. A separately isolated signed-out OpenCode v2 profile
reached the free provider but returned 429 `FreeUsageLimitError`, with a
64,490,000 ms retry interval. An expired account key and anonymous quota are
different failures. A new Console key is needed for account inference;
signing in does not renew the expired key. No successful live classification
is claimed. No parser-format patch was installed as a bypass.
