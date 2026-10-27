#!/usr/bin/env bash
# Workspace-only files: did Golden's history ever contain this exact content?
#   in history      -> Golden deleted it on purpose; do not resurrect.
#   unknown         -> never part of Golden; candidate to keep after review.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BK="$ROOT/_merge_20261026"
WS="$ROOT/RED_Ultimate_V1-main/RED_Ultimate"
GD="/d/RED_Golden/RED_Ultimate_V1-main/RED_Ultimate"
GIT=(git -c safe.directory="$GD" --git-dir="$GD/.git")
OUT="$BK/ONLY_WS_VERDICT.tsv"

: > "$OUT"
while IFS=$'\t' read -r path _status _a _b; do
  case "$path" in
    lib/*|core/*|feature/*) echo -e "$path\tQUARANTINE_THIRD_PARTY" >> "$OUT"; continue ;;
  esac
  sha=$("${GIT[@]}" hash-object -- "$WS/$path")
  if "${GIT[@]}" cat-file -e "$sha" 2>/dev/null; then
    echo -e "$path\tDELETED_IN_GOLDEN" >> "$OUT"
  else
    echo -e "$path\tNEVER_IN_GOLDEN" >> "$OUT"
  fi
done < <(awk -F'\t' '$2=="ONLY_WS"' "$BK/DIFFS.tsv")

echo "=== workspace-only verdicts ==="
awk -F'\t' '{c[$2]++} END {for (k in c) printf "%-24s %5d\n", k, c[k]}' "$OUT" | sort
echo "=== never-in-golden by area ==="
awk -F'\t' '$2=="NEVER_IN_GOLDEN"{ split($1,a,"/"); print a[1]"/"a[2] }' "$OUT" | sort | uniq -c | sort -rn | head -20
