param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$CertificateName
)

$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path $PSScriptRoot -Parent
$workspaceDirectory = Split-Path $projectDirectory -Parent
$signingDirectory = Join-Path $workspaceDirectory '.ganyu-signing'
$propertiesFile = Join-Path $signingDirectory 'signing.properties'
$keystoreFile = Join-Path $signingDirectory 'ganyu-release.p12'

if ((Test-Path -LiteralPath $propertiesFile) -or (Test-Path -LiteralPath $keystoreFile)) {
    throw 'Signing files already exist. Refusing to replace the existing release identity.'
}
$certificateDisplayName = $CertificateName.Trim()
if (!$certificateDisplayName -or $certificateDisplayName -match '[\x00-\x1f]') {
    throw 'Choose a nonempty public name without control characters.'
}
$keytool = (Get-Command keytool.exe -ErrorAction Stop).Source
New-Item -ItemType Directory -Force -Path $signingDirectory | Out-Null

# Keep the key and its password readable only by this Windows account and SYSTEM.
$currentSid = [System.Security.Principal.WindowsIdentity]::GetCurrent().User.Value
& icacls.exe $signingDirectory /inheritance:r /grant:r "*${currentSid}:(OI)(CI)F" '*S-1-5-18:(OI)(CI)F' | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Unable to protect the signing directory.' }

$randomBytes = New-Object byte[] 32
$generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try { $generator.GetBytes($randomBytes) } finally { $generator.Dispose() }
$generatedPassword = [System.BitConverter]::ToString($randomBytes).Replace('-', '').ToLowerInvariant()
$escapedName = $certificateDisplayName.Replace('\', '\\').Replace('"', '\"')
$distinguishedName = 'CN="' + $escapedName + '", O=Personal'
$previousPasswordEnvironment = [Environment]::GetEnvironmentVariable('GANYU_KEYSTORE_PASSWORD', 'Process')

try {
    [Environment]::SetEnvironmentVariable('GANYU_KEYSTORE_PASSWORD', $generatedPassword, 'Process')
    & $keytool -genkeypair -noprompt -storetype PKCS12 -keystore $keystoreFile `
        -alias ganyu -keyalg RSA -keysize 3072 -sigalg SHA256withRSA -validity 14610 `
        -dname $distinguishedName -storepass:env GANYU_KEYSTORE_PASSWORD -keypass:env GANYU_KEYSTORE_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw 'Key generation failed.' }
    $properties = @(
        'storeFile=ganyu-release.p12'
        'keyAlias=ganyu'
        ('storePassword=' + $generatedPassword)
        ('keyPassword=' + $generatedPassword)
    ) -join "`n"
    [System.IO.File]::WriteAllText($propertiesFile, $properties + "`n", [System.Text.UTF8Encoding]::new($false))
    [System.IO.File]::WriteAllText((Join-Path $signingDirectory 'owner.txt'), $certificateDisplayName, [System.Text.UTF8Encoding]::new($false))
    & $keytool -exportcert -rfc -alias ganyu -keystore $keystoreFile `
        -storepass:env GANYU_KEYSTORE_PASSWORD -file (Join-Path $signingDirectory 'ganyu-release-certificate.pem')
    if ($LASTEXITCODE -ne 0) { throw 'Certificate export failed.' }
} finally {
    [Environment]::SetEnvironmentVariable('GANYU_KEYSTORE_PASSWORD', $previousPasswordEnvironment, 'Process')
    $generatedPassword = $null
    $properties = $null
    [Array]::Clear($randomBytes, 0, $randomBytes.Length)
}

Write-Host 'Release signing identity created. The password was not printed.'
Write-Host "Private backup directory: $signingDirectory"
