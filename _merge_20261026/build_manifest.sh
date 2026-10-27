#!/usr/bin/env bash
# Build a byte-level manifest of source files in both project copies.
# Read-only against the trees: it only writes into _merge_20261026/.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BK="$ROOT/_merge_20261026"
WS="$ROOT/RED_Ultimate_V1-main/RED_Ultimate"
GD="/d/RED_Golden/RED_Ultimate_V1-main/RED_Ultimate"

mkdir -p "$BK/manifests" "$BK/backup"

# Generated, cached, vendored, archival and large binary artifacts are not part
# of the source merge. They are rebuilt/regenerated, never merged.
PRUNE=(
  -not -path '*/build/*'
  -not -path '*/.gradle/*'
  -not -path '*/node_modules/*'
  -not -path '*/.git/*'
  -not -path '*/apk-output/*'
  -not -path '*/dist/*'
  -not -path '*/.next/*'
  -not -path '*/backups/*'
  -not -path '*/archive/*'
  -not -path '*/prebuilt-backend/*'
  -not -name '*.apk' -not -name '*.aab' -not -name '*.hprof'
  -not -name '*.class' -not -name '*.jar'
)

manifest() {
  local tree="$1" out="$2" tmp="$2.tmp"
  mkdir -p "$tmp"

  ( cd "$tree" && find . -type f "${PRUNE[@]}" -printf '%P\t%s\t%T@\n' 2>/dev/null \
      | LC_ALL=C sort > "$tmp/paths.tsv" )

  ( cd "$tree" && find . -type f "${PRUNE[@]}" -print0 2>/dev/null \
      | LC_ALL=C sort -z | xargs -0 -r sha256sum 2>/dev/null > "$tmp/hashes.raw" )

  # sha256sum prints "<hash> *<path>" in binary mode or "<hash>  <path>" in text mode.
  awk '{ h=$1; $1=""; sub(/^ \*/,""); sub(/^  /,""); sub(/^ /,""); sub(/^\.\//,""); print h "\t" $0 }' \
      "$tmp/hashes.raw" | LC_ALL=C sort -t "$(printf '\t')" -k2 > "$tmp/hashes.tsv"

  awk -F'\t' 'NR==FNR { h[$2]=$1; next }
              { printf "%s\t%s\t%s\t%s\n", $1, $2, $3, (($1 in h) ? h[$1] : "MISSING") }' \
      "$tmp/hashes.tsv" "$tmp/paths.tsv" > "$out"

  echo "manifest $out: $(wc -l < "$out") files, missing-hash=$(awk -F'\t' '$4=="MISSING"' "$out" | wc -l)"
  rm -rf "$tmp"
}

if [ ! -f "$BK/backup/workspace-src.tar.gz" ] || [ ! -f "$BK/backup/golden-src.tar.gz" ]; then
  echo "=== backup (source-complete, generated dirs excluded) ==="
  TAR_EX=( --exclude='*/build/*' --exclude='*/.gradle/*' --exclude='*/node_modules/*'
           --exclude='*/.git/*' --exclude='*/apk-output/*' --exclude='*/dist/*'
           --exclude='*/.next/*' --exclude='*/backups/*' --exclude='*/archive/*'
           --exclude='*/prebuilt-backend/*' --exclude='*.apk' --exclude='*.aab' --exclude='*.hprof' )
  ( cd "$WS" && tar -czf "$BK/backup/workspace-src.tar.gz" "${TAR_EX[@]}" . )
  ( cd "$GD" && tar -czf "$BK/backup/golden-src.tar.gz"    "${TAR_EX[@]}" . )
else
  echo "=== backup already present, skipping ==="
fi
ls -la "$BK/backup"

echo "=== manifests ==="
manifest "$WS" "$BK/manifests/ws.tsv"
manifest "$GD" "$BK/manifests/gd.tsv"
