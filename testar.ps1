$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
Set-Location $PSScriptRoot
$site = 'http://localhost:8080'
$script:ok = 0
$script:falhou = 0
$log = Join-Path $PSScriptRoot 'testar.log'
Start-Transcript -Path $log -Force | Out-Null

function Teste([string]$nome, [scriptblock]$bloco) {
  try {
    $detalhe = & $bloco
    Write-Host ("[OK]      " + $nome + $(if ($detalhe) { "  ->  " + $detalhe } else { "" })) -ForegroundColor Green
    $script:ok++
  } catch {
    Write-Host ("[FALHOU]  " + $nome + "  ->  " + $_.Exception.Message) -ForegroundColor Red
    $script:falhou++
  }
}

function Texto([string]$s) {
  [Text.Encoding]::UTF8.GetString([Text.Encoding]::GetEncoding(28591).GetBytes($s))
}

function Json([string]$caminho, $corpo) {
  $bytes = [Text.Encoding]::UTF8.GetBytes(($corpo | ConvertTo-Json -Depth 10))
  Invoke-RestMethod -Uri ($site + $caminho) -Method Post -Body $bytes -ContentType 'application/json; charset=utf-8'
}

Write-Host ""
Write-Host "  MOKKA - TESTE GERAL" -ForegroundColor Cyan
Write-Host "  -------------------" -ForegroundColor Cyan
Write-Host "  Esperando o sistema ligar (pode levar ate 2 minutos na primeira vez)..."
$pronto = $false
for ($i = 0; $i -lt 90; $i++) {
  try {
    Invoke-RestMethod 'http://127.0.0.1:8000/saude' | Out-Null
    Invoke-RestMethod ($site + '/api/unidades') | Out-Null
    $pronto = $true
    break
  } catch { Start-Sleep -Seconds 2 }
}
Write-Host ""

Teste 'Sistema ligado (Python e Java)' {
  if (-not $pronto) { throw 'nao respondeu em 3 minutos; veja as janelas Similaridade e Aplicacao' }
  $s = Invoke-RestMethod 'http://127.0.0.1:8000/saude'
  "$($s.codigos) codigos carregados no Python"
}

Teste 'Numeros do catalogo' {
  $r = Invoke-RestMethod ($site + '/api/codigos/resumo')
  if ($r.na_base -le 0) { throw 'catalogo vazio' }
  "$($r.na_base) na base, $($r.oficiais) oficiais, $($r.repetidos) repetidos"
}

Teste 'Consulta de codigo que ja existe (BLOQUEAR)' {
  $r = Json '/api/consultas' @{ descricao = 'ELETRICISTA HORA EXTRA 100%'; unidade = 'hora'; solicitante = 'Teste automatico' }
  if ($r.resultado -ne 'BLOQUEAR') { throw "veio $($r.resultado)" }
  Texto $r.motivo
}

Teste 'Consulta de servico novo (LIBERAR)' {
  $r = Json '/api/consultas' @{ descricao = 'TREINAMENTO EM REALIDADE VIRTUAL'; unidade = 'SV'; solicitante = 'Teste automatico' }
  if ($r.resultado -ne 'LIBERAR') { throw "veio $($r.resultado)" }
  'nenhum codigo igual'
}

Teste 'Correcao de fala ou digitacao (SOLDA DOR)' {
  $r = Json '/api/consultas' @{ descricao = 'SOLDA DOR HORA EXTRA 50%'; unidade = 'H'; solicitante = 'Teste automatico' }
  if (-not $r.sugestao) { throw 'nao sugeriu correcao' }
  "sugeriu: $($r.sugestao.descricao)"
}

Teste 'Erro em portugues para unidade errada' {
  $corpo = Join-Path $env:TEMP 'mokka_unidade.json'
  [IO.File]::WriteAllText($corpo, '{"descricao":"PEDREIRO HN","unidade":"caixa","solicitante":"Teste automatico"}')
  $txt = & curl.exe -s -H "Content-Type: application/json" --data-binary "@$corpo" ($site + '/api/consultas')
  $msg = ($txt | ConvertFrom-Json).erro
  if ($msg -notmatch 'Unidade') { throw "resposta: $txt" }
  $msg
}

