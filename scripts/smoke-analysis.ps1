param(
    [string] $BaseUrl = 'http://127.0.0.1:18080',
    [string] $Username = 'analyst',
    [string] $Password = 'local_demo_only_change_me'
)

$ErrorActionPreference = 'Stop'
$auth = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("${Username}:${Password}"))
$headers = @{ Authorization = "Basic $auth"; 'X-Correlation-ID' = 'smoke-analysis-demo' }
function Get-Status {
    param([hashtable] $Request)
    try {
        return [int](Invoke-WebRequest @Request -UseBasicParsing).StatusCode
    } catch {
        if ($_.Exception.Response) { return [int]$_.Exception.Response.StatusCode }
        throw
    }
}
$identifier = 'DEMO-' + [guid]::NewGuid().ToString('N').Substring(0, 12)
$body = @{
    applicant = @{ name = 'Maria Silva'; syntheticIdentifier = $identifier }
    declaredMonthlyIncome = 8000.00
    currency = 'BRL'
} | ConvertTo-Json -Depth 4
$proposal = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/proposals" -Headers $headers -ContentType 'application/json' -Body $body
$fixture = (Resolve-Path (Join-Path $PSScriptRoot '../fixtures/documents/income-proof-demo.pdf')).Path
$documentsUrl = "$BaseUrl/api/v1/proposals/$($proposal.id)/documents"
$document = (& curl.exe --silent --show-error --user "${Username}:${Password}" --form 'type=INCOME_PROOF' --form "file=@$fixture;type=application/pdf" $documentsUrl) | ConvertFrom-Json
if (-not $document.id) { throw 'Document upload failed' }

$analysisUrl = "$BaseUrl/api/v1/proposals/$($proposal.id)/analyses"
$analysisKey = "smoke-analysis-$identifier"
$keyedHeaders = $headers.Clone()
$keyedHeaders['Idempotency-Key'] = $analysisKey
$analysis = Invoke-RestMethod -Method Post -Uri $analysisUrl -Headers $keyedHeaders
$income = @($analysis.extractedFields | Where-Object fieldName -eq 'monthlyIncome')[0]
$finding = @($analysis.findings | Where-Object code -eq 'INCOME_CONSISTENCY')[0]
if ($analysis.status -ne 'COMPLETED' -or $analysis.policyVersion -ne 'POLICY-1.0' -or
    $income.value -ne '7500.00' -or $income.page -ne 1 -or
    $finding.result -ne 'WARNING' -or $finding.evidenceRef -ne $income.evidenceRef -or
    $analysis.aiExplanationStatus -ne 'AVAILABLE' -or $analysis.aiExplanation.provider -ne 'BEDROCK' -or
    $analysis.aiExplanation.modelId -ne 'deepseek.v3.2') {
    throw "Analysis failed: status=$($analysis.status) code=$($analysis.errorCode) income=$($income.value) result=$($finding.result)"
}
Write-Host "T05/T07/T08/T09 PASS analysis=$($analysis.analysisId) income=$($income.value) finding=$($finding.result) AI=BEDROCK/DeepSeek"

$dossier = Invoke-RestMethod -Uri "$BaseUrl/api/v1/proposals/$($proposal.id)/dossier?analysisId=$($analysis.analysisId)" -Headers $headers
$repeated = Invoke-RestMethod -Method Post -Uri $analysisUrl -Headers $keyedHeaders
if ($dossier.analysisId -ne $analysis.analysisId -or $repeated.analysisId -ne $analysis.analysisId -or
    $dossier.documents.Count -ne 1 -or $dossier.documents[0].version -ne 1) {
    throw 'Dossier or duplicate analysis differs from snapshot'
}
Write-Host 'DOSSIER PASS persisted snapshot, policy, evidence and duplicate reuse'

