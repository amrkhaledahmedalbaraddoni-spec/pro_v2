<#
.SYNOPSIS
  Auto-refresh LAN IPs in .env (P0 fix 2026-09-27 — stale 192.168.1.67 bug).
.DESCRIPTION
  Wraps detect-lan-ips.ps1 and rewrites .env keys so phones can reach
  Nginx/TURN/SFU/ntfy after every DHCP change:
    CLIENT_LAN_IP, TURN_PUBLIC_HOST, TURN_EXTERNAL_IP,
    MEDIASOUP_ANNOUNCED_IP, TLS_SAN_IP, NTFY_BASE_URL,
    ASTERISK_WSS_URL, ALLOWED_ORIGINS (adds current IP, keeps legacy).
  Usage: powershell -ExecutionPolicy Bypass -File .\scripts\detect-client-ip.ps1 [-Apply]
  Without -Apply: prints only (dry-run). With -Apply: rewrites .env in place.
#>
param([switch]$Apply, [string]$EnvPath = "$PSScriptRoot\..\.env")

$ErrorActionPreference = 'Stop'
$info = & "$PSScriptRoot\detect-lan-ips.ps1" -Json | ConvertFrom-Json
$clientIp = $info.clientLanIp
if (-not $clientIp) {
  # Fallback: first non-loopback IPv4 (covers hotspot/USB-tether names)
  $clientIp = (Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' -and $_.PrefixOrigin -ne 'Link' } |
    Select-Object -First 1 -ExpandProperty IPAddress)
}
if (-not $clientIp) { Write-Error 'No LAN IPv4 found'; exit 1 }
Write-Host "Detected CLIENT_LAN_IP = $clientIp (wifi=$($info.wifiIp) eth=$($info.ethernetIp))"
if (-not $Apply) { Write-Host 'Dry-run: re-run with -Apply to rewrite .env'; exit 0 }
if (-not (Test-Path -LiteralPath $EnvPath)) { Write-Error "Missing $EnvPath"; exit 1 }

$text = Get-Content -LiteralPath $EnvPath -Raw -Encoding UTF8
function Set-EnvKey([string]$t, [string]$key, [string]$val) {
  $pat = "(?m)^\s*#?\s*$key\s*=.*$"
  $rep = "$key=$val"
  if ($t -match $pat) { return [regex]::Replace($t, $pat, $rep) }
  return $t.TrimEnd() + "`r`n$rep`r`n"
}
$text = Set-EnvKey $text 'CLIENT_LAN_IP' $clientIp
$text = Set-EnvKey $text 'TURN_PUBLIC_HOST' $clientIp
$text = Set-EnvKey $text 'TURN_EXTERNAL_IP' $clientIp
$text = Set-EnvKey $text 'MEDIASOUP_ANNOUNCED_IP' $clientIp
$text = Set-EnvKey $text 'TLS_SAN_IP' $clientIp
$text = Set-EnvKey $text 'NTFY_BASE_URL' "http://${clientIp}:2586"
$text = Set-EnvKey $text 'ASTERISK_WSS_URL' "ws://${clientIp}:8088/ws/sip"

# ALLOWED_ORIGINS: ensure current IP present, keep legacy (no deletion) with cap 20 (newest kept)
if ($text -match '(?m)^\s*ALLOWED_ORIGINS\s*=\s*(.*)$') {
  $cur = $Matches[1].Trim()
  $need = "http://${clientIp}:8088"
  if ($cur -notlike "*$need*") {
    $new = if ($cur) { "$cur,$need" } else { $need }
    $parts = $new -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ } | Select-Object -Unique
    if ($parts.Count -gt 20) { $parts = $parts[-20..-1] }
    $new = $parts -join ','
    $text = Set-EnvKey $text 'ALLOWED_ORIGINS' $new
    Write-Host "ALLOWED_ORIGINS += $need"
  } else { Write-Host 'ALLOWED_ORIGINS already contains current IP' }
}
[System.IO.File]::WriteAllText($EnvPath, $text, [System.Text.Encoding]::UTF8)
Write-Host "Updated $EnvPath — restart stack: docker compose up -d --force-recreate nginx backend media-sfu coturn"
