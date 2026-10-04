#!/usr/bin/env bash
set -euo pipefail

root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
"$root/scripts/install.sh" --dest "$tmp/new" | grep -F 'dry run'
mkdir "$tmp/conflict"
if "$root/scripts/install.sh" --apply --dest "$tmp/conflict"; then echo 'unowned destination accepted' >&2; exit 1; fi
ln -s "$tmp/conflict" "$tmp/link"
if "$root/scripts/install.sh" --apply --dest "$tmp/link"; then echo 'symlink destination accepted' >&2; exit 1; fi
"$root/scripts/install.sh" --apply --dest "$tmp/managed"
"$root/scripts/install.sh" --apply --dest "$tmp/managed"
[[ -f "$tmp/managed/.agent-i3-sandbox-managed" ]]
refuses() { if "$root/scripts/install.sh" --apply --dest "$1"; then echo "unsafe destination accepted: $1" >&2; exit 1; fi; }
mkdir "$tmp/real-parent"
ln -s "$tmp/real-parent" "$tmp/symlink-parent"
refuses "$tmp/symlink-parent/new"
[[ ! -e "$tmp/real-parent/new" ]]
printf 'sentinel\n' > "$tmp/external"
rm "$tmp/managed/.agent-i3-sandbox-managed"
ln -s "$tmp/external" "$tmp/managed/.agent-i3-sandbox-managed"
refuses "$tmp/managed"
[[ "$(<"$tmp/external")" == sentinel ]]
rm "$tmp/managed/.agent-i3-sandbox-managed"
printf 'forged\n' > "$tmp/managed/.agent-i3-sandbox-managed"
refuses "$tmp/managed"
printf 'managed-by=agent-i3-sandbox v1\n' > "$tmp/managed/.agent-i3-sandbox-managed"
printf 'local edits\n' >> "$tmp/managed/SKILL.md"
refuses "$tmp/managed"
refuses "$root"
refuses "$root/nested-install"
"$root/scripts/install.sh" --apply --dest "$tmp/extras"
ln -s "$tmp/extras" "$tmp/managed-link"
refuses "$tmp/managed-link/"
printf 'preserved\n' > "$tmp/extras/user-file"
"$root/scripts/install.sh" --apply --dest "$tmp/extras"
[[ "$(<"$tmp/extras/user-file")" == preserved ]]
"$root/scripts/install.sh" --apply --dest "$tmp/concurrent" >"$tmp/concurrent-one.log" 2>&1 & one=$!
"$root/scripts/install.sh" --apply --dest "$tmp/concurrent" >"$tmp/concurrent-two.log" 2>&1 & two=$!
first=0; wait "$one" || first=$?
second=0; wait "$two" || second=$?
(( first == 0 || second == 0 )) || { echo "concurrent installation had no successful writer: $first/$second" >&2; exit 1; }
[[ -f "$tmp/concurrent/.agent-i3-sandbox-managed" ]]
[[ "$(grep -hF 'installed managed sandbox skill' "$tmp"/concurrent-*.log | wc -l)" == 1 ]]
if compgen -G "$tmp/.agent-i3-sandbox-stage.*" >/dev/null; then
  echo 'concurrent installation left a staging directory' >&2
  exit 1
fi
echo 'installer checks passed'
