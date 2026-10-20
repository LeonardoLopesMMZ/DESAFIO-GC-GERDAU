@echo off
setlocal
cd /d "%~dp0"
title Prototipo - codigos de servico
set "PY_DIR=%~dp0saneamento"
set "BASE=%PY_DIR%\dados\base_codigos_servico.xlsx"
set "VENV=%PY_DIR%\.venv"
set "APP=%~dp0codigos-api"
set "JAR=%APP%\target\codigos-api-0.2.0.jar"

if not exist "%BASE%" (echo Coloque a planilha da Gerdau em saneamento\dados\base_codigos_servico.xlsx e rode de novo. & pause & exit /b 1)
where java >nul 2>nul || (echo Java nao encontrado. Instale o JDK 17 ou mais novo em https://adoptium.net & pause & exit /b 1)

powershell -NoProfile -Command "Get-NetTCPConnection -LocalPort 8000,8080 -State Listen -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }"

if not exist "%VENV%\Scripts\python.exe" call :preparar_python || (pause & exit /b 1)

if not exist "%PY_DIR%\saida\data.sql" (
  echo Montando o catalogo a partir da planilha...
  pushd "%PY_DIR%"
  "%VENV%\Scripts\python.exe" exportar.py dados\base_codigos_servico.xlsx --tudo || (popd & pause & exit /b 1)
  popd
)

if not exist "%JAR%" (
  echo Compilando a aplicacao Java, so na primeira vez...
  pushd "%APP%"
  call mvnw.cmd -q -DskipTests package || (popd & echo Falhou a compilacao do Java. & pause & exit /b 1)
  popd
)

echo Ligando o servico de similaridade (Python)...
start "Similaridade - Python (nao feche)" /min /d "%PY_DIR%" "%VENV%\Scripts\python.exe" api.py dados\base_codigos_servico.xlsx
echo Ligando a aplicacao (Java)...
start "Aplicacao - Java (nao feche)" /min /d "%APP%" java -Dfile.encoding=UTF-8 -jar "%JAR%" --spring.sql.init.data-locations=file:../saneamento/saida/data.sql --spring.sql.init.encoding=UTF-8

echo Esperando os dois ligarem, leva uns 30 segundos...
set /a N=0
:esperar
set /a N+=1
if %N% gtr 90 (echo Demorou demais. Abra as janelas minimizadas pra ver o erro. & pause & exit /b 1)
timeout /t 2 /nobreak >nul
curl -s -o nul http://127.0.0.1:8000/saude || goto esperar
curl -s -o nul http://127.0.0.1:8080/ || goto esperar
start "" "http://localhost:8080/?%RANDOM%"
echo.
echo Pronto: http://localhost:8080
echo Para desligar, feche as janelas "Similaridade" e "Aplicacao" na barra de tarefas.
timeout /t 10 >nul
exit /b 0

:preparar_python
set "PYBASE="
if exist "%USERPROFILE%\miniconda3\python.exe" set PYBASE="%USERPROFILE%\miniconda3\python.exe"
if not defined PYBASE if exist "%USERPROFILE%\anaconda3\python.exe" set PYBASE="%USERPROFILE%\anaconda3\python.exe"
if not defined PYBASE for %%V in (3.13 3.12 3.14 3.11) do if not defined PYBASE py -%%V -c "" >nul 2>nul && set "PYBASE=py -%%V"
if not defined PYBASE set "PYBASE=python"
echo Primeira vez: preparando o Python (precisa de internet, so desta vez)...
%PYBASE% -m venv "%VENV%" || (echo Nao consegui criar o ambiente Python. & exit /b 1)
"%VENV%\Scripts\python.exe" -m pip install -q --disable-pip-version-check pandas numpy scikit-learn openpyxl || (rmdir /s /q "%VENV%" & echo Falhou a instalacao das bibliotecas. Confira a internet e rode de novo. & exit /b 1)
exit /b 0
