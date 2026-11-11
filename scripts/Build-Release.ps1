param([switch]$Online)

$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path $PSScriptRoot -Parent
$workspaceDirectory = Split-Path $projectDirectory -Parent
$buildDirectory = Join-Path $workspaceDirectory '.coloros-ota-build'
$signingFile = Join-Path $workspaceDirectory '.ganyu-signing/signing.properties'
if (!(Test-Path -LiteralPath $signingFile)) {
    throw 'Signing files are missing. Run Initialize-Signing.ps1 first.'
}
$javacPath = (Get-Command javac.exe -ErrorAction Stop).Source
$env:JAVA_HOME = Split-Path (Split-Path $javacPath -Parent) -Parent
$env:ANDROID_HOME = Join-Path $buildDirectory 'sdk'
$env:ANDROID_USER_HOME = Join-Path $buildDirectory 'android-user'
$env:GRADLE_USER_HOME = Join-Path $buildDirectory 'gradle-user'
$gradleArguments = @('-p', $projectDirectory, '--no-daemon', '--console=plain', '--max-workers=4', ':app:assembleRelease', ':app:lintRelease')
if (!$Online) { $gradleArguments += '--offline' }
& (Join-Path $buildDirectory 'gradle-8.9/bin/gradle.bat') @gradleArguments
if ($LASTEXITCODE -ne 0) { throw 'Release build failed.' }
$apk = Join-Path $projectDirectory 'app/build/outputs/apk/release/app-release.apk'
& (Join-Path $env:ANDROID_HOME 'build-tools/34.0.0/apksigner.bat') verify --verbose --print-certs $apk
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
Write-Host "Signed release APK: $apk"
