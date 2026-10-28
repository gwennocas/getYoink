param([ValidateSet('init','build','dev','devices')][string]$Action = 'build')
. "$PSScriptRoot\env.ps1"
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    switch ($Action) {
        'init' { npm run tauri -- android init }
        'build' { npm run android:build }
        'dev' { npm run android:dev }
        'devices' { adb devices -l }
    }
    if ($LASTEXITCODE -ne 0) { throw "O comando terminou com erro $LASTEXITCODE" }
} finally { Pop-Location }
