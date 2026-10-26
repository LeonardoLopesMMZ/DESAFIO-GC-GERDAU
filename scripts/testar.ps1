$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$raiz = Split-Path $PSScriptRoot -Parent
Set-Location $raiz
$site = 'http://localhost:8080'
$script:ok = 0
$script:falhou = 0
$log = Join-Path $raiz 'testar.log'
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

$script:token = ''

function Json([string]$caminho, $corpo) {
  $bytes = [Text.Encoding]::UTF8.GetBytes(($corpo | ConvertTo-Json -Depth 10))
  Invoke-RestMethod -Uri ($site + $caminho) -Method Post -Body $bytes -ContentType 'application/json; charset=utf-8' -Headers @{ Authorization = "Bearer $script:token" }
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

Teste 'Entrada com token' {
  $r = Json '/api/acesso/entrar' @{ nome = 'Teste automatico' }
  $script:token = $r.token
  $corpo = Join-Path $env:TEMP 'mokka_sem_token.json'
  [IO.File]::WriteAllText($corpo, '{"descricao":"PEDREIRO HN","unidade":"H"}')
  $txt = & curl.exe -s -H "Content-Type: application/json" --data-binary "@$corpo" ($site + '/api/consultas')
  if (($txt | ConvertFrom-Json).status -ne 401) { throw "consulta sem token deveria ser recusada: $txt" }
  "token valido ate $($r.expira); sem token a gravacao e recusada"
}

Teste 'Entrada pelo rosto (servidor)' {
  $tokenAntes = $script:token
  try {
    $script:token = (Json '/api/acesso/entrar' @{ nome = 'Teste rosto ' + (Get-Random) }).token
    $rnd = New-Object Random
    $d = 1..128 | ForEach-Object { [math]::Round($rnd.NextDouble() * 0.4 - 0.2, 5) }
    $foto = '/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAA0JCgsKCA0LCgsODg0PEyAVExISEyccHhcgLikxMC4pLSwzOko+MzZGNywtQFdBRkxOUlNSMj5aYVpQYEpRUk//2wBDAQ4ODhMREyYVFSZPNS01T09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT0//wAARCAAQABADASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwDiqKKK9M84/9k='
    Json '/api/acesso/rosto/cadastrar' @{ descritor = $d; foto = $foto } | Out-Null
    $script:token = ''
    $r = Json '/api/acesso/rosto/entrar' @{ descritor = $d; foto = $foto }
    if ($r.metodo -ne 'ROSTO' -or -not $r.retrato_id) { throw 'nao entrou pelo rosto' }
    $img = Invoke-WebRequest -UseBasicParsing ($site + '/api/acesso/retratos/' + $r.retrato_id) -Headers @{ Authorization = "Bearer $($r.token)" }
    if ($img.Content[0] -ne 0xFF -or $img.Content[1] -ne 0xD8) { throw 'a foto voltou com defeito' }
    $outro = 1..128 | ForEach-Object { [math]::Round($rnd.NextDouble() * 0.4 - 0.2, 5) }
    $codigo = 0
    try { Json '/api/acesso/rosto/entrar' @{ descritor = $outro; foto = $foto } | Out-Null } catch { $codigo = [int]$_.Exception.Response.StatusCode }
    if ($codigo -ne 401) { throw "rosto de outra pessoa deveria ser recusado (veio $codigo)" }
    "reconheceu, guardou a foto no banco ($($img.Content.Length) bytes) e recusou outro rosto"
  } finally { $script:token = $tokenAntes }
}

Teste 'Consulta de codigo que ja existe (BLOQUEAR)' {
  $r = Json '/api/consultas' @{ descricao = 'ELETRICISTA HORA EXTRA 100%'; unidade = 'hora' }
  if ($r.resultado -ne 'BLOQUEAR') { throw "veio $($r.resultado)" }
  Texto $r.motivo
}

Teste 'Consulta de servico novo (LIBERAR)' {
  $r = Json '/api/consultas' @{ descricao = 'TREINAMENTO EM REALIDADE VIRTUAL'; unidade = 'SV' }
  if ($r.resultado -ne 'LIBERAR') { throw "veio $($r.resultado)" }
  'nenhum codigo igual'
}

Teste 'Correcao de fala ou digitacao (SOLDA DOR)' {
  $r = Json '/api/consultas' @{ descricao = 'SOLDA DOR HORA EXTRA 50%'; unidade = 'H' }
  if (-not $r.sugestao) { throw 'nao sugeriu correcao' }
  "sugeriu: $($r.sugestao.descricao)"
}

Teste 'Erro em portugues para unidade errada' {
  $corpo = Join-Path $env:TEMP 'mokka_unidade.json'
  [IO.File]::WriteAllText($corpo, '{"descricao":"PEDREIRO HN","unidade":"caixa"}')
  $txt = & curl.exe -s -H "Content-Type: application/json" -H "Authorization: Bearer $script:token" --data-binary "@$corpo" ($site + '/api/consultas')
  $msg = ($txt | ConvertFrom-Json).erro
  if ($msg -notmatch 'Unidade') { throw "resposta: $txt" }
  $msg
}

Teste 'Curador junta pedidos iguais' {
  $ids = @()
  foreach ($d in @('PEDREIRO HORA NORMAL', 'pedreiro hora normal', 'PEDREIRO - HORA NORMAL')) {
    $c = Json '/api/consultas' @{ descricao = $d; unidade = 'H' }
    if ($c.resultado -ne 'BLOQUEAR') { continue }
    $s = Json "/api/solicitacoes/$($c.solicitacao_id)/decisao" @{ acao = 'CRIAR_NOVO'; justificativa = 'teste automatico' }
    $ids += $s.id
  }
  if ($ids.Count -lt 2) { throw 'a base nao tem PEDREIRO HORA NORMAL para testar' }
  $g = @(Invoke-RestMethod ($site + '/api/solicitacoes/curador')) | Where-Object { $_.ids -contains $ids[0] }
  if (@($g.ids).Count -ne $ids.Count) { throw "esperava $($ids.Count) juntos, veio $(@($g.ids).Count)" }
  $r = Json '/api/solicitacoes/curador/grupo' @{ ids = $ids; aprovar = $false }
  "$($ids.Count) pedidos escritos diferente viraram 1 grupo e foram decididos de uma vez"
}

Teste 'Planilha em lote (modelo com 14 linhas)' {
  $modelo = Join-Path $raiz 'front\modelo_lote.xlsx'
  if (-not (Test-Path $modelo)) { $modelo = Join-Path $raiz 'codigos-api\src\main\resources\static\modelo_lote.xlsx' }
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
  $py = Join-Path $raiz 'saneamento\.venv\Scripts\python.exe'
  Push-Location (Join-Path $raiz 'saneamento')
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
