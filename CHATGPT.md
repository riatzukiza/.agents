# ChatGPT Adapter

## Scope

ChatGPT sessions may expose different combinations of web search, uploaded files, File Library, Python, a temporary filesystem, connected GitHub/Drive/Gmail/Calendar tools, image/document generation, or scheduled automations.

The presence of ChatGPT does not imply a local repository checkout or unrestricted shell. Run `environment-classifier` first.

## Discovery

1. Read the active tool and permission descriptions.
2. Determine whether repository access is:
   - local checkout,
   - GitHub connector/API,
   - uploaded archive or file reference,
   - or absent.
3. Determine whether generated files are in a temporary sandbox and provide durable links when required.
4. Determine whether current information requires web verification.
5. Read applicable repository instructions and the smallest relevant skills.

## Process projection

### Local or code-execution sandbox

- Confirm the exact writable roots and whether the repository is actually mounted.
- Do not infer that an uploaded or connector file exists in the local sandbox.
- Run only checks supported by the environment.
- Return created artifacts with explicit links.

### GitHub connector

- Treat connector reads/writes as repository API operations, not local shell execution.
- Use a branch and pull request for substantive changes.
- Preserve Receipt River append-only semantics by fetching the current blob and appending new lines.
- Do not claim local builds, scripts, hooks, or Git commands ran.
- Use stacked branches when a new change depends on an unmerged constitutional PR.

### Conversation or research only

- Deliver evidence-linked analysis, patches, or file contents.
- Mark unperformed mutations and checks.
- Do not promise background work. Use an actual automation only when the user requests future or recurring execution and the automation is created.

## Skills

ChatGPT may not natively auto-discover `~/.agents/skills`.

When repository access is available:

1. inspect applicable `AGENTS.md`,
2. locate referenced `SKILL.md` files,
3. read only the relevant skills,
4. state any capability mismatch,
5. execute the strongest supported projection.

## Optional eta-mu/Muse integration

When only connector access exists, do not attempt to install local tooling.

When a real writable checkout and shell exist, `process-bootstrap` may guide setup. Muse-generated artifacts remain projections; edit host-agnostic sources when they exist instead of patching generated output.