$secondDocument = (& curl.exe --silent --show-error --user "${Username}:${Password}" --form 'type=INCOME_PROOF' --form "file=@$fixture;type=application/pdf" $documentsUrl) | ConvertFrom-Json
$changedKeyStatus = Get-Status @{ Method = 'Post'; Uri = $analysisUrl; Headers = $keyedHeaders }
if ($changedKeyStatus -ne 409) { throw "Idempotency-Key changed input status=$changedKeyStatus" }
$newAnalysis = Invoke-RestMethod -Method Post -Uri $analysisUrl -Headers $headers
$oldDossier = Invoke-RestMethod -Uri "$BaseUrl/api/v1/proposals/$($proposal.id)/dossier?analysisId=$($analysis.analysisId)" -Headers $headers
if ($secondDocument.version -ne 2 -or $newAnalysis.analysisId -eq $analysis.analysisId -or
    $newAnalysis.documents[0].version -ne 2 -or $oldDossier.documents[0].version -ne 1) {
    throw 'T16: new document did not create a new snapshot while preserving the old one'
}
Write-Host 'T16 PASS new document version creates new analysis; old dossier preserved'
Write-Host 'T12 PASS repeated Idempotency-Key reuses analysis; changed input returns 409'

$decisionsUrl = "$BaseUrl/api/v1/proposals/$($proposal.id)/decisions"
$staleBody = @{ analysisId = $analysis.analysisId; documentSnapshotHash = $analysis.documentSnapshotHash;
    outcome = 'MORE_INFO_REQUIRED'; reason = 'Analise demonstrativa: solicitar comprovante adicional.' } | ConvertTo-Json
$staleStatus = Get-Status @{ Method = 'Post'; Uri = $decisionsUrl; Headers = $headers;
    ContentType = 'application/json'; Body = $staleBody }
$decisionBody = @{ analysisId = $newAnalysis.analysisId; documentSnapshotHash = $newAnalysis.documentSnapshotHash;
    outcome = 'MORE_INFO_REQUIRED'; reason = 'Analise demonstrativa: solicitar comprovante adicional.' } | ConvertTo-Json
$unauthorizedStatus = Get-Status @{ Method = 'Post'; Uri = $decisionsUrl;
    ContentType = 'application/json'; Body = $decisionBody }
if ($staleStatus -ne 409 -or $unauthorizedStatus -ne 401) {
    throw "T18: stale=$staleStatus unauthenticated=$unauthorizedStatus"
}
$decision = Invoke-RestMethod -Method Post -Uri $decisionsUrl -Headers $headers -ContentType 'application/json' -Body $decisionBody
$listedDecisions = Invoke-RestMethod -Uri $decisionsUrl -Headers $headers
$duplicateStatus = Get-Status @{ Method = 'Post'; Uri = $decisionsUrl; Headers = $headers;
    ContentType = 'application/json'; Body = $decisionBody }
if ($decision.analystId -ne $Username -or $decision.analysisId -ne $newAnalysis.analysisId -or
    $decision.outcome -ne 'MORE_INFO_REQUIRED' -or $listedDecisions.Count -ne 1 -or $duplicateStatus -ne 409) {
    throw "T17/T18: decision actor=$($decision.analystId) duplicate=$duplicateStatus"
}
Write-Host 'T17/T18 PASS authenticated decision, stale=409, unauthenticated=401, duplicate=409'

$finalProposalBody = @{
    applicant = @{ name = 'Bruno Demo'; syntheticIdentifier = ('DEMO-' + [guid]::NewGuid().ToString('N').Substring(0, 12)) }
    declaredMonthlyIncome = 8000.00
    currency = 'BRL'
} | ConvertTo-Json -Depth 4
$finalProposal = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/proposals" -Headers $headers -ContentType 'application/json' -Body $finalProposalBody
$finalDocumentsUrl = "$BaseUrl/api/v1/proposals/$($finalProposal.id)/documents"
$null = & curl.exe --silent --show-error --user "${Username}:${Password}" --form 'type=INCOME_PROOF' --form "file=@$fixture;type=application/pdf" $finalDocumentsUrl
$finalAnalysisUrl = "$BaseUrl/api/v1/proposals/$($finalProposal.id)/analyses"
$finalAnalysis = Invoke-RestMethod -Method Post -Uri $finalAnalysisUrl -Headers $headers
$finalDecisionBody = @{ analysisId = $finalAnalysis.analysisId; documentSnapshotHash = $finalAnalysis.documentSnapshotHash;
    outcome = 'REJECTED'; reason = 'Decisao humana demonstrativa.' } | ConvertTo-Json
