#!/bin/bash
cd "$(dirname "$0")" || exit 1
RAIZ="$PWD"
FERR="$RAIZ/.ferramentas"
PYV="3.12.9+20250317"
SITE="http://localhost:8080"
PGV="16.8-1"
PGPORTA=5433
mkdir -p "$FERR"
case "$1" in h2|H2) echo H2 > "$FERR/banco.txt" ;; postgres|pg) echo POSTGRES > "$FERR/banco.txt" ;; esac
ZERAR=""
[ "$1" = zerar ] && ZERAR="--mokka.zerar=true"
echo ""
echo "  MOKKA | codigos de servico"
echo "  =========================="

falhou() { echo ""; echo "  $1"; echo "  Aperte Enter para fechar."; read -r _; exit 1; }

case "$(uname -s)" in
  Darwin) SO=mac; PSO=apple-darwin ;;
  Linux) SO=linux; PSO=unknown-linux-gnu ;;
  *) falhou "No Windows, use o MOKKA.bat." ;;
esac
case "$(uname -m)" in
  arm64|aarch64) ARQ=aarch64 ;;
  *) ARQ=x64 ;;
esac
PARQ=$([ "$ARQ" = x64 ] && echo x86_64 || echo aarch64)

baixar() { curl -fL --retry 3 --progress-bar -o "$2" "$1"; }

java_serve() { "$1" -version 2>&1 | grep -Eq 'version "(1[7-9]|[2-9][0-9])'; }
JAVA=""
if command -v java >/dev/null 2>&1 && java_serve java; then JAVA=$(command -v java); fi
if [ -z "$JAVA" ] && [ -d "$FERR/jdk" ]; then JAVA=$(find "$FERR/jdk" -path '*/bin/java' -type f | head -1); fi
if [ -z "$JAVA" ]; then
  echo "  Primeira vez: baixando o Java 17 (uns 190 MB, so desta vez)..."
  mkdir -p "$FERR" && rm -rf "$FERR/jdk" && mkdir "$FERR/jdk"
  baixar "https://api.adoptium.net/v3/binary/latest/17/ga/$SO/$ARQ/jdk/hotspot/normal/eclipse" "$FERR/jdk.tar.gz" || falhou "Nao consegui baixar o Java. Confira a internet."
  tar -xzf "$FERR/jdk.tar.gz" -C "$FERR/jdk" && rm -f "$FERR/jdk.tar.gz"
  JAVA=$(find "$FERR/jdk" -path '*/bin/java' -type f | head -1)
  [ -n "$JAVA" ] || falhou "O Java baixado veio com defeito. Apague a pasta .ferramentas e rode de novo."
fi
export PATH="$(dirname "$JAVA"):$PATH"
export JAVA_HOME="$(cd "$(dirname "$JAVA")/.." && pwd)"

PY=""
for c in python3.13 python3.12 python3.11 python3.10 python3; do
  p=$(command -v "$c" 2>/dev/null) || continue
  [ "$SO" = mac ] && [ "$p" = /usr/bin/python3 ] && continue
  if "$p" -c 'import sys, venv; sys.exit(0 if (3, 10) <= sys.version_info[:2] < (3, 14) else 1)' 2>/dev/null; then PY="$p"; break; fi
done
if [ ! -x saneamento/.venv/bin/python ]; then
  if [ -z "$PY" ]; then
    PY="$FERR/python/bin/python3"
    if [ ! -x "$PY" ]; then
      echo "  Primeira vez: baixando o Python 3.12 (uns 25 MB, so desta vez)..."
      mkdir -p "$FERR"
      baixar "https://github.com/astral-sh/python-build-standalone/releases/download/${PYV#*+}/cpython-$PYV-$PARQ-$PSO-install_only.tar.gz" "$FERR/python.tar.gz" || falhou "Nao consegui baixar o Python. Confira a internet."
      tar -xzf "$FERR/python.tar.gz" -C "$FERR" && rm -f "$FERR/python.tar.gz"
    fi
  fi
  echo "  Primeira vez: preparando o Python (precisa de internet, leva uns 2 minutos)..."
  "$PY" -m venv saneamento/.venv && saneamento/.venv/bin/python -m pip install -q --disable-pip-version-check pandas numpy scikit-learn openpyxl \
    || { rm -rf saneamento/.venv; falhou "Falhou a preparacao do Python. Confira a internet e rode de novo."; }
