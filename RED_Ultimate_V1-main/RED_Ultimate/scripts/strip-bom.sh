#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# strip-bom.sh — فاحص/مصلح BOM البادئ في الملفات النصية
#
# لماذا: BOM ‏(EF BB BF) في بداية ملف TOML يجعل Gradle 9.7 يفشل فوراً
# (`TOML syntax error … Unexpected '\ufeff'`)، وكان `gradle/libs.versions.toml`
# يحمله فتوقف بناء الأندرويد كاملاً قبل أي ترجمة.
#
# الاستعمال:
#   scripts/strip-bom.sh --check    # يسرد الملفات التي تحمل BOM ويفشل إن وُجدت
#   scripts/strip-bom.sh --apply    # يزيل BOM من بداية كل ملف نصي (idempotent)
#
# لا يحذف محتوى ولا يعيد ترميزاً: يقصّ أول 3 بايتات فقط إن كانت BOM.
# ─────────────────────────────────────────────────────────────────────────────
set -u
cd "$(dirname "$0")/.." || exit 1

MODE="${1:---check}"
if [ "$MODE" != "--apply" ] && [ "$MODE" != "--check" ]; then
  echo "usage: $0 [--check|--apply]" >&2
  exit 2
fi

is_excluded() {
  case "$1" in
    ./backups/*|./prebuilt-backend/*|./.git/*) return 0 ;;
    */build/*|*/node_modules/*|*/dist/*|*/.gradle/*|*/__pycache__/*) return 0 ;;
    # ملفات PowerShell: Windows PowerShell 5.1 يقرأ .ps1 بلا BOM كأنه ANSI،
    # فيُفسد أي نص عربي داخل السكربت (خطأ تحليلي مؤكد في scripts/fix-cp1256-mojibake.ps1).
    # لذلك BOM في .ps1 مطلوب، ويُستثنى من الإزالة عمداً.
    *.ps1) return 0 ;;
  esac
  return 1
}

found=0
fixed=0

# -I: تجاهل الملفات الثنائية. القراءة من الأنبوب لأن مسارات Windows تحتوي مسافات.
while IFS= read -r f; do
  [ -n "$f" ] || continue
  if is_excluded "$f"; then continue; fi
  found=$((found + 1))
  if [ "$MODE" = "--apply" ]; then
    if sed -i '1s/^\xEF\xBB\xBF//' "$f"; then
      fixed=$((fixed + 1))
      printf '✔︎ أُزيل BOM: %s\n' "$f"
    else
      printf '✖︎ فشل الإصلاح: %s\n' "$f" >&2
    fi
  else
    printf 'BOM: %s\n' "$f"
  fi
done < <(grep -rlI $'^\xEF\xBB\xBF' . 2>/dev/null)

if [ "$MODE" = "--apply" ]; then
  printf '\n[strip-bom] ملفات بها BOM: %s — أُصلح: %s\n' "$found" "$fixed"
  exit 0
fi

if [ "$found" -gt 0 ]; then
  printf '\n[strip-bom] ❌ %s ملفاً يحمل BOM بادئاً — شغّل: scripts/strip-bom.sh --apply\n' "$found"
  exit 1
fi
printf '[strip-bom] ✅ لا BOM بادئ في أي ملف نصي\n'
