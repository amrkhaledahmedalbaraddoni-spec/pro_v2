# ══════════════════════════════════════════════════════════════════
# RED APK Builder - Windows PowerShell Script (2026-09-26 fixed)
# يبني :app (مجلده red-app/) محلياً عبر Gradle ثم ينسخ النواتج إلى apk-output/
# مع SHA256. وحدة :app مربوطة بـ red-app/ في settings.gradle.kts.
# ══════════════════════════════════════════════════════════════════

param (
    [Parameter(Mandatory = $false)]
    [ValidatePattern('^https?://[^/]+(?::\d+)?$')]
    [string]$ServerUrl = "http://10.0.2.2:8088",

    [Parameter(Mandatory = $false)]
    [ValidateSet("debug", "release", "both")]
    [string]$Flavor = "both"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $root

Write-Host "Starting RED APK Build..." -ForegroundColor Cyan
Write-Host "Target Server: $ServerUrl  Flavor: $Flavor" -ForegroundColor Gray

$gradleArgs = @()
if ($Flavor -in @("debug", "both")) { $gradleArgs += ":app:assembleDebug" }
if ($Flavor -in @("release", "both")) { $gradleArgs += ":app:assembleRelease" }
$gradleArgs += "-PRED_SERVER_URL=$ServerUrl"
# P0 2026-09-27: CANDIDATES كانت تُبنى بلا قيمة (افتراضي محاكي فقط) فتفشل
# المسارات السريعة على الشبكات الحقيقية. نحقن كل واجهات LAN الحية (واي فاي +
# إيثرنت) + عنوان البناء المطلوب، فيجد الهاتف السيرفر فوراً دون مسح /24.
$candidateIps = @(Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' -and $_.PrefixOrigin -ne 'Link' } |
    Select-Object -ExpandProperty IPAddress)
try { $serverHost = ([System.Uri]$ServerUrl).Host } catch { $serverHost = "" }
$candidateUrls = @()
if ($serverHost) { $candidateUrls += "http://${serverHost}:8088" }
foreach ($ip in $candidateIps) { $candidateUrls += "http://${ip}:8088" }
$candidateUrls += @("http://10.0.2.2:8088", "http://127.0.0.1:8088")
$candidates = ($candidateUrls | Select-Object -Unique) -join ","
$gradleArgs += "-PRED_SERVER_CANDIDATES=$candidates"
Write-Host "Candidates: $candidates" -ForegroundColor Gray

Write-Host "Compiling: $($gradleArgs -join ' ')" -ForegroundColor Yellow
& ./gradlew @gradleArgs
if ($LASTEXITCODE -ne 0) {
    Write-Host "Build Failed. Check logs above." -ForegroundColor Red
    exit 1
}

# 4. Publish the APKs to apk-output/
New-Item -ItemType Directory -Path "./apk-output" -Force | Out-Null
$copied = @()
foreach ($pair in @(
    @("./red-app/build/outputs/apk/debug/app-debug.apk", "./apk-output/red-debug.apk"),
    @("./red-app/build/outputs/apk/release/app-release.apk", "./apk-output/red-release.apk")
)) {
    if (Test-Path -LiteralPath $pair[0]) {
        Copy-Item -LiteralPath $pair[0] -Destination $pair[1] -Force
        $copied += $pair[1]
        Write-Host "Copied $($pair[0]) -> $($pair[1])" -ForegroundColor Green
    }
}

# 5. SHA256 sums
if ($copied.Count -gt 0) {
    $sums = foreach ($f in $copied) {
        $h = (Get-FileHash -LiteralPath $f -Algorithm SHA256).Hash
        "$h  $(Split-Path -Leaf $f)"
    }
    $sums | Set-Content -LiteralPath "./apk-output/SHA256SUMS.txt" -Encoding ASCII
    Write-Host "Done! APKs ready in: $root\apk-output" -ForegroundColor Cyan
    $sums | ForEach-Object { Write-Host $_ -ForegroundColor Gray }
} else {
    Write-Host "No APK outputs found - build may have been UP-TO-DATE with missing outputs." -ForegroundColor Yellow
}
