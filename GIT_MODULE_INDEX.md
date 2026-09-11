# Git module index — `~/.agents` on knoxx

Status of this repository within the machine's git federation.

## Parent

- `/home/err` — the home-directory superproject, branch `device/knoxx`.
  This repository is wired into it as a gitlink (submodule pointer); the
  superproject records which child SHA is active, not the content itself.
  The gitlink advances only by explicit commits: change the child, commit
  the child, then commit the new gitlink SHA in the parent.

## Children

- None. This repository is a leaf: there are no nested submodules and no
  `.git` directories under `skills/*/` (verified 2026-09-11). Every skill
  directory is plain versioned content of this repository.

## Notes

- Branch of record here: `device/knoxx`, tracking `origin/main` lineage
  (currently a straight descendant of `e10b6ca`).
- `skills/.skill-lock.json` carries provenance metadata for imported skills;
  it is tracked content, not a submodule boundary.
