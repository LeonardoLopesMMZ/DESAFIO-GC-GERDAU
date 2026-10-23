#!/bin/bash
cd "$(dirname "$0")" || exit 1
set -f
echo ""
echo "  MOKKA | codigos de servico"
echo "  =========================="
BASE=dados/base_codigos_servico.xlsx
[ -f "saneamento/$BASE" ] || BASE=exemplo/base_exemplo.csv
JAR=codigos-api/target/codigos-api-0.3.0.jar
if ! command -v java >/dev/null; then echo "  Java nao encontrado. Instale o JDK 17 em https://adoptium.net"; read -r; exit 1; fi
PY=$(command -v python3.12 || command -v python3.13 || command -v python3.11 || command -v python3)
if [ ! -x saneamento/.venv/bin/python ]; then
  echo "  Primeira vez: preparando o Python (precisa de internet)..."
  "$PY" -m venv saneamento/.venv && saneamento/.venv/bin/pip install -q pandas numpy scikit-learn openpyxl || { echo "  Falhou a preparacao do Python"; read -r; exit 1; }
fi
for porta in 8000 8080; do lsof -ti "tcp:$porta" -sTCP:LISTEN 2>/dev/null | xargs kill 2>/dev/null; done
echo "  Montando o catalogo..."
(cd saneamento && .venv/bin/python exportar.py "$BASE" --tudo >/dev/null) || { echo "  Nao consegui ler a base"; read -r; exit 1; }
if [ ! -f "$JAR" ]; then
  echo "  Compilando o Java, so na primeira vez..."
  (cd codigos-api && chmod +x mvnw && ./mvnw -q -DskipTests package) || { echo "  Falhou a compilacao"; read -r; exit 1; }
fi
FRONT=""
[ -f front/index.html ] && FRONT="--spring.web.resources.static-locations[0]=file:../front/ --spring.web.resources.static-locations[1]=classpath:/static/"
(cd saneamento && exec .venv/bin/python api.py "$BASE") > /tmp/mokka-python.log 2>&1 &
PID_PY=$!
(cd codigos-api && exec java -Dfile.encoding=UTF-8 -jar target/codigos-api-0.3.0.jar --spring.sql.init.data-locations=file:../saneamento/saida/data.sql --spring.sql.init.encoding=UTF-8 $FRONT) > /tmp/mokka-java.log 2>&1 &
PID_JAVA=$!
trap 'kill $PID_PY $PID_JAVA 2>/dev/null; echo "  MOKKA desligado."' EXIT INT TERM
echo "  Ligando, leva uns 30 segundos..."
for _ in $(seq 90); do
  curl -s -o /dev/null http://127.0.0.1:8000/saude && curl -s -o /dev/null http://localhost:8080/api/unidades && break
  sleep 2
done
if ! curl -s -o /dev/null http://localhost:8080/api/unidades; then echo "  Nao ligou. Veja /tmp/mokka-java.log e /tmp/mokka-python.log"; exit 1; fi
URL="http://localhost:8080/"
if command -v open >/dev/null; then open -a Safari "$URL" 2>/dev/null || open "$URL"; elif command -v xdg-open >/dev/null; then xdg-open "$URL"; fi
echo ""
echo "  PRONTO: $URL"
echo "  Teste do microfone: ${URL}#testar-voz"
echo "  Swagger: ${URL}swagger"
echo "  Para desligar, feche esta janela ou aperte Ctrl+C."
wait
