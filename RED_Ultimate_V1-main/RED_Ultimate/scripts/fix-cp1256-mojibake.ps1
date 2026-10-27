# ─────────────────────────────────────────────────────────────────────────────
# fix-cp1256-mojibake.ps1 — عكس تلف الترميز العربي (UTF-8 ⇄ Windows-1256)
#
# المشكلة: ملف نُصِّب UTF-8 ثم قُرئ بترميز Windows-1256 وأُعيد حفظه UTF-8،
# فصار النص العربي "ط§ظ„ظ…ط¹ط¯ظ„" بدل "المعدل" (وهو ما ضرب
# backend-server/.../websocket/CallWebSocketHandler.kt في 58 سطراً).
#
# الحل: عكس التحويل بايت-ببايت: CP1256 ← UTF-8. لا يُطبَّق إلا حين يكون
# الناتج نصاً صحيحاً (بلا U+FFFD وبلا علامات التلف)، فالنص العربي السليم
# لا يُلمس (CP1256 يُنتج بايتات غير صالحة كـUTF-8 ⇒ يُرفض تلقائياً).
#
# الاستعمال:
#   powershell -File scripts/fix-cp1256-mojibake.ps1 -Path <ملف>            # فحص فقط
#   powershell -File scripts/fix-cp1256-mojibake.ps1 -Path <ملف> -Apply     # إصلاح
#   powershell -File scripts/fix-cp1256-mojibake.ps1 -Root <مجلد> -Apply    # شجرة كاملة
# ─────────────────────────────────────────────────────────────────────────────
[CmdletBinding()]
param(
    [string]$Path,
    [string]$Root,
    [switch]$Apply
)

$ErrorActionPreference = 'Stop'
try {
    [System.Text.Encoding]::RegisterProvider([System.Text.CodePagesEncodingProvider]::Instance)
} catch { }

$cp1256 = [System.Text.Encoding]::GetEncoding(1256)
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

# علامات التلف: وجودها يعني أن السطر يحتاج عكساً، وغيابها في الناتج يعني نجاحه.
$markers = @('ط§', 'ط¸', 'ظ†', 'ظ‡', 'â€', 'â•', 'ï¸', 'Ø§', 'Ù„')

function Test-MojibakeLine([string]$line) {
    foreach ($m in $markers) {
        if ($line.Contains($m)) { return $true }
    }
    return $false
}

function Repair-Line([string]$line) {
    $bytes = $cp1256.GetBytes($line)
    $decoded = [System.Text.Encoding]::UTF8.GetString($bytes)
    if ($decoded.Contains([char]0xFFFD)) { return $null }        # بايتات غير صالحة ⇒ ليس تلفاً معكوساً
    foreach ($m in $markers) {
        if ($decoded.Contains($m)) { return $null }               # التلف باقٍ ⇒ لا فائدة
    }
    return $decoded
}

function Repair-File([string]$file) {
    $lines = [System.IO.File]::ReadAllLines($file, [System.Text.Encoding]::UTF8)
    $changed = 0
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if (-not (Test-MojibakeLine $lines[$i])) { continue }
        $fixed = Repair-Line $lines[$i]
        if ($null -ne $fixed) {
            $lines[$i] = $fixed
            $changed++
        }
    }
    if ($changed -gt 0) {
        if ($Apply) {
            [System.IO.File]::WriteAllLines($file, $lines, $utf8NoBom)
            Write-Output "FIXED  $changed line(s)  $file"
        } else {
            Write-Output "NEEDS  $changed line(s)  $file"
        }
    }
}

$targets = @()
if ($Path) { $targets += (Resolve-Path $Path).Path }
if ($Root) {
    $targets += Get-ChildItem -Path $Root -Recurse -File -Include *.kt, *.kts, *.sql, *.ts, *.tsx, *.md, *.ps1, *.sh, *.yml, *.yaml, *.properties, *.json |
        Where-Object { $_.FullName -notmatch '\\(build|node_modules|dist|\.gradle|backups)\\' } |
        Select-Object -ExpandProperty FullName
}
if ($targets.Count -eq 0) { Write-Error 'Pass -Path or -Root'; exit 2 }

$total = 0
foreach ($t in $targets) { Repair-File $t; $total++ }
Write-Output "Scanned $total file(s). Mode: $(if ($Apply) { 'APPLY' } else { 'CHECK' })"
