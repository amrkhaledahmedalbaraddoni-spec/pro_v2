#!/usr/bin/env bash
# Build the canonical tree in the workspace:
#   - Golden's Git history is copied in, so the merged tree is a real repository.
#   - Every file Golden tracks/holds is written over the workspace copy (Golden wins:
#     its HEAD is newer and its worktree carries today's admin changes).
# Extras are handled separately (quarantine), never deleted here.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BK="$ROOT/_merge_20261026"
WS="$ROOT/RED_Ultimate_V1-main/RED_Ultimate"
GD="/d/RED_Golden/RED_Ultimate_V1-main/RED_Ultimate"

echo "=== 1) copy git history ==="
if [ -d "$WS/.git" ]; then
  echo "workspace already has .git — refusing to overwrite"
  exit 1
fi
cp -a "$GD/.git" "$WS/.git"
chmod -R u+w "$WS/.git" 2>/dev/null || true
git -c safe.directory="$WS" -C "$WS" log --oneline -1

echo "=== 2) materialise every Golden file in the workspace tree ==="
copied=0; same=0
while IFS=$'\t' read -r path _size _mtime _sha; do
  src="$GD/$path"; dst="$WS/$path"
  if [ -f "$dst" ] && cmp -s "$src" "$dst"; then same=$((same+1)); continue; fi
  mkdir -p "$(dirname "$dst")"
  cp -p "$src" "$dst"
  copied=$((copied+1))
done < "$BK/manifests/gd.tsv"
echo "golden files: copied/updated=$copied  already-identical=$same"

echo "=== 3) repository status ==="
git -c safe.directory="$WS" -C "$WS" status --porcelain 2>&1 | awk '{print $1}' | sort | uniq -c
echo "tracked=$(( $(git -c safe.directory="$WS" -C "$WS" ls-files | wc -l) ))"
