param(
    [string] $EnvFile = '.env'
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $root
try {
    $envPath = Join-Path $root $EnvFile
    if (-not (Test-Path -LiteralPath $envPath)) {
        throw "Environment file not found: $EnvFile"
    }
    $envLines = Get-Content -LiteralPath $envPath
    $required = @('AI_PROVIDER=bedrock', 'BEDROCK_MODEL_ID=deepseek.v3.2', 'AWS_REGION=')
    foreach ($entry in $required) {
        if (-not ($envLines | Where-Object { $_.StartsWith($entry) })) {
            throw "Missing required Bedrock setting: $entry"
        }
    }
    if (-not ($envLines | Where-Object { $_ -match '^AWS_ACCESS_KEY_ID=\S+' })) {
        throw 'AWS_ACCESS_KEY_ID is missing from the environment file.'
    }
    if (-not ($envLines | Where-Object { $_ -match '^AWS_SECRET_ACCESS_KEY=\S+' })) {
        throw 'AWS_SECRET_ACCESS_KEY is missing from the environment file.'
    }

    docker compose --env-file $EnvFile up --build -d
    if ($LASTEXITCODE -ne 0) { throw 'Docker Compose failed to start the Bedrock local profile.' }

    Write-Host 'Started local Docker Compose with persistent IAM credentials from the ignored environment file.'
}
finally {
    Pop-Location
}
