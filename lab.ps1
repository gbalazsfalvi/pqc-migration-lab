<#
.SYNOPSIS
Runs the local laboratory with JDK 25 without changing the user's persistent Java settings.
.DESCRIPTION
Prefers the ignored project-local toolchain when it exists, then JAVA_HOME, then java on PATH.
Use verify for quality gates, probe for cryptography, demo for policy evidence, migration-demo for lifecycle evidence, serve for the workbench,
and benchmark for explicitly configured JMH measurements.
#>
[CmdletBinding()]
param(
    [ValidateSet('verify', 'probe', 'demo', 'migration-demo', 'legacy-migration-demo', 'compose-demo', 'rehearsal-demo', 'compose-rehearsal', 'compose-up', 'compose-down', 'init', 'publisher', 'receiver', 'serve', 'benchmark', 'format', 'help')]
    [string]$Task = 'probe',
    [ValidateSet('CLASSICAL_ONLY', 'SHADOW_PQC', 'BOTH_REQUIRED', 'PQC_REQUIRED')]
    [string]$Policy = 'BOTH_REQUIRED',
    [ValidateSet('smoke', 'quick', 'standard')]
    [string]$BenchmarkProfile = 'standard',
    [ValidateRange(1, 65535)]
    [int]$PublisherPort = 8781,
    [ValidateRange(1, 65535)]
    [int]$ReceiverPort = 8782
)

$ErrorActionPreference = 'Stop'
if ($Task -eq 'help') {
    Write-Output 'Usage: .\lab.ps1 [verify | probe | demo | migration-demo | legacy-migration-demo | compose-demo | rehearsal-demo | compose-rehearsal | compose-up | compose-down | init | publisher | receiver | serve | benchmark | format | help] [-Policy BOTH_REQUIRED] [-PublisherPort 8781] [-ReceiverPort 8782] [-BenchmarkProfile standard]'
    exit 0
}

# Compose builds Java and Node inside pinned images; these tasks do not require host build toolchains.
if ($Task -in @('compose-up', 'compose-down')) {
    if ($PublisherPort -eq $ReceiverPort) { throw 'Use distinct publisher and receiver ports.' }
    if ($Policy -ne 'BOTH_REQUIRED') { throw 'The reference Compose deployment keeps BOTH_REQUIRED.' }
    if ($env:DOCKER_HOST -and $env:DOCKER_HOST -notmatch '^(npipe|unix)://') { throw 'Use a local Docker engine.' }
    $pqcDockerContext = (& docker context show).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Docker Desktop or a local Docker engine is required.' }
    $pqcDockerEndpoint = (& docker context inspect $pqcDockerContext --format '{{.Endpoints.docker.Host}}').Trim()
    if ($LASTEXITCODE -ne 0 -or $pqcDockerEndpoint -notmatch '^(npipe|unix)://') { throw 'Use a local Docker context.' }
    $pqcPreviousPublisherPort = $env:PQC_LAB_PUBLISHER_PORT
    $pqcPreviousReceiverPort = $env:PQC_LAB_RECEIVER_PORT
    try {
        $env:PQC_LAB_PUBLISHER_PORT = [string]$PublisherPort
        $env:PQC_LAB_RECEIVER_PORT = [string]$ReceiverPort
        $pqcComposeArguments = @('--context', $pqcDockerContext, 'compose', '--project-name', 'pqc-migration-lab', '--project-directory', $PSScriptRoot,
            '--file', (Join-Path $PSScriptRoot 'compose.yaml'))
        if ($Task -eq 'compose-up') { $pqcComposeArguments += @('up', '--detach', '--build', '--wait') }
        else { $pqcComposeArguments += @('down') }
        & docker @pqcComposeArguments
        if ($LASTEXITCODE -ne 0) { throw 'The local Compose operation did not complete.' }
        if ($Task -eq 'compose-up') { Write-Output "Workbench: http://127.0.0.1:$PublisherPort/" }
    } finally {
        $env:PQC_LAB_PUBLISHER_PORT = $pqcPreviousPublisherPort
        $env:PQC_LAB_RECEIVER_PORT = $pqcPreviousReceiverPort
    }
    exit 0
}

$pqcLocalJdk = Join-Path $PSScriptRoot '.local\toolchains\jdk-25.0.5+8'
$pqcPreviousJavaHome = $env:JAVA_HOME
$pqcJavaHome = $null
if (Test-Path -LiteralPath (Join-Path $pqcLocalJdk 'bin\java.exe')) {
    $pqcJavaHome = $pqcLocalJdk
} elseif ($env:JAVA_HOME) {
    $pqcJavaHome = $env:JAVA_HOME
} else {
    $pqcJavaCommand = Get-Command java -ErrorAction SilentlyContinue
    if ($pqcJavaCommand) {
        $pqcJavaHome = Split-Path (Split-Path $pqcJavaCommand.Source -Parent) -Parent
    }
}