Teste 'Planilha em lote (modelo com 14 linhas)' {
  $modelo = Join-Path $PSScriptRoot 'front\modelo_lote.xlsx'
  if (-not (Test-Path $modelo)) { $modelo = Join-Path $PSScriptRoot 'codigos-api\src\main\resources\static\modelo_lote.xlsx' }
  $txt = & curl.exe -s -F "arquivo=@$modelo" ($site + '/api/lotes/analisar')
  $r = $txt | ConvertFrom-Json
  if ($r.resumo.TOTAL -ne 14) { throw "veio $($r.resumo.TOTAL) linhas" }
  $script:lote = $r
  "$($r.resumo.BLOQUEAR) ja existem, $($r.resumo.REVISAR) parecidos, $($r.resumo.LIBERAR) novos, $($r.resumo.REPETIDO) repetidos, $($r.resumo.ERRO) com erro, em $($r.milissegundos) ms"
}

Teste 'Excel organizado (exportar)' {
  $arq = Join-Path $env:TEMP 'mokka_teste.xlsx'
  $bytes = [Text.Encoding]::UTF8.GetBytes((@{ linhas = $script:lote.linhas } | ConvertTo-Json -Depth 10))
  Invoke-WebRequest -Uri ($site + '/api/lotes/exportar') -Method Post -Body $bytes -ContentType 'application/json; charset=utf-8' -OutFile $arq -UseBasicParsing
  $tam = (Get-Item $arq).Length
  if ($tam -lt 1000) { throw 'arquivo vazio' }
  "$([math]::Round($tam / 1KB)) KB"
}

Teste 'Swagger (API)' {
  $r = Invoke-WebRequest ($site + '/swagger-ui/index.html') -UseBasicParsing
  if ($r.StatusCode -ne 200) { throw "status $($r.StatusCode)" }
  "$site/swagger"
}

Teste 'Testes automaticos do Python' {
  $py = Join-Path $PSScriptRoot 'saneamento\.venv\Scripts\python.exe'
  Push-Location (Join-Path $PSScriptRoot 'saneamento')
  $antes = $ErrorActionPreference
  $ErrorActionPreference = 'Continue'
  try {
    $saida = & $py test_padronizar.py 2>&1 | ForEach-Object { "$_" }
    if ($LASTEXITCODE -ne 0) { throw (($saida | Select-Object -Last 3) -join ' ') }
    "$(($saida | Select-String '^ok ').Count) testes ok"
  } finally { Pop-Location; $ErrorActionPreference = $antes }
}

Teste 'Microfone conectado' {
  $mics = @(Get-PnpDevice -Class AudioEndpoint -Status OK -ErrorAction SilentlyContinue | Where-Object { $_.FriendlyName -match 'Micro|Mic|Headset' })
  if ($mics.Count -eq 0) { throw 'nenhum microfone encontrado no Windows' }
  ($mics | Select-Object -First 2 -ExpandProperty FriendlyName) -join '; '
}

Teste 'Microsoft Edge instalado' {
  $edge = @("${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe", "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe") | Where-Object { Test-Path $_ } | Select-Object -First 1
  if (-not $edge) { throw 'Edge nao encontrado; use o Chrome' }
  'ok'
}

Teste 'Internet (a voz do navegador precisa)' {
  Invoke-WebRequest 'https://www.microsoft.com' -UseBasicParsing -TimeoutSec 8 | Out-Null
  'ok'
}

Write-Host ""
if ($script:falhou -eq 0) {
  Write-Host "  TUDO CERTO: $($script:ok) de $($script:ok) testes passaram." -ForegroundColor Green
} else {
  Write-Host "  $($script:ok) passaram e $($script:falhou) falharam. Veja as linhas em vermelho." -ForegroundColor Yellow
}
Write-Host "  Resultado salvo em testar.log"
Write-Host ""
Write-Host "  Abrindo o teste do microfone no Edge..." -ForegroundColor Cyan
Write-Host "  1. Clique em Falar e permita o microfone quando o Edge pedir."
Write-Host "  2. Leia as frases que aparecem na tela, uma de cada vez."
Stop-Transcript | Out-Null
Start-Process 'msedge' ($site + '/?' + (Get-Random) + '#testar-voz')