fi

aleatorio() { od -An -N24 -tx1 /dev/urandom | tr -d ' \n'; }

achar_pg() {
  PG_BIN=""
  for d in "$FERR/pgsql/bin" "$(dirname "$(command -v pg_ctl 2>/dev/null)" 2>/dev/null)" /Applications/Postgres.app/Contents/Versions/latest/bin \
           /opt/homebrew/opt/postgresql@16/bin /usr/local/opt/postgresql@16/bin /usr/lib/postgresql/*/bin; do
    if [ -x "$d/pg_ctl" ] && [ -x "$d/initdb" ] && [ -x "$d/psql" ]; then PG_BIN="$d"; return 0; fi
  done
  return 1
}

baixar_pg() {
  [ "$SO" = mac ] || return 1
  echo "  Primeira vez: baixando o PostgreSQL (uns 300 MB, so desta vez)..."
  baixar "https://get.enterprisedb.com/postgresql/postgresql-$PGV-osx-binaries.zip" "$FERR/pg.zip" || { rm -f "$FERR/pg.zip"; return 1; }
  unzip -q -o "$FERR/pg.zip" 'pgsql/bin/*' 'pgsql/lib/*' 'pgsql/share/*' -d "$FERR" || return 1
  rm -f "$FERR/pg.zip"
}

pg() { PGPASSWORD="$(cat "$FERR/pg-admin.txt")" "$PG_BIN/psql" -h 127.0.0.1 -p $PGPORTA -U mokka_admin -v ON_ERROR_STOP=1 -qtA "$@"; }

ligar_postgres() {
  achar_pg || { baixar_pg && achar_pg; } || return 1
  PGDADOS="$FERR/pgdata"
  mkdir -p "$FERR"
  if [ ! -f "$PGDADOS/PG_VERSION" ]; then
    rm -rf "$PGDADOS"
    (umask 077; aleatorio > "$FERR/pg-admin.txt")
    "$PG_BIN/initdb" -D "$PGDADOS" -U mokka_admin --pwfile="$FERR/pg-admin.txt" -A scram-sha-256 -E UTF8 --locale=C >"$FERR/postgres-init.log" 2>&1 || return 1
  fi
  "$PG_BIN/pg_ctl" -D "$PGDADOS" status >/dev/null 2>&1 || \
    "$PG_BIN/pg_ctl" -D "$PGDADOS" -l "$FERR/postgres.log" -w -t 60 \
      -o "-p $PGPORTA -c listen_addresses=127.0.0.1 -c unix_socket_directories=" start >/dev/null 2>&1 || return 1
  if [ ! -s "$FERR/pg-app.txt" ] || [ "$(pg -d postgres -c "select 1 from pg_roles where rolname = 'mokka_app'")" != 1 ]; then
    (umask 077; aleatorio > "$FERR/pg-app.txt")
    SENHA=$(cat "$FERR/pg-app.txt")
    if [ "$(pg -d postgres -c "select 1 from pg_roles where rolname = 'mokka_app'")" = 1 ]; then
      pg -d postgres -c "ALTER ROLE mokka_app PASSWORD '$SENHA'" || return 1
    else
      pg -d postgres -c "CREATE ROLE mokka_app LOGIN PASSWORD '$SENHA' NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION CONNECTION LIMIT 30" || return 1
    fi
  fi
  if [ "$(pg -d postgres -c "select 1 from pg_database where datname = 'mokka'")" != 1 ]; then
    pg -d postgres -c "CREATE DATABASE mokka OWNER mokka_app ENCODING 'UTF8'" || return 1
    pg -d postgres -c "REVOKE ALL ON DATABASE mokka FROM PUBLIC" -c "REVOKE CONNECT ON DATABASE postgres FROM PUBLIC" -c "REVOKE CONNECT ON DATABASE template1 FROM PUBLIC" || return 1
    pg -d mokka -c "REVOKE ALL ON SCHEMA public FROM PUBLIC" -c "ALTER SCHEMA public OWNER TO mokka_app" || return 1
  fi
  export DB_URL="jdbc:postgresql://127.0.0.1:$PGPORTA/mokka" DB_USER=mokka_app DB_PASSWORD="$(cat "$FERR/pg-app.txt")"
}

parar_postgres() { [ -n "$PG_BIN" ] && [ -f "$FERR/pgdata/PG_VERSION" ] && "$PG_BIN/pg_ctl" -D "$FERR/pgdata" -m fast stop >/dev/null 2>&1; }

BASE=dados/base_codigos_servico.xlsx
[ -f "saneamento/$BASE" ] || BASE=exemplo/base_exemplo.csv
JAR=codigos-api/target/codigos-api-0.4.0.jar
for porta in 8000 8080; do lsof -ti "tcp:$porta" -sTCP:LISTEN 2>/dev/null | xargs kill 2>/dev/null; done
echo "  Montando o catalogo..."
(cd saneamento && .venv/bin/python exportar.py "$BASE" --tudo >/dev/null) || falhou "Nao consegui ler a base."
if [ ! -f "$JAR" ] || [ -n "$(find codigos-api/src codigos-api/pom.xml -newer "$JAR" -type f 2>/dev/null | head -1)" ]; then
  echo "  Compilando o Java (o codigo mudou; precisa de internet na primeira vez, leva uns 2 minutos)..."
  (cd codigos-api && chmod +x mvnw && ./mvnw -q -DskipTests package) || { [ -f "$JAR" ] && touch "$JAR" && echo "  Falhou a compilacao: usando a versao ja compilada."; } || falhou "Falhou a compilacao do Java."
fi
mkdir -p "$FERR"
MOKKA_SEGREDO=$(aleatorio)
export MOKKA_SEGREDO
(umask 077; printf '%s' "$MOKKA_SEGREDO" > "$FERR/segredo.txt")
BANCO=POSTGRES
[ "$(cat "$FERR/banco.txt" 2>/dev/null)" = H2 ] && BANCO=H2
PERFIL=""
if [ "$BANCO" = POSTGRES ]; then
  echo "  Ligando o PostgreSQL..."
  if ligar_postgres; then PERFIL="--spring.profiles.active=postgres"; else BANCO=H2; echo "  PostgreSQL nao ligou: usando o banco de reserva H2 (na memoria)."; fi
fi
FRONT=""
[ -f front/index.html ] && FRONT="--spring.web.resources.static-locations[0]=file:../front/ --spring.web.resources.static-locations[1]=classpath:/static/"
(cd saneamento && exec .venv/bin/python api.py "$BASE") > /tmp/mokka-python.log 2>&1 &
PID_PY=$!
ligar_java() {
  (cd codigos-api && exec "$JAVA" -Dfile.encoding=UTF-8 -jar target/codigos-api-0.4.0.jar --mokka.dados=../saneamento/saida/data.sql $PERFIL $ZERAR $FRONT) > /tmp/mokka-java.log 2>&1 &
  PID_JAVA=$!
}
ligar_java
trap 'kill $PID_PY $PID_JAVA 2>/dev/null; parar_postgres; echo "  MOKKA desligado."' EXIT INT TERM
echo "  Ligando, leva uns 30 segundos..."
for _ in $(seq 90); do
  curl -s -o /dev/null http://127.0.0.1:8000/saude && curl -s -o /dev/null "$SITE/api/sistema" && break
  if [ -n "$PERFIL" ] && ! kill -0 $PID_JAVA 2>/dev/null; then
    echo "  O Java nao ligou com o PostgreSQL: usando o banco de reserva H2."
    PERFIL=""; BANCO=H2; ligar_java
  fi
  sleep 2
done
curl -s -o /dev/null "$SITE/api/sistema" || falhou "Nao ligou. Veja /tmp/mokka-java.log e /tmp/mokka-python.log"
if [ -z "$SEM_NAVEGADOR" ]; then
  if command -v open >/dev/null; then open "$SITE/"; elif command -v xdg-open >/dev/null; then xdg-open "$SITE/" >/dev/null 2>&1; fi
fi
echo ""
echo "  PRONTO: $SITE"
echo "  Banco: $BANCO$([ "$BANCO" = H2 ] && echo ' (reserva, na memoria)' || echo ' (guarda tudo; ./MOKKA.command zerar recomeca do zero)')"
echo "  Teste do microfone: $SITE/#testar-voz"
echo "  Swagger: $SITE/swagger"
echo "  Safari: para falar, ative o Ditado em Ajustes do Sistema > Teclado > Ditado."
echo "  Para desligar, feche esta janela ou aperte Ctrl+C."
wait
