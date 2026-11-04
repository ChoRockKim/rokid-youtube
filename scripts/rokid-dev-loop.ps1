param(
    [int]$Iterations = 1,
    [string]$Serial = "",
    [switch]$Install,
    [switch]$SmokeKeys
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

function Invoke-Step([string]$Name, [scriptblock]$Block) {
    Write-Host ""
    Write-Host "== $Name =="
    & $Block
}

for ($i = 1; $i -le $Iterations; $i++) {
    Write-Host ""
    Write-Host "RokidPipe dev loop $i/$Iterations"

    Invoke-Step "Gradle build and JVM tests" {
        .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --stacktrace -DskipFormatKtlint
    }

    $apk = Get-ChildItem -Path "app\build\outputs\apk\debug" -Filter "*.apk" |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    if ($Install) {
        if (-not $apk) {
            throw "No debug APK found after build."
        }
        $adbTarget = if ($Serial) { @("-s", $Serial) } else { @() }
        Invoke-Step "Install APK" {
            adb @adbTarget install -r $apk.FullName
        }
    }

    if ($SmokeKeys) {
        $adbTarget = if ($Serial) { @("-s", $Serial) } else { @() }
        Invoke-Step "Rokid one-axis key smoke" {
            adb @adbTarget shell input keyevent 22 20
            adb @adbTarget shell input keyevent 21 19
            adb @adbTarget shell input keyevent 23
            adb @adbTarget shell input keyevent 66
            adb @adbTarget shell input keyevent 4
        }
    }
}
