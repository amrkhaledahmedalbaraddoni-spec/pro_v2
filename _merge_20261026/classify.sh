#!/usr/bin/env bash
# Classify every path present in either tree: identical / only-ws / only-gd /
# newer-ws / newer-gd / true-conflict (both modified within tolerance).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BK="$ROOT/_merge_20261026"
WS="$BK/manifests/ws.tsv"
GD="$BK/manifests/gd.tsv"
OUT="$BK/DIFFS.tsv"

awk -F'\t' -v out="$OUT" '
  function emit(p, st, wsm, gdm) { printf "%s\t%s\t%s\t%s\n", p, st, wsm, gdm > out }
  BEGIN { FS="\t" }
  FNR==1 { file++ }
  file==1 { wp[$1]=$1; wh[$1]=$4; wm[$1]=$3; seen[$1]=1; next }
  {
    gp=$1
    if ($1 in seen) {
      if (wh[$1]==$4) emit($1,"SAME",wm[$1],$3)
      else {
        dt = $3 - wm[$1]
        if (dt > 5)       emit($1,"NEWER_GD",wm[$1],$3)
        else if (dt < -5) emit($1,"NEWER_WS",wm[$1],$3)
        else              emit($1,"CONFLICT",wm[$1],$3)
      }
      matched[$1]=1
    } else {
      emit($1,"ONLY_GD","-",$3)
      matched[$1]=0
    }
  }
  END {
    for (p in wp) if (!(p in matched)) emit(p,"ONLY_WS",wm[p],"-")
  }
' "$WS" "$GD"

echo "=== totals ==="
awk -F'\t' '{c[$2]++} END {for (k in c) printf "%-10s %6d\n", k, c[k]}' "$OUT" | sort
echo "=== by area (non-SAME) ==="
awk -F'\t' '$2!="SAME" { split($1,a,"/"); area=a[1]; if (a[1]=="red-app" && a[2]=="src") area="red-app/"a[3]; c[area"|"$2]++ }
             END { for (k in c) printf "%-40s %6d\n", k, c[k] }' "$OUT" | sort -k2 -rn | head -25
