param(
    [switch]$SkipLocalUnitTests
)
$ErrorActionPreference = "Stop"
Set-Location (Resolve-Path (Join-Path $PSScriptRoot ".."))

$required = @(
    "CUE_UPLOAD_KEYSTORE",
    "CUE_UPLOAD_STORE_PASSWORD",
    "CUE_UPLOAD_KEY_ALIAS",
    "CUE_UPLOAD_KEY_PASSWORD"
)
$missing = @($required | Where-Object {
    [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($_))
})
if ($missing.Count -gt 0) {
    throw "Signing credentials missing: $($missing -join ', '). Configure them privately in this PowerShell session. Never paste signing passwords into GitHub or chat."
}
if (-not (Test-Path $env:CUE_UPLOAD_KEYSTORE -PathType Leaf)) {
    throw "The CUE_UPLOAD_KEYSTORE file does not exist on this computer."
}
if (-not (Select-String -Path "app\build.gradle.kts" -Pattern 'targetSdk\s*=\s*36' -Quiet)) {
    throw "Google Play API 36 target is not configured. Pull latest develop before signing."
}
$jarsigner = (Get-Command jarsigner -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty Source)
if (-not $jarsigner -and $env:JAVA_HOME) {
    $candidate = Join-Path $env:JAVA_HOME "bin\jarsigner.exe"
    if (Test-Path $candidate) { $jarsigner = $candidate }
}
if (-not $jarsigner) { throw "jarsigner missing. Install a JDK and add its bin directory to PATH." }

$tasks = @(":app:lintRelease", ":app:bundleRelease")
if (-not $SkipLocalUnitTests) {
    $tasks = @(":app:testDebugUnitTest") + $tasks
}
Write-Host "Building signed Cue release candidate. Existing phone apps/data are not touched."
& .\gradlew.bat @tasks --stacktrace
if ($LASTEXITCODE -ne 0) { throw "Gradle release build failed. No bundle is approved." }
$bundle = "app\build\outputs\bundle\release\app-release.aab"
if (-not (Test-Path $bundle -PathType Leaf)) { throw "Gradle did not produce a release AAB." }
& $jarsigner -verify -strict -certs -verbose $bundle | Out-Null
if ($LASTEXITCODE -ne 0) { throw "AAB signing verification FAILED." }

New-Item -ItemType Directory -Path "qa-results" -Force | Out-Null
$report = "qa-results\cue-release-$(Get-Date -Format yyyyMMdd-HHmmss).txt"
$commit = (& git rev-parse HEAD 2>$null)
$sha = (Get-FileHash $bundle -Algorithm SHA256).Hash
@(
    "Cue signed release candidate"
    "Commit: $commit"
    "Target SDK: 36"
    "Bundle: $bundle"
    "SHA256: $sha"
    "jarsigner strict verification: PASS"
    "Next gate: Play Console account, uploaded-key fingerprint comparison, policy review and internal test installation."
) | Set-Content -Path $report
Write-Host "SIGNED AAB VERIFIED: $bundle"
Write-Host "Local audit: $report"
Write-Host "Back up your private keystore and passwords securely; do not publish them."
