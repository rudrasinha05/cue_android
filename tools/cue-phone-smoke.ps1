param([string]$Serial = "")

$ErrorActionPreference = "Stop"
Set-Location (Resolve-Path (Join-Path $PSScriptRoot ".."))

$adb = (Get-Command adb -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source -First 1)
if (-not $adb) {
    $candidate = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
    if (Test-Path $candidate) { $adb = $candidate }
}
if (-not $adb) { throw "adb missing. Install Android SDK Platform-Tools." }

$devices = @(& $adb devices | ForEach-Object {
    if ($_ -match '^(\S+)\s+device(?:\s|$)') { $Matches[1] }
})
if ($devices.Count -eq 0) {
    throw "No authorized device. Enable USB debugging, reconnect the phone, and approve the RSA prompt."
}

if (-not $Serial) {
    if ($devices.Count -eq 1) {
        $Serial = $devices[0]
    } else {
        # Exclude Android emulators; auto-select only when exactly one physical phone is present.
        $physical = @($devices | Where-Object { $_ -notmatch '^emulator-\d+$' })
        if ($physical.Count -eq 1) {
            $Serial = $physical[0]
            Write-Host "Selected physical phone: $Serial"
        } else {
            Write-Host "Authorized devices:"
            & $adb devices -l
            throw "Multiple phones or devices detected. Run again with -Serial DEVICE_ID."
        }
    }
}
if ($Serial -notin $devices) {
    throw "Device '$Serial' is unavailable. Run: adb devices -l"
}
$target = @("-s", $Serial)
Write-Host "Testing device: $Serial"
& $adb @target get-state | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Device unavailable." }

$android = (& $adb @target shell getprop ro.build.version.release).Trim()
$api = (& $adb @target shell getprop ro.build.version.sdk).Trim()
$commit = (& git rev-parse --short HEAD 2>$null)
Write-Host "Android $android (API $api); Cue commit $commit"
Write-Host "The script will not uninstall Cue or clear its stored data."

& .\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
if ($LASTEXITCODE -ne 0) { throw "Gradle build failed." }

$apk = "app\build\outputs\apk\debug\app-debug.apk"
$testApk = "app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"
foreach ($item in @($apk, $testApk)) {
    if (-not (Test-Path $item)) { throw "Missing APK: $item" }
}
& $adb @target install -r $apk
if ($LASTEXITCODE -ne 0) {
    throw "App update failed. DO NOT uninstall the existing app to bypass a signing mismatch."
}
& $adb @target install -r $testApk
if ($LASTEXITCODE -ne 0) { throw "Test APK installation failed." }

New-Item -ItemType Directory -Path "qa-results" -Force | Out-Null
$report = "qa-results\cue-phone-$(Get-Date -Format 'yyyyMMdd-HHmmss').txt"
$results = & $adb @target shell am instrument -w com.rudrasinha.cue.test/androidx.test.runner.AndroidJUnitRunner 2>&1
$exitCode = $LASTEXITCODE
$results | Tee-Object -FilePath $report
Add-Content -Path $report -Value "Android: $android (API $api); Commit: $commit; Device: $Serial"
if ($exitCode -ne 0 -or !(($results -join [Environment]::NewLine) -match "OK \(")) {
    throw "Instrumented tests did not pass. Review $report"
}
Write-Host "Automated phone smoke tests passed. Report: $report"
Write-Host "Manual bubble, sound and screen-capture checks remain."
