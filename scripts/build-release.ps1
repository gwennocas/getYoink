$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\env.ps1"
$yoinkSigningProperties = if ($env:YOINK_SIGNING_PROPERTIES) { $env:YOINK_SIGNING_PROPERTIES } else {
    Join-Path $env:USERPROFILE '.android\getYoink-release\keystore.properties'
}
if (!(Test-Path -LiteralPath $yoinkSigningProperties -PathType Leaf)) {
    throw 'Configure o keystore de release antes de compilar. Consulte o README.'
}

Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    New-Item -ItemType Directory -Force -Path '.tools' | Out-Null
    $yoinkReleaseLog = Join-Path (Get-Location) '.tools\android-release-build.log'
    $yoinkReleaseErrorLog = Join-Path (Get-Location) '.tools\android-release-build.stderr.log'
    # Windows PowerShell 5 treats informational stderr as an error when redirected
    # through its pipeline. Redirect native streams directly and check the exit code.
    $yoinkBuildProcess = Start-Process -FilePath $env:ComSpec `
        -ArgumentList '/d', '/c', 'npm.cmd run android:build' -WindowStyle Hidden `
        -RedirectStandardOutput $yoinkReleaseLog -RedirectStandardError $yoinkReleaseErrorLog `
        -PassThru
    # Wait for the command itself, not a Windows job containing detached descendants.
    $yoinkBuildProcess.WaitForExit()
    $yoinkBuildProcess.Refresh()
    if ($yoinkBuildProcess.ExitCode -ne 0) {
        $yoinkBuildOutput = (Get-Content -LiteralPath $yoinkReleaseLog -Raw) +
            (Get-Content -LiteralPath $yoinkReleaseErrorLog -Raw)
        if ($yoinkBuildOutput -notmatch 'Creation symbolic link is not allowed for this system') {
            Get-Content -LiteralPath $yoinkReleaseLog -Tail 60
            Get-Content -LiteralPath $yoinkReleaseErrorLog -Tail 60
            throw 'Falha ao compilar o APK de release.'
        }
        # Tauri already compiled Rust and embedded the frontend before this Windows error.
        # Copy the freshly built library, then run only the remaining Gradle packaging tasks.
        Copy-Item -LiteralPath 'src-tauri\target\aarch64-linux-android\release\libgetyoink_lib.so' `
            -Destination 'src-tauri\gen\android\app\src\main\jniLibs\arm64-v8a\libgetyoink_lib.so'
        & '.\src-tauri\gen\android\gradlew.bat' -p 'src-tauri/gen/android' `
            ':app:assembleArm64Release' -x ':app:rustBuildArm64Release' --no-configuration-cache --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'Falha ao assinar ou empacotar o APK de release.' }
    }
    $yoinkApk = 'src-tauri\gen\android\app\build\outputs\apk\arm64\release\app-arm64-release.apk'
    $yoinkApkSigner = Get-ChildItem (Join-Path $env:ANDROID_HOME 'build-tools') -Filter apksigner.bat -Recurse |
        Sort-Object FullName | Select-Object -Last 1
    & $yoinkApkSigner.FullName verify --verbose $yoinkApk
    if ($LASTEXITCODE -ne 0) { throw 'A assinatura do APK não passou na verificação.' }
    $yoinkVersion = (Get-Content 'src-tauri\tauri.conf.json' -Raw | ConvertFrom-Json).version
    New-Item -ItemType Directory -Force -Path artifacts | Out-Null
    $yoinkOutput = "artifacts\getYoink-$yoinkVersion-arm64.apk"
    Copy-Item -LiteralPath $yoinkApk -Destination $yoinkOutput
    Write-Host "APK de release assinado: $yoinkOutput"
} finally { Pop-Location }
