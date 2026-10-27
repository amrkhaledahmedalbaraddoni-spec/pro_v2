#!/usr/bin/env bash
# For every path whose content differs between the two trees, ask Git whether each
# version is a blob that already exists in Golden's history.
#
#   ws blob in history  -> workspace holds an OLD revision; Golden wins.
#   ws blob unknown     -> workspace holds content Git never saw (a real edit).
#   gd blob unknown     -> Golden holds uncommitted/new content.
#
# A blob that is unknown on both sides is a genuine conflict needing review.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BK="$ROOT/_merge_20261026"
WS="$ROOT/RED_Ultimate_V1-main/RED_Ultimate"
GD="/d/RED_Golden/RED_Ultimate_V1-main/RED_Ultimate"
OUT="$BK/BLOB_VERDICT.tsv"

: > "$OUT"
counts_old=0; counts_wsnew=0; counts_bothnew=0; counts_gdnew=0

while IFS=$'\t' read -r path status _wsm _gdm; do
  [ "$status" = "SAME" ] && continue
  case "$status" in ONLY_WS|ONLY_GD) continue ;; esac
  [ -f "$WS/$path" ] || continue
  [ -f "$GD/$path" ] || continue

  GIT=(git -c safe.directory="$GD" -C "$GD")
  ws_sha=$("${GIT[@]}" hash-object -- "$WS/$path")
  gd_sha=$("${GIT[@]}" hash-object -- "$GD/$path")

  ws_in=0; gd_in=0
  "${GIT[@]}" cat-file -e "$ws_sha" 2>/dev/null && ws_in=1
  "${GIT[@]}" cat-file -e "$gd_sha" 2>/dev/null && gd_in=1

  verdict="REVIEW"
  if   [ "$ws_in" = 1 ] && [ "$gd_in" = 0 ]; then verdict="KEEP_GD_WS_IS_OLD"; counts_old=$((counts_old+1))
  elif [ "$ws_in" = 0 ] && [ "$gd_in" = 1 ]; then verdict="KEEP_WS_GD_IS_OLD"; counts_wsnew=$((counts_wsnew+1))
  elif [ "$ws_in" = 1 ] && [ "$gd_in" = 1 ]; then verdict="REVIEW_BOTH_IN_HISTORY"; counts_bothnew=$((counts_bothnew+1))
  else verdict="REVIEW_BOTH_UNKNOWN"; counts_gdnew=$((counts_gdnew+1)); fi
  printf '%s\t%s\t%s\t%s\t%s\n' "$path" "$verdict" "$ws_in" "$gd_in" "$status" >> "$OUT"
done < <(awk -F'\t' '$2=="NEWER_GD" || $2=="NEWER_WS" || $2=="CONFLICT"' "$BK/DIFFS.tsv")

echo "=== blob verdicts ==="
awk -F'\t' '{c[$2]++} END {for (k in c) printf "%-26s %4d\n", k, c[k]}' "$OUT" | sort
echo "total reviewed: $(wc -l < "$OUT")"
