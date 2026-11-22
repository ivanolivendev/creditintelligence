param(
    [string] $BaseUrl = 'http://127.0.0.1:18080',
    [string] $Username = 'analyst',
    [string] $Password = 'local_demo_only_change_me'
)

$ErrorActionPreference = 'Stop'
$auth = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("${Username}:${Password}"))
$headers = @{ Authorization = "Basic $auth"; 'X-Correlation-ID' = 'smoke-bedrock-demo' }
$identifier = 'DEMO-' + [guid]::NewGuid().ToString('N').Substring(0, 12)
$body = @{
    applicant = @{ name = 'Bedrock Demo'; syntheticIdentifier = $identifier }
    declaredMonthlyIncome = 8000.00
    currency = 'BRL'
} | ConvertTo-Json -Depth 4
$proposal = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/proposals" -Headers $headers -ContentType 'application/json' -Body $body
$fixture = (Resolve-Path (Join-Path $PSScriptRoot '../fixtures/documents/income-proof-demo.pdf')).Path
$documentsUrl = "$BaseUrl/api/v1/proposals/$($proposal.id)/documents"
$document = (& curl.exe --silent --show-error --user "${Username}:${Password}" --form 'type=INCOME_PROOF' --form "file=@$fixture;type=application/pdf" $documentsUrl) | ConvertFrom-Json
if (-not $document.id) { throw 'Synthetic fixture upload failed' }
$analysis = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/proposals/$($proposal.id)/analyses" -Headers $headers
if ($analysis.status -ne 'COMPLETED' -or $analysis.aiExplanationStatus -ne 'AVAILABLE' -or
    $analysis.aiExplanation.provider -ne 'BEDROCK' -or
    $analysis.findings[0].result -ne 'WARNING') {
    throw "Bedrock journey failed: analysis=$($analysis.status), explanation=$($analysis.aiExplanationStatus), provider=$($analysis.aiExplanation.provider)"
}
Write-Host "BEDROCK PASS analysis=$($analysis.analysisId) provider=$($analysis.aiExplanation.provider) model=$($analysis.aiExplanation.modelId) finding=$($analysis.findings[0].result)"
