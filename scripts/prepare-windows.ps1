$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$root = Split-Path $PSScriptRoot -Parent
$destination = Join-Path $root 'src-tauri/resources/windows'
$cache = Join-Path $root '.tools/windows-engines'
New-Item -ItemType Directory -Force $destination, $cache | Out-Null
if ((Test-Path "$destination/yt-dlp.exe") -and (Test-Path "$destination/ffmpeg.exe") -and (Test-Path "$destination/ffprobe.exe") -and (Test-Path "$destination/deno.exe") -and (Test-Path "$destination/THIRD-PARTY.txt") -and (Test-Path "$destination/COPYING.FFmpeg.txt") -and (Test-Path "$destination/LICENSE.Deno.txt") -and (Test-Path "$destination/LICENSE.yt-dlp.txt")) {
    Write-Host 'Motores Windows preparados.'
    exit 0
}

function Get-Sha256($file) {
    $stream = [IO.File]::OpenRead($file)
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return [BitConverter]::ToString($sha.ComputeHash($stream)).Replace('-', '').ToLowerInvariant() }
    finally { $stream.Dispose(); $sha.Dispose() }
}
function Get-ReleaseAsset($repository, $assetName) {
    $release = Invoke-RestMethod "https://api.github.com/repos/$repository/releases/latest"
    $asset = $release.assets | Where-Object name -eq $assetName | Select-Object -First 1
    if (!$asset -or $asset.digest -notmatch '^sha256:[a-f0-9]{64}$') { throw "Asset sem SHA-256: $repository/$assetName" }
    $file = Join-Path $cache $assetName
    $expected = $asset.digest.Substring(7)
    if (!(Test-Path $file) -or (Get-Sha256 $file) -ne $expected) {
        Write-Host "Baixando $repository / $assetName..."
        Invoke-WebRequest $asset.browser_download_url -OutFile $file
    }
    if ((Get-Sha256 $file) -ne $expected) { throw "SHA-256 invalido: $assetName" }
    return @{ File = $file; Release = $release.html_url; Hash = $expected }
}

$yt = Get-ReleaseAsset 'yt-dlp/yt-dlp' 'yt-dlp.exe'
$ff = Get-ReleaseAsset 'yt-dlp/FFmpeg-Builds' 'ffmpeg-master-latest-win64-gpl.zip'
$deno = Get-ReleaseAsset 'denoland/deno' 'deno-x86_64-pc-windows-msvc.zip'
Copy-Item -LiteralPath $yt.File -Destination "$destination/yt-dlp.exe" -Force
Add-Type -AssemblyName System.IO.Compression.FileSystem
foreach ($archive in @($ff, $deno)) {
    $zip = [IO.Compression.ZipFile]::OpenRead($archive.File)
    try {
        foreach ($name in @('ffmpeg.exe', 'ffprobe.exe', 'deno.exe')) {
            $entry = $zip.Entries | Where-Object Name -eq $name | Select-Object -First 1
            if ($entry) { [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, (Join-Path $destination $name), $true) }
        }
    } finally { $zip.Dispose() }
}
@"
getYoink para Windows — componentes de terceiros

yt-dlp: Unlicense; o executável inclui componentes sob outras licenças.
Release e código-fonte: $($yt.Release)
SHA-256: $($yt.Hash)
Licenças: https://github.com/yt-dlp/yt-dlp/blob/master/THIRD_PARTY_LICENSES.txt

FFmpeg / FFprobe: GPL v3 ou posterior, build estática do projeto yt-dlp.
Release, configuração de build e fontes: $($ff.Release)
Receitas e fontes correspondentes: https://github.com/yt-dlp/FFmpeg-Builds
SHA-256 do arquivo ZIP: $($ff.Hash)

Deno: MIT (e licenças das dependências).
Release e código-fonte: $($deno.Release)
SHA-256 do arquivo ZIP: $($deno.Hash)
Licenças: https://github.com/denoland/deno/blob/main/third_party/README.md
"@ | Set-Content "$destination/THIRD-PARTY.txt" -Encoding UTF8
Invoke-WebRequest 'https://raw.githubusercontent.com/FFmpeg/FFmpeg/master/COPYING.GPLv3' -OutFile "$destination/COPYING.FFmpeg.txt"
Invoke-WebRequest 'https://raw.githubusercontent.com/denoland/deno/main/LICENSE.md' -OutFile "$destination/LICENSE.Deno.txt"
Invoke-WebRequest 'https://raw.githubusercontent.com/yt-dlp/yt-dlp/master/THIRD_PARTY_LICENSES.txt' -OutFile "$destination/LICENSE.yt-dlp.txt"
Write-Host 'Motores Windows preparados e hashes verificados.'
