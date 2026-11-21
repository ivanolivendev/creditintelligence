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
$documentsUrl = "$BaseUrl/api/v1/proposals/$($proposal.id)/documents"
$bureau = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/proposals/$($proposal.id)/bureau-consultations" -Headers $headers -ContentType 'application/json' -Body '{"caseName":"COMPLETE"}'
if ($bureau.report.status -ne 'AVAILABLE') { throw 'Bureau consultation was not available' }
foreach ($month in @('2026-08', '2026-09', '2026-10')) {
    foreach ($entry in @(@('PAYSLIP', 'payslip'), @('BANK_STATEMENT', 'statement'))) {
        $fixture = (Resolve-Path (Join-Path $PSScriptRoot "../fixtures/documents/$($entry[1])-$month-demo.pdf")).Path
        $document = (& curl.exe --silent --show-error --user "${Username}:${Password}" --form "type=$($entry[0])" --form "period=$month" --form "file=@$fixture;type=application/pdf" $documentsUrl) | ConvertFrom-Json
        if (-not $document.id) { throw "Synthetic fixture upload failed for $month/$($entry[0])" }
    }
}
$analysis = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/proposals/$($proposal.id)/analyses" -Headers $headers
if ($analysis.status -ne 'COMPLETED' -or $analysis.aiExplanationStatus -ne 'AVAILABLE' -or
    $analysis.aiExplanation.provider -ne 'BEDROCK' -or
    $analysis.findings[0].result -ne 'WARNING') {
    throw "Bedrock journey failed: analysis=$($analysis.status), explanation=$($analysis.aiExplanationStatus), provider=$($analysis.aiExplanation.provider)"
}
Write-Host "BEDROCK PASS analysis=$($analysis.analysisId) provider=$($analysis.aiExplanation.provider) model=$($analysis.aiExplanation.modelId) finding=$($analysis.findings[0].result)"
