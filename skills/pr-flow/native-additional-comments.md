# Native Additional-comment envelopes

Source candidate for the presentation emitted in
[Proxx review 5649047508](https://github.com/open-hax/proxx/pull/464#pullrequestreview-5649047508).
The captured REST review remains unchanged in
`scripts/fixtures/proxx-native-dl-review-5649047508.json`.

The report declares 56 additional items: 55 unique affirmative LGTM notes and
one unmarked performance concern. Treating all of it as positive would lose a
real obligation. Treating its 55 positive IDs as unknown corrections prevents
the existing native `Handled` rule from applying.

The compatibility branch accepts the observed balanced `dl`/`dd` containers,
known native picture wrappers and constrained native asset tags, plus passive
HTTPS autolinks outside the item content. The legacy branch remains unchanged.
File-group counts must match the section count. In the native DL branch, each
group’s actual item-part count must match its declaration before any positive
items are admitted. Legacy sections retain their existing independent per-group
positive-item checks; an invalid sibling does not suppress an otherwise valid
legacy group. Surplus unmarked parts retain a whole-body
obligation and deny positive admission. Non-group source must be blank,
and positive item contents still match the complete existing LGTM grammar.
A structurally bounded section whose declared total does not reconcile retains
a conservative whole-body finding; it never gains positive-item admission.
A recognizable authored native DL opening whose structure fails parsing also
retains that obligation. Quoted, fenced and generated examples are excluded
from this conservative recognition; positive admission still requires the full
strict parser.
For conservative discovery, inline-code matching stops at raw native
Additional-comment block openings: a dangling delimiter in an earlier paragraph
cannot own that HTML block. The stricter positive-admission multiline-code veto
is unchanged. Every present image/source `src` and every `srcset` candidate must
use the constrained public-assets URL; one allowed attribute cannot conceal a
foreign URL in another attribute.
Complete fenced, inline-code, quoted-paragraph and raw pre/code/blockquote
examples are excluded before generated-region openers are recognized, so
example comments cannot claim ownership of later authored prose. Closing fences use the opener's character and at least its length, with only
whitespace afterward. Unmatched tails are masked before legacy scope scanning. Only zero-to-three-space
Markdown fence indentation can own such a tail; code-indented literal delimiters
cannot hide a later authored section.
Unknown tags, attributes, mismatched wrappers, quoted/fenced source, duplicate
IDs and corrective prose cannot acquire positive status.

Recognition preserves raw review data and does not alter identity, timestamps,
severity, current-head binding, writer permission, outstanding unmarked items,
review approval, cohort, CI or merge rules. Recognition alone has no GitHub
effect. Synthetic writer tests are fixtures, not native settlement evidence.

Validation:

```sh
nbb -cp skills/pr-flow/scripts skills/pr-flow/scripts/test_native_dl.cljs
nbb -cp skills/pr-flow/scripts skills/pr-flow/scripts/test_law.cljs
nbb -cp skills/pr-flow/scripts skills/pr-flow/scripts/test_policy.cljs
nbb -cp skills/pr-flow/scripts skills/pr-flow/scripts/test_cli.cljs
```

This source is not installed in the active global skill directory. Protected
review and adoption precede operational use.
