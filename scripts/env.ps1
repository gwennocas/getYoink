$ErrorActionPreference = 'Stop'
$yoinkRoot = Split-Path $PSScriptRoot -Parent
$javaInstall = Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory -ErrorAction SilentlyContinue | Sort-Object Name | Select-Object -Last 1
if ($javaInstall) { $env:JAVA_HOME = $javaInstall.FullName }
$env:ANDROID_HOME = Join-Path $yoinkRoot '.tools\android-sdk'
$ndkInstall = Get-ChildItem (Join-Path $env:ANDROID_HOME 'ndk') -Directory -ErrorAction SilentlyContinue | Sort-Object Name | Select-Object -Last 1
if ($ndkInstall) { $env:NDK_HOME = $ndkInstall.FullName }
$env:Path = "C:\Program Files\nodejs;$env:USERPROFILE\.cargo\bin;$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:Path"
