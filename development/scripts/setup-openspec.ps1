# Run from the repository root in PowerShell. Non-destructive by default.
$ErrorActionPreference = 'Stop'
Write-Host 'Credit Intelligence - preparacao local' -ForegroundColor Cyan
foreach ($tool in @('git','node','npm','codex','openspec','docker','java','python')) {
  $cmd = Get-Command $tool -ErrorAction SilentlyContinue
  if ($cmd) { Write-Host "[OK] $tool" -ForegroundColor Green }
  else { Write-Warning "[AUSENTE] $tool (ver README do pacote)" }
}
$root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Set-Location $root
if (-not (Test-Path 'AGENTS.md')) { throw 'AGENTS.md nao encontrado na raiz do repositorio.' }
$bp = @('development/Credit_Intelligence_Master_Blueprint_V3.docx','Credit_Intelligence_Master_Blueprint_V3.docx','docs/Credit_Intelligence_Master_Blueprint_V3.docx') | Where-Object { Test-Path $_ }
if (-not $bp) { Write-Warning 'Blueprint nao encontrado nas localizacoes padrao; confira onde foi salvo.' }
else { Write-Host "Blueprint: $($bp[0])" }
if (Test-Path 'openspec') {
  Write-Host 'OpenSpec parece ja inicializado; nao vou sobrescrever.' -ForegroundColor Yellow
} elseif (Get-Command openspec -ErrorAction SilentlyContinue) {
  Write-Host 'Inicializando OpenSpec para Codex...' -ForegroundColor Cyan
  openspec init --tools codex
  if ($LASTEXITCODE -ne 0 -or -not (Test-Path 'openspec')) { throw 'OpenSpec falhou ou nao criou a pasta openspec.' }
} else {
  Write-Warning 'Instale OpenSpec: npm install -g @fission-ai/openspec@latest'
}
Write-Host 'Preparacao finalizada. Consulte development/docs/PLANO-IMPLEMENTACAO.md.'
