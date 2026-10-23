@echo off
setlocal
cd /d "%~dp0"
title MOKKA - codigos de servico
set "PY_DIR=%~dp0saneamento"
set "VENV=%PY_DIR%\.venv"
set "APP=%~dp0codigos-api"
set "JAR=%APP%\target\codigos-api-0.3.0.jar"
set "FRONT="
if exist "%~dp0front\index.html" set "FRONT=--spring.web.resources.static-locations[0]=file:../front/ --spring.web.resources.static-locations[1]=classpath:/static/"
set "BASE=dados\base_codigos_servico.xlsx"
if not exist "%PY_DIR%\%BASE%" set "BASE=exemplo\base_exemplo.csv"

echo.
echo  MOKKA - codigos de servico
echo  ----------------------------------------------
if "%BASE%"=="exemplo\base_exemplo.csv" (
  echo  Usando a BASE DE EXEMPLO ficticia.
  echo  Para usar a base real, coloque a planilha em saneamento\dados\base_codigos_servico.xlsx
) else (
  echo  Usando a base real: saneamento\%BASE%
)
if defined FRONT echo  Usando a tela da pasta front: edite front\index.html e aperte F5 no navegador.
echo.

where java >nul 2>nul || (echo Java nao encontrado. Instale o JDK 17 ou mais novo em https://adoptium.net e rode de novo. & pause & exit /b 1)

powershell -NoProfile -Command "Get-NetTCPConnection -LocalPort 8000,8080 -State Listen -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }"

if not exist "%VENV%\Scripts\python.exe" call :preparar_python || (pause & exit /b 1)

echo Montando o catalogo a partir da base...
pushd "%PY_DIR%"
"%VENV%\Scripts\python.exe" exportar.py "%BASE%" --tudo || (popd & echo Nao consegui ler a base. Confira o arquivo. & pause & exit /b 1)
popd

if not exist "%JAR%" (
  echo Compilando a aplicacao Java, so na primeira vez, precisa de internet...
  pushd "%APP%"
  call mvnw.cmd -q -DskipTests package || (popd & echo Falhou a compilacao do Java. & pause & exit /b 1)
  popd
)

echo Ligando o servico de similaridade (Python)...
start "Similaridade - Python (nao feche)" /min /d "%PY_DIR%" "%VENV%\Scripts\python.exe" api.py "%BASE%"
echo Ligando a aplicacao (Java)...
start "Aplicacao - Java (nao feche)" /min /d "%APP%" java -Dfile.encoding=UTF-8 -jar "%JAR%" --spring.sql.init.data-locations=file:../saneamento/saida/data.sql --spring.sql.init.encoding=UTF-8 %FRONT%

echo Esperando os dois ligarem, leva uns 30 segundos...
set /a N=0
:esperar
set /a N+=1
if %N% gtr 90 (echo Demorou demais. Abra as janelas minimizadas na barra de tarefas pra ver o erro. & pause & exit /b 1)
timeout /t 2 /nobreak >nul
curl -s -o nul http://127.0.0.1:8000/saude || goto esperar
curl -s -o nul http://127.0.0.1:8080/api/unidades || goto esperar
start "" "http://localhost:8080/?%RANDOM%"
echo.
echo  PRONTO
echo  Neste computador ......... http://localhost:8080
echo  Swagger (API) ............ http://localhost:8080/swagger
echo  Outros PCs na mesma rede:
for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /r /c:"IPv4"') do for /f "tokens=* delims= " %%b in ("%%a") do echo                              http://%%b:8080
echo.
echo  Se o Windows perguntar sobre o Firewall, marque "Redes privadas" e clique em Permitir.
echo  O microfone so funciona pelo endereco localhost, no computador que esta rodando.
echo  Para desligar, rode o parar.bat ou feche as janelas "Similaridade" e "Aplicacao".
echo.
pause
exit /b 0

:preparar_python
set "PYBASE="
if exist "%USERPROFILE%\miniconda3\python.exe" set PYBASE="%USERPROFILE%\miniconda3\python.exe"
if not defined PYBASE if exist "%USERPROFILE%\anaconda3\python.exe" set PYBASE="%USERPROFILE%\anaconda3\python.exe"
if not defined PYBASE for %%V in (3.13 3.12 3.14 3.11) do if not defined PYBASE py -%%V -c "" >nul 2>nul && set "PYBASE=py -%%V"
if not defined PYBASE set "PYBASE=python"
echo Primeira vez: preparando o Python (precisa de internet, so desta vez)...
%PYBASE% -m venv "%VENV%" || (echo Nao consegui criar o ambiente Python. Instale o Python 3.11 a 3.13 em https://python.org & exit /b 1)
"%VENV%\Scripts\python.exe" -m pip install -q --disable-pip-version-check pandas numpy scikit-learn openpyxl || (rmdir /s /q "%VENV%" & echo Falhou a instalacao das bibliotecas. Confira a internet e rode de novo. & exit /b 1)
exit /b 0
