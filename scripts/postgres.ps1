param([ValidateSet('ligar', 'parar')][string]$Acao = 'ligar')

$ErrorActionPreference = 'Continue'
$Raiz = Split-Path -Parent $PSScriptRoot
$Ferr = Join-Path $Raiz '.ferramentas'
$Pg = $Ferr
if (-not (Test-Path (Join-Path $Ferr 'pgdata\PG_VERSION')) -and ($Raiz -match '[^\x00-\x7F]' -or $Raiz -match 'OneDrive')) {
  $md5 = [Security.Cryptography.MD5]::Create().ComputeHash([Text.Encoding]::UTF8.GetBytes($Raiz.ToLower()))
  $Pg = Join-Path $env:LOCALAPPDATA ('MOKKA-postgres\' + (-join ($md5[0..3] | ForEach-Object { $_.ToString('x2') })))
}
$Bin = Join-Path $Pg 'pgsql\bin'
$Dados = Join-Path $Pg 'pgdata'
$Porta = 5433
$Versao = '16.8-1'
$SenhaAdmin = Join-Path $Ferr 'pg-admin.txt'
$SenhaApp = Join-Path $Ferr 'pg-app.txt'
$Log = Join-Path $Pg 'postgres.log'

function Exe($nome) { Join-Path $Bin "$nome.exe" }

function Aleatorio {
  $b = New-Object byte[] 24
  [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b)
  -join ($b | ForEach-Object { $_.ToString('x2') })
}

function Gravar($arquivo, $texto) {
  [IO.File]::WriteAllText($arquivo, $texto, (New-Object Text.UTF8Encoding $false))
}

function Sql($banco, $comando) {
  $env:PGPASSWORD = (Get-Content -Raw -ErrorAction Stop $SenhaAdmin).Trim()
  try {
    $r = & (Exe 'psql') -h 127.0.0.1 -p $Porta -U mokka_admin -d $banco -v ON_ERROR_STOP=1 -qtA -c $comando 2>&1
    if ($LASTEXITCODE -ne 0) { throw "psql: $r" }
    return ($r | Out-String).Trim()
  } finally { $env:PGPASSWORD = $null }
}

function Baixar($url, $destino) {
  Remove-Item -ErrorAction SilentlyContinue $destino
  & curl.exe -fL --retry 3 --connect-timeout 20 -o $destino $url 2>$null
  if ($LASTEXITCODE -eq 0 -and (Test-Path $destino) -and (Get-Item $destino).Length -gt 1MB) { return $true }
  try {
    $ProgressPreference = 'SilentlyContinue'
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $destino -ErrorAction Stop
    if ((Get-Item $destino).Length -gt 1MB) { return $true }
  } catch {}
  try {
    Start-BitsTransfer -Source $url -Destination $destino -ErrorAction Stop
    if ((Get-Item $destino).Length -gt 1MB) { return $true }
  } catch {}
  return $false
}

function Runtime {
  $dlls = 'vcruntime140.dll', 'vcruntime140_1.dll', 'msvcp140.dll'
  $faltam = $dlls | Where-Object { -not (Test-Path (Join-Path $env:SystemRoot "System32\$_")) -and -not (Test-Path (Join-Path $Bin $_)) }
  if (-not $faltam) { return }
  $fontes = @()
  $j = Get-Command java -ErrorAction SilentlyContinue
  if ($j) { $fontes += Split-Path $j.Source }
  $fontes += Get-ChildItem -ErrorAction SilentlyContinue -Recurse -Filter vcruntime140.dll (Join-Path $Ferr 'jdk'), (Join-Path $Ferr 'python') | ForEach-Object { $_.DirectoryName }
  foreach ($d in $faltam) {
    foreach ($f in $fontes) { $x = Join-Path $f $d; if (Test-Path $x) { Copy-Item -Force $x $Bin -ErrorAction SilentlyContinue; break } }
  }
}

function Pronto { & (Exe 'pg_isready') -h 127.0.0.1 -p $Porta -q; return $LASTEXITCODE -eq 0 }

function PararOutro {
  $c = Get-NetTCPConnection -LocalPort $Porta -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
  if (-not $c) { return }
  $p = Get-CimInstance Win32_Process -Filter "ProcessId=$($c.OwningProcess)" -ErrorAction SilentlyContinue
  if (-not $p -or $p.Name -notmatch '^postgres') { return }
  $ctl = Join-Path (Split-Path $p.ExecutablePath) 'pg_ctl.exe'
  if ($p.CommandLine -match '-D\s+"([^"]+)"' -or $p.CommandLine -match '-D\s+(\S+)') { $outro = $Matches[1] }
  if ($outro -and (Test-Path $ctl)) { & $ctl -D $outro -m fast stop 2>&1 | Out-Null }
  for ($i = 0; $i -lt 15 -and (Get-NetTCPConnection -LocalPort $Porta -State Listen -ErrorAction SilentlyContinue); $i++) { Start-Sleep -Seconds 1 }
  if (Get-NetTCPConnection -LocalPort $Porta -State Listen -ErrorAction SilentlyContinue) { Stop-Process -Id $c.OwningProcess -Force -ErrorAction SilentlyContinue; Start-Sleep -Seconds 2 }
}

if ($Acao -eq 'parar') {
  if ((Test-Path (Exe 'pg_ctl')) -and (Test-Path (Join-Path $Dados 'PG_VERSION'))) {
    & (Exe 'pg_ctl') -D $Dados -m fast stop 2>&1 | Out-Null
  }
  PararOutro
  exit 0
}

try {
  New-Item -ItemType Directory -Force -ErrorAction Stop $Ferr | Out-Null
  New-Item -ItemType Directory -Force -ErrorAction Stop $Pg | Out-Null
  if (-not (Test-Path (Exe 'pg_ctl')) -and $Pg -ne $Ferr -and (Test-Path (Join-Path $Ferr 'pgsql\bin\pg_ctl.exe'))) {
    Write-Host '   Copiando o PostgreSQL para uma pasta sem acentos...'
    Copy-Item -Recurse -Force (Join-Path $Ferr 'pgsql') $Pg
  }
  if (-not (Test-Path (Exe 'pg_ctl'))) {
    $instalado = Get-ChildItem -ErrorAction SilentlyContinue "$env:ProgramFiles\PostgreSQL\*\bin\pg_ctl.exe" |
      Where-Object { [int](('0' + $_.Directory.Parent.Name) -replace '[^0-9].*$', '') -ge 12 } |
      Sort-Object { [int](('0' + $_.Directory.Parent.Name) -replace '[^0-9].*$', '') } -Descending | Select-Object -First 1
    if ($instalado) {
      Write-Host "   Usando o PostgreSQL ja instalado neste computador ($($instalado.Directory.Parent.Name))."
      $script:Bin = $instalado.DirectoryName
    }
  }
  if (-not (Test-Path (Exe 'pg_ctl'))) {
    Write-Host '   Primeira vez: baixando o PostgreSQL 16, so desta vez...'
    $zip = Join-Path $Pg 'pg.zip'
    if (Baixar "https://get.enterprisedb.com/postgresql/postgresql-$Versao-windows-x64-binaries.zip" $zip) {
      Write-Host '   Descompactando o PostgreSQL...'
      & tar.exe -xf $zip -C $Pg pgsql/bin pgsql/lib pgsql/share 2>$null
      if (-not (Test-Path (Exe 'pg_ctl'))) { try { Expand-Archive -LiteralPath $zip -DestinationPath $Pg -Force } catch {} }
    }
    Remove-Item -ErrorAction SilentlyContinue $zip
  }
  if (-not (Test-Path (Exe 'pg_ctl'))) {
    Write-Host '   Tentando outra fonte do PostgreSQL (GitHub, uns 45 MB)...'
    $zip = Join-Path $Pg 'pg2.zip'
    $nome = 'postgresql-16.8.0-x86_64-pc-windows-msvc'
    if (Baixar "https://github.com/theseus-rs/postgresql-binaries/releases/download/16.8.0/$nome.zip" $zip) {
      Remove-Item -Recurse -Force -ErrorAction SilentlyContinue (Join-Path $Pg $nome)
      & tar.exe -xf $zip -C $Pg 2>$null
      if (-not (Test-Path (Join-Path $Pg "$nome\bin\pg_ctl.exe"))) { try { Expand-Archive -LiteralPath $zip -DestinationPath $Pg -Force } catch {} }
      if (Test-Path (Join-Path $Pg "$nome\bin\pg_ctl.exe")) {
        Remove-Item -Recurse -Force -ErrorAction SilentlyContinue (Join-Path $Pg 'pgsql')
        Rename-Item (Join-Path $Pg $nome) 'pgsql'
      }
    }
    Remove-Item -ErrorAction SilentlyContinue $zip
  }
  if (-not (Test-Path (Exe 'pg_ctl'))) { throw 'nao consegui baixar (confira a internet)' }
  Runtime
  if (-not (Test-Path (Join-Path $Dados 'PG_VERSION'))) {
    if (Test-Path $Dados) { Rename-Item -ErrorAction Stop $Dados ("pgdata-incompleto-" + (Get-Date -Format 'yyyyMMddHHmmss')) }
    Gravar $SenhaAdmin (Aleatorio)
    Write-Host '   Criando o banco PostgreSQL do MOKKA...'
    $pw = Join-Path $Pg 'pw.tmp'
    Copy-Item -Force $SenhaAdmin $pw
    & (Exe 'initdb') -D $Dados -U mokka_admin "--pwfile=$pw" -A scram-sha-256 -E UTF8 --locale=C *> (Join-Path $Ferr 'postgres-init.log')
    $ok = $LASTEXITCODE -eq 0
    Remove-Item -Force -ErrorAction SilentlyContinue $pw
    if (-not $ok) {
      Get-Content -Tail 4 -ErrorAction SilentlyContinue (Join-Path $Ferr 'postgres-init.log') | ForEach-Object { Write-Host "      $_" }
      if (Test-Path $Dados) { Rename-Item -ErrorAction SilentlyContinue $Dados ("pgdata-falhou-" + (Get-Date -Format 'yyyyMMddHHmmss')) }
      throw 'initdb falhou, veja .ferramentas\postgres-init.log'
    }
  }
  if (-not (Test-Path (Join-Path $Dados 'postmaster.pid'))) { PararOutro }
  if (-not (Pronto)) {
    $opcoes = "-p $Porta -c listen_addresses=127.0.0.1"
    Start-Process -FilePath (Exe 'pg_ctl') -ArgumentList @('-D', "`"$Dados`"", '-l', "`"$Log`"", '-o', "`"$opcoes`"", 'start') -WindowStyle Hidden | Out-Null
    for ($i = 0; $i -lt 60 -and -not (Pronto); $i++) { Start-Sleep -Seconds 1 }
    if (-not (Pronto)) { throw 'o PostgreSQL nao respondeu, veja .ferramentas\postgres.log' }
  }
  $temPapel = (Sql 'postgres' "select count(*) from pg_roles where rolname = 'mokka_app'") -eq '1'
  if (-not $temPapel -or -not (Test-Path $SenhaApp)) {
    Gravar $SenhaApp (Aleatorio)
    $senha = (Get-Content -Raw -ErrorAction Stop $SenhaApp).Trim()
    if ($temPapel) { Sql 'postgres' "ALTER ROLE mokka_app PASSWORD '$senha'" | Out-Null }
    else { Sql 'postgres' "CREATE ROLE mokka_app LOGIN PASSWORD '$senha' NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION CONNECTION LIMIT 30" | Out-Null }
  }
  if ((Sql 'postgres' "select count(*) from pg_database where datname = 'mokka'") -ne '1') {
    Sql 'postgres' "CREATE DATABASE mokka OWNER mokka_app ENCODING 'UTF8'" | Out-Null
    Sql 'postgres' 'REVOKE ALL ON DATABASE mokka FROM PUBLIC' | Out-Null
    Sql 'postgres' 'REVOKE CONNECT ON DATABASE postgres FROM PUBLIC' | Out-Null
    Sql 'postgres' 'REVOKE CONNECT ON DATABASE template1 FROM PUBLIC' | Out-Null
    Sql 'mokka' 'REVOKE ALL ON SCHEMA public FROM PUBLIC' | Out-Null
    Sql 'mokka' 'ALTER SCHEMA public OWNER TO mokka_app' | Out-Null
  }
  $senha = (Get-Content -Raw -ErrorAction Stop $SenhaApp).Trim()
  Gravar (Join-Path $Ferr 'pg-env.bat') "set `"DB_URL=jdbc:postgresql://127.0.0.1:$Porta/mokka`"`r`nset `"DB_USER=mokka_app`"`r`nset `"DB_PASSWORD=$senha`"`r`n"
  exit 0
} catch {
  Write-Host "   PostgreSQL: $($_.Exception.Message)"
  exit 1
}
