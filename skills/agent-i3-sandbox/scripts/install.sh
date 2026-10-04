#!/usr/bin/env bash
set -euo pipefail

apply=false dest=
source_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
while (($#)); do case "$1" in --apply) apply=true;; --dest) dest="${2:?--dest requires a path}"; shift;; *) echo "usage: $0 [--apply] --dest DESTINATION" >&2; exit 64;; esac; shift; done
[[ -n "$dest" && "$dest" = /* ]] || { echo '--dest must be absolute' >&2; exit 64; }
while [[ "$dest" != / && "$dest" == */ ]]; do dest="${dest%/}"; done
check_ancestors() { local p="$1"; while [[ "$p" != / ]]; do [[ ! -L "$p" ]] || { echo "refusing symlink ancestor: $p" >&2; exit 1; }; p="$(dirname "$p")"; done; }
check_ancestors "$dest"
dest_real="$(realpath -m "$dest")"
[[ "$dest_real" != "$source_dir" && "$dest_real" != "$source_dir"/* && "$source_dir" != "$dest_real"/* ]] || { echo 'refusing source/destination overlap' >&2; exit 1; }
marker="$dest/.agent-i3-sandbox-managed"
manifest="$dest/.agent-i3-sandbox-manifest"
expected_marker='managed-by=agent-i3-sandbox v1'
validate_managed() {
  [[ ! -L "$marker" && -f "$marker" && "$(<"$marker")" == "$expected_marker" ]] || { echo 'refusing forged or invalid managed marker' >&2; exit 1; }
  [[ ! -L "$manifest" && -f "$manifest" ]] || { echo 'refusing missing managed manifest' >&2; exit 1; }
  ! find "$dest" -type l -print -quit | grep -q . || { echo 'refusing symlink inside managed destination' >&2; exit 1; }
  (cd "$dest" && sha256sum -c .agent-i3-sandbox-manifest) >/dev/null || { echo 'refusing locally modified managed files' >&2; exit 1; }
}
if [[ -e "$dest" ]]; then validate_managed; fi
if ! $apply; then echo "dry run: would install $source_dir to $dest"; exit 0; fi
parent="$(dirname "$dest")"; mkdir -p "$parent"; check_ancestors "$parent"
stage="$(mktemp -d "$parent/.agent-i3-sandbox-stage.XXXXXX")"; trap 'rm -rf "$stage"' EXIT
cp -a "$source_dir"/. "$stage"/
rm -f "$stage/.agent-i3-sandbox-managed" "$stage/.agent-i3-sandbox-manifest"
(cd "$stage" && find . -type f ! -name .agent-i3-sandbox-manifest -print0 | sort -z | xargs -0 sha256sum) >"$stage/.agent-i3-sandbox-manifest"
printf '%s\n' "$expected_marker" >"$stage/.agent-i3-sandbox-managed"
if [[ -e "$dest" ]]; then
  cmp "$manifest" "$stage/.agent-i3-sandbox-manifest" >/dev/null || { echo 'refusing update: managed destination differs from source; preserve it and choose a new destination' >&2; exit 1; }
  echo "managed install already current: $dest"; exit 0
fi
mv -T -n "$stage" "$dest"
[[ ! -d "$stage" ]] || { echo 'refusing concurrent destination creation' >&2; exit 1; }
trap - EXIT
echo "installed managed sandbox skill at $dest"
