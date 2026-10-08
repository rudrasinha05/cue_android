param([string]$Serial = "")
$ErrorActionPreference = "Stop"
Set-Location (Resolve-Path (Join-Path $PSScriptRoot ".."))
$adb = (Get-Command adb -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source -First 1)
if (-not $adb) {
    $candidate = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
    if (Test-Path $candidate) { $adb = $candidate }
}
if (-not $adb) { throw "adb missing; install Android SDK Platform-Tools." }
$devices = @(& $adb devices | ForEach-Object {
    if ($_ -match '^(\S+)\s+device(?:\s|$)') { $Matches[1] }
})
if ($devices.Count -eq 0) { throw "No authorized phone. Enable USB debugging and approve the prompt." }
if (-not $Serial) {
    if ($devices.Count -eq 1) {
        $Serial = $devices[0]
    } else {
        # Prefer exactly one connected physical phone when an Android Studio emulator
        # is also running. Never silently choose between two physical phones.
        $physical = @($devices | Where-Object { $_ -notmatch '^emulator-\d+
& $adb @target get-state | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Phone unavailable." }
$android = (& $adb @target shell getprop ro.build.version.release).Trim()
$api = (& $adb @target shell getprop ro.build.version.sdk).Trim()
$commit = (& git rev-parse --short HEAD 2>$null)
Write-Host "Android $android API $api; Cue commit $commit"
Write-Host "No uninstall or data clear will occur."
& .\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
if ($LASTEXITCODE -ne 0) { throw "Android test build failed." }
$apk = "app\build\outputs\apk\debug\app-debug.apk"
$testApk = "app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"
foreach ($item in @($apk,$testApk)) { if (-not (Test-Path $item)) { throw "Missing APK: $item" } }
& $adb @target install -r $apk
if ($LASTEXITCODE -ne 0) { throw "Update failed. Preserve existing app data; DO NOT uninstall to fix a signature mismatch." }
& $adb @target install -r $testApk
if ($LASTEXITCODE -ne 0) { throw "Test APK installation failed." }
New-Item -ItemType Directory -Path "qa-results" -Force | Out-Null
$report = "qa-results\cue-phone-$(Get-Date -Format yyyyMMdd-HHmmss).txt"
$results = & $adb @target shell am instrument -w com.rudrasinha.cue.test/androidx.test.runner.AndroidJUnitRunner 2>&1
$exit = $LASTEXITCODE
$results | Tee-Object -FilePath $report
Add-Content $report "Android: $android (API $api); Commit: $commit"
if ($exit -ne 0 -or !(($results -join [Environment]::NewLine) -match "OK \(")) {
    throw "Instrumented device tests not passed; see $report"
}
Write-Host "Instrumented tests passed; see $report. Manual phone QA remains."
 })
        if ($physical.Count -eq 1) {
            $Serial = $physical[0]
            Write-Host "Selected physical device $Serial (ignoring running emulator)."
        } else {
            throw "Multiple devices detected: $($devices -join ', '). Run: adb devices -l; then pass -Serial DEVICE_ID."
        }
    }
}
if ($Serial -notin $devices) {
    throw "Device '$Serial' is not authorized/connected. Run: adb devices -l."
}
$target = @("-s", $Serial)
Write-Host "Testing device: $Serial"
& $adb @target get-state | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Phone unavailable." }
$android = (& $adb @target shell getprop ro.build.version.release).Trim()
$api = (& $adb @target shell getprop ro.build.version.sdk).Trim()
$commit = (& git rev-parse --short HEAD 2>$null)
Write-Host "Android $android API $api; Cue commit $commit"
Write-Host "No uninstall or data clear will occur."
& .\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
if ($LASTEXITCODE -ne 0) { throw "Android test build failed." }
$apk = "app\build\outputs\apk\debug\app-debug.apk"
$testApk = "app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"
foreach ($item in @($apk,$testApk)) { if (-not (Test-Path $item)) { throw "Missing APK: $item" } }
& $adb @target install -r $apk
if ($LASTEXITCODE -ne 0) { throw "Update failed. Preserve existing app data; DO NOT uninstall to fix a signature mismatch." }
& $adb @target install -r $testApk
if ($LASTEXITCODE -ne 0) { throw "Test APK installation failed." }
New-Item -ItemType Directory -Path "qa-results" -Force | Out-Null
$report = "qa-results\cue-phone-$(Get-Date -Format yyyyMMdd-HHmmss).txt"
$results = & $adb @target shell am instrument -w com.rudrasinha.cue.test/androidx.test.runner.AndroidJUnitRunner 2>&1
$exit = $LASTEXITCODE
$results | Tee-Object -FilePath $report
Add-Content $report "Android: $android (API $api); Commit: $commit"
if ($exit -ne 0 -or !(($results -join [Environment]::NewLine) -match "OK \(")) {
    throw "Instrumented device tests not passed; see $report"
}
Write-Host "Instrumented tests passed; see $report. Manual phone QA remains."