$null = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/proposals/$($finalProposal.id)/decisions" -Headers $headers -ContentType 'application/json' -Body $finalDecisionBody
$finalAnalysisStatus = Get-Status @{ Method = 'Post'; Uri = $finalAnalysisUrl; Headers = $headers }
$finalUploadStatus = [int](& curl.exe --silent --output NUL --write-out '%{http_code}' --user "${Username}:${Password}" --form 'type=INCOME_PROOF' --form "file=@$fixture;type=application/pdf" $finalDocumentsUrl)
if ($finalAnalysisStatus -ne 409 -or $finalUploadStatus -ne 409) {
    throw "Final decision did not freeze proposal: analysis=$finalAnalysisStatus upload=$finalUploadStatus"
}
Write-Host 'FINAL DECISION PASS final proposal rejects new analysis and document'

$missingProposalBody = @{
    applicant = @{ name = 'Joao Demo'; syntheticIdentifier = ('DEMO-' + [guid]::NewGuid().ToString('N').Substring(0, 12)) }
    declaredMonthlyIncome = 8000.00
    currency = 'BRL'
} | ConvertTo-Json -Depth 4
$missingProposal = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/proposals" -Headers $headers -ContentType 'application/json' -Body $missingProposalBody
$missingAnalysis = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/proposals/$($missingProposal.id)/analyses" -Headers $headers
$missingIncome = @($missingAnalysis.findings | Where-Object code -eq 'INCOME_CONSISTENCY')[0]
$missingDocuments = @($missingAnalysis.findings | Where-Object code -eq 'REQUIRED_DOCUMENTS')[0]
if ($missingAnalysis.status -ne 'COMPLETED' -or $missingIncome.result -ne 'NOT_EVALUATED' -or $missingDocuments.result -ne 'FAIL') {
    throw 'T06: missing document produced a factual or favorable result'
}
Write-Host 'T06 PASS missing document is NOT_EVALUATED, required document FAIL'

$ambiguousProposalBody = @{
    applicant = @{ name = 'Ana Demo'; syntheticIdentifier = ('DEMO-' + [guid]::NewGuid().ToString('N').Substring(0, 12)) }
    declaredMonthlyIncome = 8000.00
    currency = 'BRL'
} | ConvertTo-Json -Depth 4
$ambiguousProposal = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/proposals" -Headers $headers -ContentType 'application/json' -Body $ambiguousProposalBody
$ambiguousFixture = (Resolve-Path (Join-Path $PSScriptRoot '../fixtures/documents/ambiguous-income-demo.pdf')).Path
$ambiguousDocumentsUrl = "$BaseUrl/api/v1/proposals/$($ambiguousProposal.id)/documents"
$ambiguousDocument = (& curl.exe --silent --show-error --user "${Username}:${Password}" --form 'type=INCOME_PROOF' --form "file=@$ambiguousFixture;type=application/pdf" $ambiguousDocumentsUrl) | ConvertFrom-Json
$ambiguousAnalysis = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/proposals/$($ambiguousProposal.id)/analyses" -Headers $headers
$ambiguousField = @($ambiguousAnalysis.extractedFields | Where-Object fieldName -eq 'monthlyIncome')[0]
$ambiguousFinding = @($ambiguousAnalysis.findings | Where-Object code -eq 'INCOME_CONSISTENCY')[0]
if (-not $ambiguousDocument.id -or $ambiguousField.extractionStatus -ne 'AMBIGUOUS' -or
    $ambiguousField.value -or $ambiguousFinding.result -ne 'NOT_EVALUATED') {
    throw 'T06: ambiguous income was inferred as a fact'
}
Write-Host 'T06 PASS ambiguous income remains nonfactual'
