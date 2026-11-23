param(
    [switch] $SkipIntegration,
    [switch] $SkipClean
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$lockPath = Join-Path $repoRoot '.java-verification.lock'
$lock = $null

try {
    $lock = [System.IO.File]::Open($lockPath, [System.IO.FileMode]::OpenOrCreate,
        [System.IO.FileAccess]::ReadWrite, [System.IO.FileShare]::None)
    $clean = if ($SkipClean) { '' } else { 'clean ' }
    $unitCommand = "docker compose --profile test run --rm backend-test mvn -B -ntp ${clean}test"
    Write-Host "Running Java unit tests: $unitCommand"
    & powershell -NoProfile -Command $unitCommand
    if ($LASTEXITCODE -ne 0) { throw "Java unit tests failed with exit code $LASTEXITCODE" }

    if (-not $SkipIntegration) {
        $integrationCommand = 'docker compose --profile test run --rm backend-test mvn -B -ntp -Pintegration verify'
        Write-Host "Running Java integration tests: $integrationCommand"
        & powershell -NoProfile -Command $integrationCommand
        if ($LASTEXITCODE -ne 0) { throw "Java integration tests failed with exit code $LASTEXITCODE" }
    }
}
finally {
    if ($lock) { $lock.Dispose() }
    if (Test-Path -LiteralPath $lockPath) { Remove-Item -LiteralPath $lockPath -Force }
}
