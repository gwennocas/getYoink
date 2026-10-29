$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Push-Location $root
try {
    & powershell -NoProfile -ExecutionPolicy Bypass -File "$PSScriptRoot/prepare-windows.ps1"
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao preparar motores Windows.' }
    & npm.cmd run tauri -- build --bundles nsis
    if ($LASTEXITCODE -ne 0) { throw 'Falha no build Windows.' }
    $version = (Get-Content package.json -Raw | ConvertFrom-Json).version
    $installer = Get-ChildItem 'src-tauri/target/release/bundle/nsis/*-setup.exe' | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (!$installer) { throw 'Instalador Windows não encontrado.' }
    New-Item -ItemType Directory -Force artifacts | Out-Null
    $output = "artifacts/getYoink-$version-windows-x64-setup.exe"
    Copy-Item -LiteralPath $installer.FullName -Destination $output -Force
    $sha = [Security.Cryptography.SHA256]::Create()
    $stream = [IO.File]::OpenRead((Join-Path $root $output))
    try { Write-Host ('SHA-256: ' + [BitConverter]::ToString($sha.ComputeHash($stream)).Replace('-', '').ToLowerInvariant()) }
    finally { $sha.Dispose(); $stream.Dispose() }
    Write-Host "Instalador: $output"
} finally { Pop-Location }
