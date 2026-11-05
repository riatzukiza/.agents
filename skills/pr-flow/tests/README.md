# Verification

The existing executable regression suites remain under `scripts/` to preserve
their canonical paths. This bundle includes those suites and their local fixtures.
Run the portable subset from this skill directory:

```sh
nbb -cp scripts tests/run.cljs
```

This runs the existing raw-review classification and typed-verdict suites. It
does not qualify a native swarm or exercise live provider publication. The
native profile uses UltraCode's native evidence suite and checks its source
and instructions separately.

The complete hosted-profile law/policy suites run in the canonical `.agents`
checkout through `.github/workflows/pr-flow-laws.yml`. Some intentionally read
historical native evidence under the repository's `.ημ/review-evidence/`; those
external repository fixtures are not copied or rewritten inside this skill.
Use the full repository for that suite. No hosted-provider CLI behavior changes
are part of the native profile addition.
