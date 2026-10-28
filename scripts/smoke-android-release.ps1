param(
    [Parameter(Mandatory = $true)][string]$Device,
    [string]$ApkPath,
    [ValidateRange(1, 5)][int]$Launches = 3
)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\env.ps1"
if ($ApkPath) {
    adb -s $Device install -r $ApkPath
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao instalar o APK. O teste nunca desinstala ou limpa os dados do app.' }
}
adb -s $Device shell input keyevent KEYCODE_WAKEUP
for ($yoinkAttempt = 1; $yoinkAttempt -le $Launches; $yoinkAttempt++) {
    # Run only while the queue is idle: a force-stop interrupts active downloads.
    adb -s $Device shell am force-stop app.getyoink.mobile
    adb -s $Device shell am start -W -n app.getyoink.mobile/.MainActivity
    if ($LASTEXITCODE -ne 0) { throw "Falha ao abrir o app na tentativa $yoinkAttempt." }
    Start-Sleep -Seconds 12
    $yoinkProcessId = (adb -s $Device shell pidof app.getyoink.mobile | Out-String).Trim()
    if (!$yoinkProcessId) {
        adb -s $Device logcat -d -b crash -t 100
        throw "O app encerrou na tentativa $yoinkAttempt."
    }
    $yoinkCrashes = adb -s $Device logcat -d -b crash "--pid=$yoinkProcessId"
    if ($yoinkCrashes -match 'FATAL EXCEPTION|Fatal signal|FORTIFY:') {
        $yoinkCrashes | Write-Output
        throw "Crash detectado na tentativa $yoinkAttempt."
    }
    Write-Output "Abertura $yoinkAttempt/${Launches}: processo permanece ativo, sem crash."
}
