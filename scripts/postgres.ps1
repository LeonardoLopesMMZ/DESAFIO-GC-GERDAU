param([ValidateSet('ligar', 'parar')][string]$Acao = 'ligar')

$ErrorActionPreference = 'Continue'
$Raiz = Split-Path -Parent $PSScriptRoot
$Ferr = Join-Path $Raiz '.ferramentas'
$Bin = Join-Path $Ferr 'pgsql\bin'
$Dados = Join-Path $Ferr 'pgdata'
$Porta = 5433
$Versao = '16.8-1'
$SenhaAdmin = Join-Path $Ferr 'pg-admin.txt'
$SenhaApp = Join-Path $Ferr 'pg-app.txt'
$Log = Join-Path $Ferr 'postgres.log'

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

function Pronto { & (Exe 'pg_isready') -h 127.0.0.1 -p $Porta -q; return $LASTEXITCODE -eq 0 }

if ($Acao -eq 'parar') {
  if ((Test-Path (Exe 'pg_ctl')) -and (Test-Path (Join-Path $Dados 'PG_VERSION'))) {
    & (Exe 'pg_ctl') -D $Dados -m fast stop 2>&1 | Out-Null
  }
  exit 0
}

try {
  New-Item -ItemType Directory -Force -ErrorAction Stop $Ferr | Out-Null
  if (-not (Test-Path (Exe 'pg_ctl'))) {
    Write-Host '   Primeira vez: baixando o PostgreSQL 16, uns 300 MB, so desta vez...'
    $zip = Join-Path $Ferr 'pg.zip'
    & curl.exe -fL --retry 3 -o $zip "https://get.enterprisedb.com/postgresql/postgresql-$Versao-windows-x64-binaries.zip"
    if ($LASTEXITCODE -ne 0) { throw 'nao consegui baixar' }
    Write-Host '   Descompactando o PostgreSQL...'
    & tar.exe -xf $zip -C $Ferr pgsql/bin pgsql/lib pgsql/share
    if ($LASTEXITCODE -ne 0) { throw 'o arquivo baixado veio com defeito' }
    Remove-Item -ErrorAction SilentlyContinue $zip
  }
  if (-not (Test-Path (Join-Path $Dados 'PG_VERSION'))) {
    if (Test-Path $Dados) { Rename-Item -ErrorAction Stop $Dados ("pgdata-incompleto-" + (Get-Date -Format 'yyyyMMddHHmmss')) }
    Gravar $SenhaAdmin (Aleatorio)
    Write-Host '   Criando o banco PostgreSQL do MOKKA...'
    & (Exe 'initdb') -D $Dados -U mokka_admin "--pwfile=$SenhaAdmin" -A scram-sha-256 -E UTF8 --locale=C *> (Join-Path $Ferr 'postgres-init.log')
    if ($LASTEXITCODE -ne 0) { throw 'initdb falhou, veja .ferramentas\postgres-init.log' }
  }
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
