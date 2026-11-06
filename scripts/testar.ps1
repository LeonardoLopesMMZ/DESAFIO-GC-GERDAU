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
$script:admin = ''

function Json([string]$caminho, $corpo, [string]$token = $script:token) {
  $bytes = [Text.Encoding]::UTF8.GetBytes(($corpo | ConvertTo-Json -Depth 10))
  Invoke-RestMethod -Uri ($site + $caminho) -Method Post -Body $bytes -ContentType 'application/json; charset=utf-8' -Headers @{ Authorization = "Bearer $token" }
}

function Ler([string]$caminho, [string]$token = $script:admin) {
  Invoke-RestMethod -Uri ($site + $caminho) -Headers @{ Authorization = "Bearer $token" }
}

function Status([string]$metodo, [string]$url, [hashtable]$cabecalhos = @{}, [string]$corpo = $null) {
  try {
    $p = @{ Uri = $url; Method = $metodo; Headers = $cabecalhos; UseBasicParsing = $true }
    if ($corpo) { $p.Body = [Text.Encoding]::UTF8.GetBytes($corpo); $p.ContentType = 'application/json; charset=utf-8' }
    return [int](Invoke-WebRequest @p).StatusCode
  } catch {
    if ($_.Exception.Response) { return [int]$_.Exception.Response.StatusCode }
    throw
  }
}

$arqSegredo = Join-Path $raiz '.ferramentas\segredo.txt'
$segredo = if (Test-Path $arqSegredo) { (Get-Content -Raw $arqSegredo).Trim() } else { '' }
if ($segredo) { $env:MOKKA_SEGREDO = $segredo }

