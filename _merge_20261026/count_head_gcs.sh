#!/usr/bin/env bash
set -euo pipefail
tmp=$(mktemp)
trap 'rm -f "$tmp"' EXIT
git -C "D:/RED_Golden/RED_Ultimate_V1-main/RED_Ultimate" show 'HEAD:red-app/src/main/java/com/red/sovereign/calls/GroupCallService.kt' > "$tmp"
wc -l "$tmp"
