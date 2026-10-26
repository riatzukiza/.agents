# Proposed skill placement contract v1

Status: proposed vocabulary and agent-readable authoring guidance. Existing
loaders, schedulers and Unison do not interpret these fields. This proposal
neither enables an automatic capability router nor changes sync filters.

A shared skill pack may describe several machines without being executable on
all of them. Keep three independent questions explicit:

1. **Distribution**: may these bytes be replicated, or must the installed file
   remain local to its owning host?
2. **Execution**: which host or site is permitted to run this procedure/profile?
3. **Requirements**: what hardware, runtime, permissions and current resources
   must be demonstrated before this particular workload starts?

## Optional `placement` form in CONTRACT.edn

Use `(placement {...})` in an existing `skill-contract`; examples are in
[placement-examples.edn](placement-examples.edn). It is additive: skills without
it retain their existing meaning. No unknown field silently grants execution.

- `:schema` identifies this proposed vocabulary.
- `:execution` contains optional stable `:hosts`, `:requires-all`, and
  `:on-unknown :stop-and-report`. Host labels describe locations; requirements
  describe capabilities. Remote execution needs separate authorization.
- `:files` is a vector of explicit file/directory entries. `:distribution` is
  `:shared` or `:host-local`. Optional `:applies-to` narrows the entry to host
  labels; all applicable restrictions combine with AND. Unmatched files inherit
  existing skill/sync behavior; they are never implicitly made local/private.
- `:evidence` points to the current observation or workload qualification, its
  time and validity scope. Missing, stale or contradictory evidence is unknown.

A path marked shared may carry a Yoga-only configuration *template*. Replicating
that template on Stealth does not permit selecting or installing it there.
The installed/generated instance can be host-local even when its source is shared.
Do not use timestamps or GPU vendor names to choose between conflicting profiles.

## Local files and the current synchronization boundary

Today Unison shares `skills/`, `skills.disabled/`, and `docs/notes/`.
**A contract declaration alone does not exclude a file from synchronization.**
Put actual local runtime state, device probes, compiled caches and credentials
outside those roots, e.g. `~/.local/state/agent-skills/<skill>/` or the service's
existing protected configuration directory. Keep source templates in the pack.
Do not introduce `skills/<skill>/local/` and assume it is ignored; it is not.

A future in-pack local-file convention must first add reviewed native Unison
exclusions and prove non-propagation in both directions. No second sync engine,
custom board parser, hidden relocation or automatic credential distribution is
part of this proposal.

## Hardware presence versus workload eligibility

A host observation can establish CPU model, installed memory, PCI devices,
visible touchscreen and driver/device-node presence. It cannot establish that a
particular model runs correctly, fits memory, supports a precision, or meets a
latency/training target. Record those as separate workload qualifications.

For inference and training:

- Re-probe free RAM, free device memory, current reservations, thermals/power and
  concurrent work at admission time. Installed capacity is not a free-memory budget.
- Distinguish dedicated NVIDIA device memory from AMD/Intel shared-system memory;
  never add shared graphics memory to system RAM as if it were another pool.
- Require the actual backend and operation: CUDA/ROCm/Vulkan/OpenVINO/CPU and
  inference/training support. An AMD GPU is not evidence of working ROCm, and an
  Intel NPU is not evidence of model/operator/precision support.
- Budget model weights, cache, runtime overhead and, for training, activations,
  gradients, optimizer state, batch size and precision. A successful tiny
  inference probe does not qualify training or a larger context.
- Keep CPU fallback, migration to another host and downloads explicit. A failed
  requirement means stop/report or choose a separately authorized eligible profile.

## Initial observations

[Stealth](hosts/stealth.edn) and [Yoga](hosts/yoga.edn) are dated hardware snapshots
from read-only commands in this session. Live usable memory differs from installed
memory and changes continuously. No new model execution, GPU training, Intel Arc
backend, NPU backend or ROCm qualification was performed for this proposal.

## Adoption and verification

Before declaring an operational enforcement layer, identify its native consumer,
validate the vocabulary there, and test host mismatch, missing capability, stale
observations, workload-specific memory budgets, both-way local-file isolation,
and preservation of existing unannotated skills. Source review and a parsed EDN
file do not establish loader or scheduler enforcement.
