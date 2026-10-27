#!/usr/bin/env bash
# Idempotent completion of the two-tree merge.
#   Golden  = authoritative content and Git history (HEAD 21eefe3).
#   Workspace = canonical working tree from now on; extras are quarantined, never deleted.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BK="$ROOT/_merge_20261026"
WS="$ROOT/RED_Ultimate_V1-main/RED_Ultimate"
GD="/d/RED_Golden/RED_Ultimate_V1-main/RED_Ultimate"
Q="$BK/quarantine"
LOG="$BK/resume.log"
GIT=(git -c safe.directory="$WS" -C "$WS")

mkdir -p "$Q"
: > "$LOG"
say() { echo "$*" | tee -a "$LOG"; }

say "=== 0) repository check ==="
[ -d "$WS/.git" ] || { say "FATAL: workspace has no .git"; exit 1; }
"${GIT[@]}" log --oneline -1 2>&1 | tee -a "$LOG"

say "=== 1) materialise every Golden file (Golden wins) ==="
copied=0; same=0
while IFS=$'\t' read -r path _size _mtime _sha; do
  src="$GD/$path"; dst="$WS/$path"
  if [ -f "$dst" ] && cmp -s "$src" "$dst"; then same=$((same+1)); continue; fi
  mkdir -p "$(dirname "$dst")"
  cp -p "$src" "$dst" || say "COPY FAILED: $path"
  copied=$((copied+1))
done < "$BK/manifests/gd.tsv"
say "copied/updated=$copied identical=$same"

say "=== 2) quarantine workspace-only trees and superseded files ==="
qmove() { # $1 = relative path
  local p="$1"
  [ -e "$WS/$p" ] || return 0
  mkdir -p "$Q/$(dirname "$p")"
  mv "$WS/$p" "$Q/$p" && echo -e "$p\tQUARANTINED" >> "$BK/QUARANTINE.tsv"
}
: > "$BK/QUARANTINE.tsv"

# 2a. Legacy Signal module tree: excluded from settings.gradle.kts by documented decision
#     (docs/OLD_MODULES_DECISION_2026-09-16.md) and never compiled by :app.
for d in lib core feature; do
  [ -d "$WS/$d" ] && qmove "$d"
done

# 2b. Files Golden deleted on purpose (content exists in its history) — never resurrect.
awk -F'\t' '$2=="DELETED_IN_GOLDEN"{print $1}' "$BK/ONLY_WS_VERDICT.tsv" | while IFS= read -r p; do qmove "$p"; done

# 2c. Legacy admin router, superseded by Golden's TanStack file-routes + new dashboard.
for d in admin_dashboard/src/routes admin_dashboard/src/pages/tabs admin_dashboard/dev-server; do
  [ -d "$WS/$d" ] && qmove "$d"
done
for f in admin_dashboard/src/App.tsx admin_dashboard/build_runner.cjs admin_dashboard/patch_unplugin.cjs \
         admin_dashboard/final-admin-check.log admin_dashboard/src/components/UltimateEmpty.tsx; do
  qmove "$f"
done

# 2d. Superseded app sources that Golden replaced with newer call/conference screens.
for f in red-app/src/main/java/com/red/sovereign/ui/screens/CallsScreen.kt \
         red-app/src/main/java/com/red/sovereign/calls/ModernCallScreens.kt \
         red-app/src/main/java/com/red/sovereign/calls/UnifiedCallOrchestrator.kt \
         red-app/src/main/java/com/red/sovereign/ui/RedDashboard.kt; do
  qmove "$f"
done

# 2e. Build/compile scratch output and scraped pages — not source.
for f in build_errors*.txt build_log.txt build_out.txt build_output.txt compile.log compile2.log \
         compile_out*.txt compile_test*.txt errs.txt recovered.txt scratch_duplicates.txt \
         test_results.txt test_log.txt rebuild_log.txt test_sip_ws.py \
         sipcfg.html sipcfg2.html sipcfg3.html sipcfg4.html sipcfg_post.txt sipcfg_post2.txt \
         sipcfg_post3.txt sipcfg_save.html sipcfg_save2.html sipcfg_save3.html sipconfig.html \
         sip_cfg.html sys.html menu.html iptunk.html frame.html login_resp.html portlist.html \
         portinfo.json tel2ip.json; do
  qmove "$f"
done
qmove "backend-server/.kotlin"
qmove "backend-server/jar_build.log"
qmove "backend-server/compile_test2.txt"
qmove "backend-server/test_out.txt"
qmove "backend-server/compile_out.txt"
qmove "backend-server/pro_new_compile.log"
qmove ".kotlin"

say "quarantined entries: $(wc -l < "$BK/QUARANTINE.tsv")"

say "=== 3) verify nothing tracked was lost ==="
"${GIT[@]}" status --porcelain > "$BK/git-status.txt" 2>&1
say "deleted(staged/unstaged): $("${GIT[@]}" status --porcelain | grep -cE '^( D|D )')"
say "modified:                 $("${GIT[@]}" status --porcelain | grep -cE '^( M|M )')"
say "untracked:                $("${GIT[@]}" status --porcelain | grep -c '^??')"
say "--- untracked sample ---"
"${GIT[@]}" status --porcelain | grep '^??' | head -30 | tee -a "$LOG"

say "=== 4) compile the app (proof the merged tree is coherent) ==="
cd "$WS" || exit 1
./gradlew --no-daemon --console=plain :app:compileDebugKotlin >> "$LOG" 2>&1
say "app compile exit=$?"
./gradlew --no-daemon --console=plain :backend-server:compileKotlin >> "$LOG" 2>&1
say "backend compile exit=$?"
say "DONE"
