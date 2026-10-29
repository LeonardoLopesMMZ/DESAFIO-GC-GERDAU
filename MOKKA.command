#!/bin/bash
cd "$(dirname "$0")" || exit 1
RAIZ="$PWD"
FERR="$RAIZ/.ferramentas"
PYV="3.12.9+20250317"
SITE="http://localhost:8080"
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

BASE=dados/base_codigos_servico.xlsx
[ -f "saneamento/$BASE" ] || BASE=exemplo/base_exemplo.csv
JAR=codigos-api/target/codigos-api-0.4.0.jar
for porta in 8000 8080; do lsof -ti "tcp:$porta" -sTCP:LISTEN 2>/dev/null | xargs kill 2>/dev/null; done
echo "  Montando o catalogo..."
(cd saneamento && .venv/bin/python exportar.py "$BASE" --tudo >/dev/null) || falhou "Nao consegui ler a base."
if [ ! -f "$JAR" ]; then
  echo "  Primeira vez: compilando o Java (precisa de internet, leva uns 2 minutos)..."
  (cd codigos-api && chmod +x mvnw && ./mvnw -q -DskipTests package) || falhou "Falhou a compilacao do Java."
fi
FRONT=""
[ -f front/index.html ] && FRONT="--spring.web.resources.static-locations[0]=file:../front/ --spring.web.resources.static-locations[1]=classpath:/static/"
(cd saneamento && exec .venv/bin/python api.py "$BASE") > /tmp/mokka-python.log 2>&1 &
PID_PY=$!
(cd codigos-api && exec "$JAVA" -Dfile.encoding=UTF-8 -jar target/codigos-api-0.4.0.jar --spring.sql.init.data-locations=file:../saneamento/saida/data.sql --spring.sql.init.encoding=UTF-8 $FRONT) > /tmp/mokka-java.log 2>&1 &
PID_JAVA=$!
trap 'kill $PID_PY $PID_JAVA 2>/dev/null; echo "  MOKKA desligado."' EXIT INT TERM
echo "  Ligando, leva uns 30 segundos..."
for _ in $(seq 90); do
  curl -s -o /dev/null http://127.0.0.1:8000/saude && curl -s -o /dev/null "$SITE/api/unidades" && break
  sleep 2
done
curl -s -o /dev/null "$SITE/api/unidades" || falhou "Nao ligou. Veja /tmp/mokka-java.log e /tmp/mokka-python.log"
if [ -z "$SEM_NAVEGADOR" ]; then
  if command -v open >/dev/null; then open "$SITE/"; elif command -v xdg-open >/dev/null; then xdg-open "$SITE/" >/dev/null 2>&1; fi
fi
echo ""
echo "  PRONTO: $SITE"
echo "  Teste do microfone: $SITE/#testar-voz"
echo "  Swagger: $SITE/swagger"
echo "  Safari: para falar, ative o Ditado em Ajustes do Sistema > Teclado > Ditado."
echo "  Para desligar, feche esta janela ou aperte Ctrl+C."
wait