# Read toolchain metadata instead of parsing locale-dependent console output.
if (-not $pqcJavaHome -or -not (Test-Path -LiteralPath (Join-Path $pqcJavaHome 'release'))) {
    throw 'JDK 25 was not found. Set JAVA_HOME to a JDK 25 installation; see README.md.'
}
$pqcRelease = Get-Content -LiteralPath (Join-Path $pqcJavaHome 'release')
if (-not ($pqcRelease | Select-String -Pattern '^JAVA_VERSION="25[.+"]')) {
    throw 'This laboratory requires JDK 25. Set JAVA_HOME to JDK 25; see README.md.'
}

$pqcGradleArguments = switch ($Task) {
    'verify' { @('clean', 'check', '--console=plain') }
    'probe' { @('probe', '--console=plain') }
    'demo' { @(':integration-app:demo', '--console=plain') }
    'migration-demo' { @('migrationDemo', '--console=plain') }
    'legacy-migration-demo' { @(':integration-app:legacyMigrationDemo', '--console=plain') }
    'compose-demo' { @('composeMigrationDemo', '--console=plain') }
    'rehearsal-demo' { @('rehearsalDemo', '--console=plain') }
    'compose-rehearsal' { @('composeRehearsalDemo', '--console=plain') }
    'init' { @(':integration-app:bootRun', '--args=--role=init', '--console=plain') }
    'publisher' { @(':integration-app:bootRun', "--args=--role=publisher --port=$PublisherPort --receiver-port=$ReceiverPort --policy=$Policy", '--console=plain') }
    'receiver' { @(':integration-app:bootRun', "--args=--role=receiver --port=$ReceiverPort --policy=$Policy", '--console=plain') }
    'serve' { @(':integration-app:bootRun', "--args=--policy=$Policy --publisher-port=$PublisherPort --receiver-port=$ReceiverPort", '--console=plain') }
    'benchmark' { @(":benchmarks:$BenchmarkProfile", '--console=plain') }
    'format' { @('spotlessApply', '--console=plain') }
}
$pqcExitCode = 1
Push-Location -LiteralPath $PSScriptRoot
try {
    # This assignment is process-local and is restored even when the build fails.
    $env:JAVA_HOME = $pqcJavaHome
    if ($Task -in @('verify', 'demo', 'migration-demo', 'legacy-migration-demo', 'compose-demo', 'rehearsal-demo', 'compose-rehearsal', 'init', 'publisher', 'receiver', 'serve', 'format')) {
        # Node is used at build time; the packaged Java application serves the finished UI itself.
        $pqcNode = Get-Command node -ErrorAction SilentlyContinue
        $pqcNpm = Get-Command npm.cmd -ErrorAction SilentlyContinue
        if (-not $pqcNode -or -not $pqcNpm) {
            throw 'Node.js 24.14 or newer in the 24 LTS line is required to build the workbench.'
        }
        $pqcNodeVersion = [Version]((& $pqcNode.Source --version).TrimStart('v'))
        if ($pqcNodeVersion.Major -ne 24 -or $pqcNodeVersion -lt [Version]'24.14.0') {
            throw 'Select Node.js 24.14 or newer in the 24 LTS line; see README.md.'
        }
        $pqcWorkbench = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'workbench'))
        $pqcExpectedRoot = [IO.Path]::GetFullPath($PSScriptRoot) + [IO.Path]::DirectorySeparatorChar
        if (-not $pqcWorkbench.StartsWith($pqcExpectedRoot, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'The workbench dependency directory must stay inside this project.'
        }
        $pqcFingerprint = ((Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $pqcWorkbench 'package.json')).Hash,
            (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $pqcWorkbench 'package-lock.json')).Hash) -join ':'
        $pqcMarker = Join-Path $PSScriptRoot '.local\workbench-install.sha256'
        $pqcInstalled = Test-Path -LiteralPath (Join-Path $pqcWorkbench 'node_modules\.package-lock.json')
        $pqcKnownFingerprint = if (Test-Path -LiteralPath $pqcMarker) { [IO.File]::ReadAllText($pqcMarker).Trim() } else { '' }
        if (-not $pqcInstalled -or $pqcKnownFingerprint -ne $pqcFingerprint) {
            & $pqcNpm.Source ci --prefix $pqcWorkbench --no-audit --no-fund
            if ($LASTEXITCODE -ne 0) { throw 'The pinned workbench dependencies could not be installed.' }
            [IO.Directory]::CreateDirectory((Split-Path $pqcMarker -Parent)) | Out-Null
            [IO.File]::WriteAllText($pqcMarker, $pqcFingerprint, [Text.UTF8Encoding]::new($false))
        }
    }
    & (Join-Path $PSScriptRoot 'gradlew.bat') @pqcGradleArguments
    $pqcExitCode = $LASTEXITCODE
    if ($Task -eq 'format' -and $pqcExitCode -eq 0) {
        & $pqcNpm.Source run format --prefix $pqcWorkbench
        $pqcExitCode = $LASTEXITCODE
    }
} finally {
    $env:JAVA_HOME = $pqcPreviousJavaHome
    Pop-Location
}
exit $pqcExitCode
