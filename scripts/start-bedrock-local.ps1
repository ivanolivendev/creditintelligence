param(
    [string] $Profile = 'bedrock',
    [string] $Region = 'us-east-1',
    [string] $ModelId = 'deepseek.v3.2'
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$envPath = Join-Path $root '.bedrock.env'
Push-Location $root
try {
    $session = aws sts get-federation-token --name CreditIntelligenceLocal --policy file://infra/aws-demo/bedrock-invoke-policy.json --duration-seconds 3600 --profile $Profile --region $Region --output json | ConvertFrom-Json
    if ($LASTEXITCODE -ne 0 -or -not $session.Credentials.AccessKeyId) {
        throw 'Could not obtain temporary Bedrock credentials. Check the AWS profile and IAM/SCP permissions.'
    }

    $lines = @(
        'AI_PROVIDER=bedrock',
        "AWS_REGION=$Region",
        "BEDROCK_MODEL_ID=$ModelId",
        "AWS_ACCESS_KEY_ID=$($session.Credentials.AccessKeyId)",
        "AWS_SECRET_ACCESS_KEY=$($session.Credentials.SecretAccessKey)",
        "AWS_SESSION_TOKEN=$($session.Credentials.SessionToken)"
    )
    Set-Content -LiteralPath $envPath -Value $lines -Encoding ascii

    $envFiles = @()
    if (Test-Path -LiteralPath '.env') { $envFiles += @('--env-file', '.env') }
    $envFiles += @('--env-file', '.bedrock.env')
    docker compose @envFiles up --build -d
    if ($LASTEXITCODE -ne 0) { throw 'Docker Compose failed to start the Bedrock local profile.' }

    Write-Host 'Started local Docker Compose with temporary, Bedrock-scoped STS credentials (1-hour maximum).'
    Write-Host 'The Docker containers retain those credentials only until they expire or are recreated.'
}
finally {
    if (Test-Path -LiteralPath $envPath) { Remove-Item -LiteralPath $envPath -Force }
    Pop-Location
}