Write-Host ""
Write-Host "  MOKKA - TESTE GERAL" -ForegroundColor Cyan
Write-Host "  -------------------" -ForegroundColor Cyan
Write-Host "  Esperando o sistema ligar (pode levar ate 2 minutos na primeira vez)..."
$pronto = $false
for ($i = 0; $i -lt 90; $i++) {
  try {
    Invoke-RestMethod 'http://127.0.0.1:8000/saude' | Out-Null
    Invoke-RestMethod ($site + '/api/sistema') | Out-Null
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

Teste 'Entrada do teste automatico (administrador)' {
  if (-not $segredo) { throw 'falta .ferramentas\segredo.txt; ligue pelo MOKKA.bat' }
  $r = Invoke-RestMethod -Uri ($site + '/api/acesso/entrar-local') -Method Post -Headers @{ 'X-Mokka-Segredo' = $segredo }
  if ($r.perfil -ne 'ADMIN') { throw "veio perfil $($r.perfil)" }
  $script:admin = $r.token
  'entrou como ADMIN so neste computador, com o segredo gerado ao ligar'
}

Teste 'Banco em uso' {
  $r = Invoke-RestMethod ($site + '/api/sistema')
  $preferido = if (Test-Path (Join-Path $raiz '.ferramentas\banco.txt')) { (Get-Content -Raw (Join-Path $raiz '.ferramentas\banco.txt')).Trim() } else { 'POSTGRES' }
  if ($preferido -eq 'POSTGRES' -and $r.banco -ne 'PostgreSQL') { throw 'escolhido PostgreSQL, mas ligou no banco de reserva H2; veja .ferramentas\postgres.log' }
  if ($r.persistente) { "$($r.banco): guarda tudo entre um dia e outro" } else { "$($r.banco): banco de reserva, na memoria" }
}

Teste 'Numeros do catalogo' {
  $r = Ler '/api/codigos/resumo'
  if ($r.na_base -le 0) { throw 'catalogo vazio' }
  "$($r.na_base) na base, $($r.oficiais) oficiais, $($r.repetidos) repetidos"
}

Teste 'Entrada com token (comprador)' {
  $r = Json '/api/acesso/entrar' @{ nome = 'Comprador de teste' }
  $script:token = $r.token
  if ($r.perfil -ne 'COMPRADOR') { throw "veio perfil $($r.perfil)" }
  "perfil COMPRADOR, token valido ate $($r.expira)"
}

Teste 'Seguranca: nada abre sem token' {
  $fechados = @('/api/codigos/resumo', '/api/codigos/leve', '/api/unidades', '/api/solicitacoes', '/api/raio-x', '/api/aprendizado', '/api/admin/pessoas')
  foreach ($c in $fechados) {
    $s = Status 'GET' ($site + $c)
    if ($s -ne 401) { throw "$c abriu sem token (status $s)" }
  }
  $s = Status 'POST' ($site + '/api/consultas') @{} '{"descricao":"PEDREIRO HN","unidade":"H"}'
  if ($s -ne 401) { throw "consulta sem token veio $s" }
  $s = Status 'GET' ($site + '/api/codigos/resumo') @{ Authorization = 'Bearer token-inventado' }
  if ($s -ne 401) { throw "token inventado veio $s" }
  "$($fechados.Count + 2) caminhos testados: todos pedem para entrar (401)"
}

Teste 'Seguranca: perfis (comprador nao decide)' {
  $h = @{ Authorization = "Bearer $script:token" }
  $a = Status 'GET' ($site + '/api/solicitacoes/curador') $h
  $b = Status 'POST' ($site + '/api/solicitacoes/curador/grupo') $h '{"ids":[1],"aprovar":true}'
  $c = Status 'GET' ($site + '/api/admin/auditoria') $h
  if ($a -ne 403 -or $b -ne 403 -or $c -ne 403) { throw "curador $a, decidir $b, auditoria $c (esperava 403)" }
  'comprador recebe 403 no curador e na administracao'
}

Teste 'Seguranca: cabecalhos de protecao' {
  $r = Invoke-WebRequest ($site + '/') -UseBasicParsing
  $falta = @('Content-Security-Policy', 'X-Content-Type-Options', 'X-Frame-Options', 'Referrer-Policy', 'Permissions-Policy') | Where-Object { -not $r.Headers[$_] }
  if ($falta) { throw "faltam: $($falta -join ', ')" }
  $api = Invoke-WebRequest ($site + '/api/sistema') -UseBasicParsing
  if ($api.Headers['Cache-Control'] -notmatch 'no-store') { throw 'a API deixa o navegador guardar respostas' }
  'CSP, nosniff, anti-iframe, sem referrer, camera e microfone so no proprio site, API sem cache'
}

Teste 'Seguranca: link do grupo (Tailscale)' {
  $h = @{ 'Tailscale-User-Login' = 'teste@exemplo.com' }
  $l = (Invoke-RestMethod ($site + '/api/sistema')).local
  $t = (Invoke-RestMethod ($site + '/api/sistema') -Headers $h).local
  if ($l -ne $true -or $t -ne $false) { throw "neste computador $l, pelo link $t" }
  $s = Status 'GET' ($site + '/swagger-ui/index.html') $h
  if ($s -ne 403) { throw "Swagger pelo link veio $s (esperava 403)" }
  'pelo link o MOKKA nao se trata como este computador e fecha o Swagger'
}

Teste 'Seguranca: tentativa de injecao' {
  $h = @{ Authorization = "Bearer $script:token" }
  $antes = (Ler '/api/codigos/resumo').na_base
  $ataques = @("' OR '1'='1", "'; DROP TABLE codigo_servico; --", '<script>alert(1)</script>', '../../etc/passwd', '%00%27')
  foreach ($x in $ataques) {
    $s = Status 'GET' ($site + '/api/codigos?busca=' + [uri]::EscapeDataString($x)) $h
    if ($s -ge 500) { throw "busca com $x deu erro $s" }
    $s = Status 'POST' ($site + '/api/consultas/palavras') $h (@{ texto = $x } | ConvertTo-Json)
    if ($s -ge 500) { throw "texto com $x deu erro $s" }
  }
  $s = Status 'POST' ($site + '/api/consultas') $h '{"descricao": [1,2], "unidade": {"x": 1}}'
  if ($s -ne 400) { throw "JSON torto veio $s" }
  $depois = (Ler '/api/codigos/resumo').na_base
  if ($depois -ne $antes) { throw "o catalogo mudou: $antes -> $depois" }
  "$($ataques.Count) ataques e JSON torto: nenhum erro 500, catalogo intacto ($depois codigos)"
}

Teste 'Seguranca: Python so aceita o MOKKA' {
  $s = Status 'POST' 'http://127.0.0.1:8000/similares' @{} '{"descricao":"PEDREIRO HN","unidade":"H"}'
  if ($s -ne 403) { throw "sem o segredo o Python respondeu $s" }
  $s = Status 'POST' 'http://127.0.0.1:8000/similares' @{ 'X-Mokka-Segredo' = $segredo } '{"descricao":"PEDREIRO HN","unidade":"H"}'
  if ($s -ne 200) { throw "com o segredo veio $s" }
  'sem o segredo: 403; com o segredo: 200; e so ouve em 127.0.0.1'
}

Teste 'Seguranca: forca bruta na entrada' {
  $vezes = 0
  $s = 0
  while ($vezes -lt 12) {
    $vezes++
    $s = Status 'POST' ($site + '/api/acesso/entrar-local') @{ 'X-Mokka-Segredo' = 'chute' + $vezes }
    if ($s -eq 429) { break }
  }
  if ($s -ne 429) { throw "depois de $vezes tentativas erradas ainda deixava tentar" }
  $s = Status 'POST' ($site + '/api/acesso/entrar') @{} '{"nome":"Depois do bloqueio"}'
  if ($s -ne 429) { throw "bloqueado, mas a entrada pelo nome veio $s" }
  Invoke-RestMethod -Uri ($site + '/api/admin/desbloquear') -Method Post -Headers @{ Authorization = "Bearer $script:admin" } | Out-Null
  $s = Status 'POST' ($site + '/api/acesso/entrar') @{} '{"nome":"Depois do bloqueio"}'
  if ($s -ne 200) { throw "desbloqueado, mas veio $s" }
  "bloqueou na tentativa $vezes (429) e o administrador desbloqueou"
}

Teste 'Entrada pelo rosto (servidor)' {
  $tokenAntes = $script:token
  try {
    $nomeRosto = 'Teste rosto ' + (Get-Random)
    $script:token = (Json '/api/acesso/entrar' @{ nome = $nomeRosto }).token
    $rnd = New-Object Random
    $d = 1..128 | ForEach-Object { [math]::Round($rnd.NextDouble() * 0.4 - 0.2, 5) }
    $foto = '/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAA0JCgsKCA0LCgsODg0PEyAVExISEyccHhcgLikxMC4pLSwzOko+MzZGNywtQFdBRkxOUlNSMj5aYVpQYEpRUk//2wBDAQ4ODhMREyYVFSZPNS01T09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT0//wAARCAAQABADASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwDiqKKK9M84/9k='
    $script:fotoTeste = $foto
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
    $alheia = Status 'GET' ($site + '/api/acesso/retratos/' + $r.retrato_id) @{ Authorization = "Bearer $tokenAntes" }
    if ($alheia -ne 403) { throw "outro comprador viu a foto (status $alheia)" }
    $nome = Status 'POST' ($site + '/api/acesso/entrar') @{} (@{ nome = $nomeRosto } | ConvertTo-Json)
    if ($nome -ne 403) { throw "com rosto cadastrado, a entrada pelo nome deveria ser recusada (veio $nome)" }
    $tam = $img.Content.Length
    $apagou = Invoke-RestMethod -Uri ($site + '/api/acesso/rosto') -Method Delete -Headers @{ Authorization = "Bearer $($r.token)" }
    if ($apagou.tem_rosto) { throw 'o rosto nao foi apagado' }
    $depois = Status 'GET' ($site + '/api/acesso/retratos/' + $r.retrato_id) @{ Authorization = "Bearer $($r.token)" }
    if ($depois -ne 404) { throw "a foto continuou no banco (status $depois)" }
    "reconheceu, guardou a foto criptografada ($tam bytes), recusou outro rosto, escondeu a foto de outros e apagou tudo a pedido (LGPD)"
  } finally { $script:token = $tokenAntes }
}

Teste 'Foto do perfil fixa' {
  $tokenAntes = $script:token
  try {
    $nome = 'Teste perfil ' + (Get-Random)
    $script:token = (Json '/api/acesso/entrar' @{ nome = $nome }).token
    $r = Json '/api/acesso/foto-perfil' @{ foto = $script:fotoTeste }
    if (-not $r.foto_perfil_id) { throw 'a foto do perfil nao ficou na conta' }
    $eu = Ler '/api/acesso/eu' $script:token
    if ($eu.foto_perfil_id -ne $r.foto_perfil_id) { throw 'a foto nao ficou guardada na conta' }
    $sem = Invoke-RestMethod -Uri ($site + '/api/acesso/foto-perfil') -Method Delete -Headers @{ Authorization = "Bearer $script:token" }
    if ($sem.foto_perfil_id) { throw 'a foto nao saiu' }
    "trocou, ficou guardada na conta (foto $($r.foto_perfil_id)) e saiu a pedido"
  } finally { $script:token = $tokenAntes }
}

Teste 'Entrada sem camera (curador libera)' {
  $nome = 'Teste sem camera ' + (Get-Random)
  $t = (Json '/api/acesso/entrar' @{ nome = $nome } '').token
  $d = 1..128 | ForEach-Object { [math]::Round((Get-Random -Minimum -20000 -Maximum 20000) / 100000, 5) }
  Json '/api/acesso/rosto/cadastrar' @{ descritor = $d; foto = $script:fotoTeste } $t | Out-Null
  $p = Json '/api/acesso/pedir-entrada' @{ nome = $nome; observacao = 'teste automatico sem camera' } ''
  $url = $site + "/api/acesso/pedir-entrada/$($p.id)?codigo=$($p.codigo)"
  $antes = Invoke-RestMethod $url
  if ($antes.status -ne 'PENDENTE' -or $antes.entrada) { throw "antes de liberar veio $($antes.status)" }
  $viu = Status 'GET' ($site + '/api/acesso/pedidos-de-entrada') @{ Authorization = "Bearer $script:token" }
  if ($viu -ne 403) { throw "um comprador viu os pedidos (status $viu)" }
  Json "/api/acesso/pedidos-de-entrada/$($p.id)/decisao" @{ aceitar = $true } $script:admin | Out-Null
  $depois = Invoke-RestMethod $url
  if ($depois.status -ne 'APROVADO' -or -not $depois.entrada.token) { throw "depois de liberar veio $($depois.status)" }
  if ($depois.entrada.metodo -ne 'APROVADO') { throw "entrou como $($depois.entrada.metodo)" }
  $denovo = Invoke-RestMethod $url
  if ($denovo.entrada) { throw 'o mesmo pedido deu entrada duas vezes' }
  $aud = Ler '/api/admin/auditoria' | ForEach-Object { $_ } | Where-Object { $_.acao -eq 'APROVOU_ENTRADA' -and $_.alvo -eq $nome }
  if (-not $aud) { throw 'a liberacao nao ficou na auditoria' }
  "pediu com observacao -> curador liberou -> entrou uma vez so; liberado por $($depois.decidido_por), na auditoria"
}

Teste 'Consulta de codigo que ja existe (BLOQUEAR)' {
  $r = Json '/api/consultas' @{ descricao = 'ELETRICISTA HORA EXTRA 100%'; unidade = 'hora' }
  if ($r.resultado -ne 'BLOQUEAR') { throw "veio $($r.resultado)" }
  Texto $r.motivo
}

Teste 'Consulta de servico novo (LIBERAR)' {
  $turma = Get-Random -Minimum 1000 -Maximum 9999
  $r = Json '/api/consultas' @{ descricao = "TREINAMENTO REALIDADE VIRTUAL TURMA $turma"; unidade = 'SV' }
  if ($r.resultado -ne 'LIBERAR') { throw "veio $($r.resultado): $(Texto $r.motivo)" }
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
  $g = Ler '/api/solicitacoes/curador' | ForEach-Object { $_ } | Where-Object { @($_.ids) -contains $ids[0] } | Select-Object -First 1
  $fora = @($ids | Where-Object { @($g.ids) -notcontains $_ })
  if ($fora.Count) { throw "esperava os $($ids.Count) juntos, ficaram separados: $($fora -join ', ')" }
  if (-not $g.descricao_existente) { throw 'o curador nao recebeu a descricao do codigo que ja existe' }
  $script:grupo = $ids
  "$($ids.Count) pedidos escritos diferente viraram 1 grupo; sugestao: $($g.sugestao.acao) com $($g.sugestao.certeza)% de certeza"
}

Teste 'Curador decide, o MOKKA aprende e da para desfazer' {
  if (-not $script:grupo) { throw 'o teste anterior nao montou o grupo' }
  $r = Json '/api/solicitacoes/curador/grupo' @{ ids = $script:grupo; aprovar = $false } $script:admin
  $codigo = $r[0].codigo_escolhido
  $c = Json '/api/consultas' @{ descricao = 'NORMAL HORA PEDREIRO'; unidade = 'H' }
  if ((Texto $c.motivo) -notmatch 'aprendeu') { throw "a consulta seguinte nao usou o que aprendeu: $(Texto $c.motivo)" }
  $painel = Ler '/api/aprendizado'
  $regra = @($painel.regras) | Where-Object { $_.codigo -eq $codigo -and $_.tipo -eq 'IGUAL' } | Select-Object -First 1
  if (-not $regra) { throw 'a regra nao apareceu em O que o MOKKA aprendeu' }
  $d = Json '/api/solicitacoes/curador/desfazer' @{ ids = $script:grupo } $script:admin
  if (@($d | Where-Object { $_.status -ne 'AGUARDANDO_CURADOR' }).Count) { throw 'nao voltou para o curador' }
  Json '/api/solicitacoes/curador/grupo' @{ ids = $script:grupo; aprovar = $false } $script:admin | Out-Null
  $aud = @(Ler '/api/admin/auditoria') | Where-Object { $_.acao -eq 'DESFEZ' }
  if (-not $aud) { throw 'o desfazer nao ficou na auditoria' }
  $resumo = Ler '/api/solicitacoes/curador/resumo'
  if ($resumo.recusados_hoje -lt 1 -or $resumo.regras_aprendidas -lt 1) { throw "o resumo do curador nao contou a decisao" }
  "recusou -> proxima consulta ja diz o codigo $codigo ($($regra.votos) votos) -> desfez -> decidiu de novo; tudo na auditoria"
}

Teste 'Palavra ensinada so vale depois do curador' {
  Json '/api/aprendizado/palavras' @{ de = 'PEDRERO'; para = 'PEDREIRO' } | Out-Null
  Json '/api/aprendizado/palavras/PEDRERO/decisao' @{ aceitar = $true } $script:admin | Out-Null
  $depois = Json '/api/consultas/palavras' @{ texto = 'PEDRERO HORA NORMAL' }
  $sug = @($depois.palavras | Where-Object { $_.indice -eq 0 } | Select-Object -First 1).sugestoes
  if (@($sug)[0] -ne 'PEDREIRO') { throw "depois de aceita, a sugestao veio: $($sug -join ', ')" }
  'PEDRERO -> PEDREIRO: sugerida pelo comprador, aceita pelo curador, agora aparece primeiro'
}

Teste 'Planilha em lote (modelo com 14 linhas)' {
  $modelo = Join-Path $raiz 'front\modelo_lote.xlsx'
  if (-not (Test-Path $modelo)) { $modelo = Join-Path $raiz 'codigos-api\src\main\resources\static\modelo_lote.xlsx' }
  $txt = & curl.exe -s -H "Authorization: Bearer $script:token" -F "arquivo=@$modelo" ($site + '/api/lotes/analisar')
  $r = $txt | ConvertFrom-Json
  if ($r.resumo.TOTAL -ne 14) { throw "veio $($r.resumo.TOTAL) linhas" }
  $script:lote = $r
  "$($r.resumo.BLOQUEAR) ja existem, $($r.resumo.REVISAR) parecidos, $($r.resumo.LIBERAR) novos, $($r.resumo.REPETIDO) repetidos, $($r.resumo.ERRO) com erro, em $($r.milissegundos) ms"
}

Teste 'Lote em JSON e em texto' {
  $sem = New-Object Text.UTF8Encoding $false
  $json = Join-Path $env:TEMP 'mokka_lote.json'
  $txt = Join-Path $env:TEMP 'mokka_lote.txt'
  [IO.File]::WriteAllText($json, '{"itens":[{"descricao":"PEDREIRO HORA NORMAL","unidade":"H"},{"descricao":"PINTOR LETREIRO","unidade":"H"},{"descricao":"SOLDADOR","unidade":"H"}]}', $sem)
  [IO.File]::WriteAllText($txt, "PEDREIRO HORA NORMAL;H`nPINTOR LETREIRO;H", $sem)
  $a = (& curl.exe -s -H "Authorization: Bearer $script:token" -F "arquivo=@$json" ($site + '/api/lotes/analisar')) | ConvertFrom-Json
  $b = (& curl.exe -s -H "Authorization: Bearer $script:token" -F "arquivo=@$txt" ($site + '/api/lotes/analisar')) | ConvertFrom-Json
  Remove-Item $json, $txt
  if ($a.resumo.TOTAL -ne 3) { throw "o JSON veio com $($a.resumo.TOTAL) linhas" }
  if ($b.resumo.TOTAL -ne 2) { throw "o texto veio com $($b.resumo.TOTAL) linhas" }
  'JSON com 3 servicos e texto com 2 linhas lidos certo'
}

Teste 'Excel organizado (exportar)' {
  $arq = Join-Path $env:TEMP 'mokka_teste.xlsx'
  $bytes = [Text.Encoding]::UTF8.GetBytes((@{ linhas = $script:lote.linhas } | ConvertTo-Json -Depth 10))
  Invoke-WebRequest -Uri ($site + '/api/lotes/exportar') -Method Post -Body $bytes -ContentType 'application/json; charset=utf-8' -OutFile $arq -UseBasicParsing -Headers @{ Authorization = "Bearer $script:token" }
  $tam = (Get-Item $arq).Length
  if ($tam -lt 1000) { throw 'arquivo vazio' }
  "$([math]::Round($tam / 1KB)) KB"
}

Teste 'Modo reserva (Python desligado)' {
  $p = Get-NetTCPConnection -LocalPort 8000 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
  if (-not $p) { throw 'o Python nao estava ligado' }
  Stop-Process -Id $p.OwningProcess -Force
  Start-Sleep -Seconds 1
  try {
    $r = Json '/api/consultas' @{ descricao = 'MECANICO MANUTENCAO HORA NORMAL'; unidade = 'hora' }
    if ((Texto $r.motivo) -notmatch 'modo reserva') { throw "veio: $(Texto $r.motivo)" }
    "Python desligado e a consulta respondeu pelo banco ($($r.resultado))"
  } finally {
    $base = if (Test-Path (Join-Path $raiz 'saneamento\dados\base_codigos_servico.xlsx')) { 'dados\base_codigos_servico.xlsx' } else { 'exemplo\base_exemplo.csv' }
    Start-Process -FilePath (Join-Path $raiz 'saneamento\.venv\Scripts\python.exe') -ArgumentList 'api.py', $base -WorkingDirectory (Join-Path $raiz 'saneamento') -WindowStyle Minimized
    for ($i = 0; $i -lt 60; $i++) { try { Invoke-RestMethod 'http://127.0.0.1:8000/saude' | Out-Null; break } catch { Start-Sleep -Seconds 2 } }
  }
}

Teste 'Raio-X do catalogo' {
  $r = Ler '/api/raio-x'
  if ($r.nota_depois -lt $r.nota_antes) { throw "a nota piorou: $($r.nota_antes) -> $($r.nota_depois)" }
  "nota $($r.nota_antes) -> $($r.nota_depois), $($r.familias.Count) familias de duplicados"
}

Teste 'Tela inicial' {
  $r = Invoke-WebRequest ($site + '/inicio.html') -UseBasicParsing
  if ($r.StatusCode -ne 200 -or $r.Content -notmatch 'data-demo="consulta"') { throw "status $($r.StatusCode)" }
  'abre sem entrar, com as demonstracoes e o tour'
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

Teste 'Bibliotecas do Python sem falha conhecida' {
  $py = Join-Path $raiz 'saneamento\.venv\Scripts\python.exe'
  $antes = $ErrorActionPreference
  $ErrorActionPreference = 'Continue'
  try {
    & $py -m pip_audit --version *> $null
    if ($LASTEXITCODE -ne 0) { & $py -m pip install -q --disable-pip-version-check pip-audit *> $null }
    if ($LASTEXITCODE -ne 0) { return 'sem internet para baixar o verificador; pulado' }
    $saida = & $py -m pip_audit --progress-spinner off 2>&1 | ForEach-Object { "$_" }
    if ($LASTEXITCODE -ne 0) {
      if (($saida -join ' ') -match 'Found \d+ known vulnerabilit') { throw (($saida | Select-Object -First 6) -join ' | ') }
      return 'sem internet para consultar a base de falhas; pulado'
    }
    'nenhuma falha conhecida nas bibliotecas instaladas'
  } finally { $ErrorActionPreference = $antes }
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
