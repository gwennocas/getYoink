. "$PSScriptRoot\env.ps1"
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    npm.cmd run android:apk > .tools/android-build.log 2>&1
    $yoinkBuildExit = $LASTEXITCODE
    Set-Content .tools/android-build.exit $yoinkBuildExit
    exit $yoinkBuildExit
} finally { Pop-Location }
