param(
    [ValidateSet('verify', 'mysql-test', 'run', 'demo')]
    [string]$Action = 'verify'
)

$ErrorActionPreference = 'Stop'

# Returns the major Java version of a JDK folder (e.g. 21), or 0 if it is not a JDK.
function Get-JdkVersion([string]$path) {
    if (-not $path -or -not (Test-Path -LiteralPath (Join-Path $path 'bin\javac.exe'))) { return 0 }
    $release = Join-Path $path 'release'
    if (-not (Test-Path -LiteralPath $release)) { return 0 }
    $line = Select-String -LiteralPath $release -Pattern '^JAVA_VERSION="(\d+)' | Select-Object -First 1
    if ($line) { return [int]$line.Matches[0].Groups[1].Value }
    return 0
}

# Look for a JDK 21+ in the usual places: JAVA_HOME, a copy inside .tools, javac on PATH,
# then the default install folders of the common JDK installers.
$candidates = @($env:JAVA_HOME)
$candidates += Get-ChildItem -Directory -Path (Join-Path $PSScriptRoot '.tools') -Filter 'jdk-*' -ErrorAction SilentlyContinue | ForEach-Object FullName
$javacOnPath = Get-Command javac.exe -ErrorAction SilentlyContinue
if ($javacOnPath) { $candidates += Split-Path (Split-Path $javacOnPath.Source) }
foreach ($root in @("$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Java", "$env:ProgramFiles\Microsoft", "$env:ProgramFiles\Zulu", "$env:ProgramFiles\Amazon Corretto")) {
    $candidates += Get-ChildItem -Directory -Path $root -Filter '*jdk*21*' -ErrorAction SilentlyContinue | ForEach-Object FullName
}

$jdk = $candidates | Where-Object { (Get-JdkVersion $_) -ge 21 } | Select-Object -First 1
if (-not $jdk) {
    Write-Host ''
    Write-Host 'Java 21 (JDK) was not found.' -ForegroundColor Red
    Write-Host 'Install "Temurin 21 (LTS)" from https://adoptium.net/ and tick "Set JAVA_HOME" in the installer,'
    Write-Host 'then close and reopen PowerShell and run this script again.'
    exit 1
}
Write-Host "Using Java from $jdk"
$env:JAVA_HOME = $jdk
$env:PATH = "$(Join-Path $jdk 'bin');$env:PATH"

# The Maven Wrapper (mvnw.cmd) downloads the right Maven version on first use,
# so nobody has to install Maven by hand.
$maven = Join-Path $PSScriptRoot 'backend\mvnw.cmd'
$mavenRepository = Join-Path $PSScriptRoot '.tools\maven-repository'
New-Item -ItemType Directory -Force -Path $mavenRepository | Out-Null
$mavenArgs = @('-B', '-ntp', "-Dmaven.repo.local=$mavenRepository")

Push-Location (Join-Path $PSScriptRoot 'backend')
try {
    switch ($Action) {
        'verify' { & $maven @mavenArgs verify }
        'mysql-test' { & $maven @mavenArgs -Pmysql-it verify }
        'run' { & $maven @mavenArgs spring-boot:run }
        'demo' {
            if (-not $env:SEED_ADMIN_PASSWORD) {
                $credential = Get-Credential -UserName 'admin@restorant.local' -Message 'Set the admin login (password: at least 10 characters). Only used the first time; press Cancel if the admin account already exists.'
                if ($credential) {
                    $env:SEED_ADMIN_PASSWORD = $credential.GetNetworkCredential().Password
                    $env:SEED_ADMIN_EMAIL = $credential.UserName
                } else {
                    Write-Host 'No admin password given. Starting without creating an admin account.' -ForegroundColor Yellow
                }
            }
            & $maven @mavenArgs '-Dspring-boot.run.profiles=demo' spring-boot:run
        }
    }
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally {
    Pop-Location
}
