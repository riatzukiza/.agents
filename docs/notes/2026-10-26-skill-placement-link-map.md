# Skill placement: observed links and proposed outcome

User intent: "a skill, or files that are a part of that skill pack, are unique to a location"; host hardware influences inference and training choices.

Observed: `61314fa` introduced the shared catalog/Unison profile and Atlas CLI guidance; device branches remain separate. Shared catalog placement is not workload eligibility. Yoga now has a separately authenticated Atlas CLI login.

Observed: [current Unison profile](../../skills/agent-skills-sync/ops/agents-skills.prf), [Atlas guidance](../../skills/atlas-cli/SKILL.md), [existing CUDA workflow](../../skills/local-container-registry/SKILL.md), and dated [Stealth](../../skills/skill-authoring/references/hosts/stealth.edn)/[Yoga](../../skills/skill-authoring/references/hosts/yoga.edn) hardware snapshots.

Derived: a shared source template may apply to one host, while its installed state remains local. Hardware detection and workload qualification need distinct evidence. Explicit host bindings and capability predicates solve different problems.

Provisional outcome: additive [placement vocabulary and authoring guidance](../../skills/skill-authoring/references/placement.md), with examples and hardware observations. No loader, scheduler, automatic dispatch, or Unison behavior is claimed or changed. Integration should be a separately reviewed implementation story after vocabulary agreement; no board state is mutated here.
