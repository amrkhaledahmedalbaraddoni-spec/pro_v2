$lines = @();
git -C "D:\RED_Golden\RED_Ultimate_V1-main\RED_Ultimate" diff "HEAD~1..HEAD" -- "red-app/src/main/java/com/red/sovereign/calls/LiveStreamService.kt" | Out-String -Stream | ForEach-Object { $lines += $_ };
$lines | Where-Object {
    $_.Contains("VideoTrack", [StringComparison]::OrdinalIgnoreCase) -or
    $_.Contains("VideoCapturer", [StringComparison]::OrdinalIgnoreCase) -or
    $_.Contains("selectCamera", [StringComparison]::OrdinalIgnoreCase) -or
    $_.Contains("Camera2", [StringComparison]::OrdinalIgnoreCase) -or
    $_.Contains("createLocalVideoTrack", [StringComparison]::OrdinalIgnoreCase)
} | Select-Object -First 40
Write-Output LIVE_VID_PS2_DONE=1
