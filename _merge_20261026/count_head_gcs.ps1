git -C 'D:\RED_Golden\RED_Ultimate_V1-main\RED_Ultimate' show 'HEAD:red-app/src/main/java/com/red/sovereign/calls/GroupCallService.kt' | measure-object -Line | select -ExpandProperty Lines
Write-Output HEAD_GCS_LINES_DONE=1
