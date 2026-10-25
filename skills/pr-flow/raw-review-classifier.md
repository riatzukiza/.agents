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

nbb -cp skills/pr-flow/scripts skills/pr-flow/scripts/classify_review.cljs \
  CAPTURE.json OWNER/REPO PR CURRENT_FULL_HEAD \
  opencode opencode/glm-5.3-flash-free opencode
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

Both transports collect bounded stdin bytes before strict UTF-8 decoding, so
a pipe buffer boundary cannot change a multibyte character. The parent also
compares the transport's payload digest with its own exact serialized payload
before accepting any chat, System One or native-client result. A missing or
different digest remains uncertain. Deterministic tests split 4-, 2- and 3-byte
characters across buffers in an 81,000-byte payload and reject altered child
digests on all three paths.

The standalone law suite declares an explicit failing-process reporter, matching
the existing suites. Its injected-failure subprocess test checks the actual exit
code. The pinned nbb 1.3.204 baseline already exited 1 without this declaration;
the explicit hook is consistency protection, not a reproduced exit-0 repair.

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

Live evaluation through the supported Console-account login and actual OpenCode
v2.1.3 client succeeded on two captured native reviews. Receipt River review
5641463808 retained its unmarked provenance request and six LGTM acknowledgements;
Axxium review5641483869 retained its unmarked wording request and ten LGTM
acknowledgements. The integrated runs took 15,993 ms and 39,400 ms. Native
session exports confirm the selected free GLM model and cost 0 for both.
These are two examples, not a measured accuracy claim or gate qualification.
The Jev adapter has transport/schema tests but no successful live evaluation yet.

The native-client adapter uses an isolated configuration/plugin directory,
the existing native auth/session store, an explicit free model and active
provider-model JSON-output settings. Paid model IDs are refused. It requires
one successful native assistant result, no tool calls, the selected model,
reported zero cost, matching event/export text and valid classifier JSON.
Invalid JSON remains unresolved and preserves the unvalidated model output
and inference provenance for diagnosis; it is not repaired by extracting a
fence or guessing omitted structure. Native client/provider internals may
have their own retry behavior; the adapter does not submit a second run.
Only temporary files it creates are removed; no Docker artifacts are created.

Following native review5641957517, the candidate now denies host read/glob/grep
as well as all other actions, with only the exact no-op shell command `true`
registered for the native client. The complete review arrives as the supplied
attachment; file discovery is unnecessary. Any actual tool call still refuses
the result. A synthetic client checks this exact permission configuration, and
the actual nbb CLI now has a complete opencode-dispatch/validation test.
The first live call after this restriction returned429 before inference, so
the two earlier successful native runs do not qualify the restricted runtime.
No automatic retry or paid fallback was attempted.

Account and signed-out routes have separate availability. An expired Console
API key returned401; supported Console-account device login subsequently
connected the existing workspace without copying credentials. A separately
isolated signed-out profile returned429 `FreeUsageLimitError` with a
64,490,000 ms retry interval. Local Proxx also returned429 for Sol/Luna and502
for MiMo. These failures remain failures, and no paid fallback is automatic.
The previous parser-format extension remains uninstalled.
