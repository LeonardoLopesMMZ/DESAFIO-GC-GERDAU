@echo off
setlocal
cd /d "%~dp0"
title MOKKA - codigos de servico
set "RAIZ=%~dp0"
set "PY_DIR=%~dp0saneamento"
set "VENV=%PY_DIR%\.venv"
set "APP=%~dp0codigos-api"
set "JAR=%APP%\target\codigos-api-0.4.0.jar"
set "SITE=http://localhost:8080"
set "FERR=%~dp0.ferramentas"
set "JAVA=java"
set "FRONT="
if exist "%~dp0front\index.html" set "FRONT=--spring.web.resources.static-locations[0]=file:../front/ --spring.web.resources.static-locations[1]=classpath:/static/"
set "BASE=dados\base_codigos_servico.xlsx"
if not exist "%PY_DIR%\%BASE%" set "BASE=exemplo\base_exemplo.csv"

if /i "%~1"=="ligar" (call :ligar & exit /b)
if /i "%~1"=="testar" (call :testar & exit /b)
if /i "%~1"=="parar" (call :parar & exit /b)

:menu
cls
echo.
echo    MOKKA  ^|  codigos de servico
echo    ============================================
call :estado
echo.
echo    [1]  Ligar e abrir o site
echo    [2]  Testar tudo e abrir o teste do microfone
echo    [3]  Abrir o teste do microfone
echo    [4]  Abrir o Swagger (API)
echo    [5]  Desligar
echo    [0]  Sair
echo.
choice /c 123450 /n /m "   Escolha uma opcao: "
if errorlevel 6 exit /b 0
if errorlevel 5 goto op5
if errorlevel 4 goto op4
if errorlevel 3 goto op3
if errorlevel 2 goto op2
call :ligar
pause
goto menu
:op2
call :testar
pause
goto menu
:op3
call :abrir "/#testar-voz"
goto menu
:op4
call :abrir "/swagger"
goto menu
:op5
call :parar
timeout /t 2 >nul
goto menu

:estado
curl -s -o nul %SITE%/api/unidades && (echo    Situacao: LIGADO em %SITE%) || (echo    Situacao: desligado)
if "%BASE%"=="exemplo\base_exemplo.csv" (echo    Base: exemplo ficticio) else (echo    Base: real da Gerdau)
if defined FRONT echo    Tela: pasta front ^(edite e aperte F5^)
exit /b 0

:abrir
curl -s -o nul %SITE%/api/unidades || call :ligar
start "" msedge "%SITE%%~1"
exit /b 0

:ligar
where java >nul 2>nul || call :baixar_java || exit /b 1
call :parar
if not exist "%VENV%\Scripts\python.exe" call :preparar_python || exit /b 1
echo    Montando o catalogo a partir da base...
pushd "%PY_DIR%"
"%VENV%\Scripts\python.exe" exportar.py "%BASE%" --tudo >nul || (popd & echo    Nao consegui ler a base. Confira o arquivo. & exit /b 1)
popd
if not exist "%JAR%" (
  echo    Compilando a aplicacao Java, so na primeira vez, precisa de internet...
  pushd "%APP%"
  call mvnw.cmd -q -DskipTests package || (popd & echo    Falhou a compilacao do Java. & exit /b 1)
  popd
)
echo    Ligando o servico de similaridade (Python)...
start "Similaridade - Python (nao feche)" /min /d "%PY_DIR%" "%VENV%\Scripts\python.exe" api.py "%BASE%"
echo    Ligando a aplicacao (Java)...
start "Aplicacao - Java (nao feche)" /min /d "%APP%" "%JAVA%" -Dfile.encoding=UTF-8 -jar "%JAR%" --spring.sql.init.data-locations=file:../saneamento/saida/data.sql --spring.sql.init.encoding=UTF-8 %FRONT%
echo    Esperando os dois ligarem, leva uns 30 segundos...
set /a N=0
:esperar
set /a N+=1
if %N% gtr 90 (echo    Demorou demais. Abra as janelas minimizadas na barra de tarefas pra ver o erro. & exit /b 1)
timeout /t 2 /nobreak >nul
curl -s -o nul http://127.0.0.1:8000/saude || goto esperar
curl -s -o nul %SITE%/api/unidades || goto esperar
if not defined SEM_NAVEGADOR start "" msedge "%SITE%/?%RANDOM%"
echo.
echo    PRONTO
echo    Neste computador ......... %SITE%
echo    Swagger (API) ............ %SITE%/swagger
echo    Outros PCs na mesma rede:
for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /r /c:"IPv4"') do for /f "tokens=* delims= " %%b in ("%%a") do echo                               http://%%b:8080
echo    A voz funciona no Edge, no Chrome ou no Safari, pelo endereco localhost.
echo    Se o Windows perguntar sobre o Firewall, marque "Redes privadas" e clique em Permitir.
exit /b 0

:testar
curl -s -o nul %SITE%/api/unidades
if errorlevel 1 (
  echo    Ligando o MOKKA antes de testar...
  set "SEM_NAVEGADOR=1"
  call :ligar
)
set "SEM_NAVEGADOR="
powershell -NoProfile -ExecutionPolicy Bypass -File "%RAIZ%scripts\testar.ps1"
exit /b 0

:parar
powershell -NoProfile -Command "Get-NetTCPConnection -LocalPort 8000,8080 -State Listen -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }"
exit /b 0

:baixar_java
for /f "delims=" %%j in ('dir /b /s "%FERR%\jdk\java.exe" 2^>nul') do set "JAVA=%%j"
if not "%JAVA%"=="java" goto java_pronto
echo    Primeira vez: baixando o Java 17, uns 190 MB, so desta vez...
if not exist "%FERR%\jdk" mkdir "%FERR%\jdk"
curl.exe -fL --retry 3 -o "%FERR%\jdk.zip" "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse" || (echo    Nao consegui baixar o Java. Confira a internet. & exit /b 1)
tar -xf "%FERR%\jdk.zip" -C "%FERR%\jdk" || (echo    O Java baixado veio com defeito. & exit /b 1)
del "%FERR%\jdk.zip"
for /f "delims=" %%j in ('dir /b /s "%FERR%\jdk\java.exe" 2^>nul') do set "JAVA=%%j"
if "%JAVA%"=="java" (echo    O Java baixado veio com defeito. & exit /b 1)
:java_pronto
for %%d in ("%JAVA%\..\..") do set "JAVA_HOME=%%~fd"
set "PATH=%JAVA_HOME%\bin;%PATH%"
exit /b 0

:baixar_python
if exist "%FERR%\python\python.exe" (set "PYBASE="%FERR%\python\python.exe"" & exit /b 0)
echo    Primeira vez: baixando o Python 3.12, uns 25 MB, so desta vez...
if not exist "%FERR%" mkdir "%FERR%"
curl.exe -fL --retry 3 -o "%FERR%\python.tar.gz" "https://github.com/astral-sh/python-build-standalone/releases/download/20250317/cpython-3.12.9+20250317-x86_64-pc-windows-msvc-install_only.tar.gz" || (echo    Nao consegui baixar o Python. Confira a internet. & exit /b 1)
tar -xzf "%FERR%\python.tar.gz" -C "%FERR%" || (echo    O Python baixado veio com defeito. & exit /b 1)
del "%FERR%\python.tar.gz"
set PYBASE="%FERR%\python\python.exe"
exit /b 0

:preparar_python
set "PYBASE="
if exist "%USERPROFILE%\miniconda3\python.exe" set PYBASE="%USERPROFILE%\miniconda3\python.exe"
if not defined PYBASE if exist "%USERPROFILE%\anaconda3\python.exe" set PYBASE="%USERPROFILE%\anaconda3\python.exe"
if not defined PYBASE for %%V in (3.13 3.12 3.14 3.11) do if not defined PYBASE py -%%V -c "" >nul 2>nul && set "PYBASE=py -%%V"
if not defined PYBASE python -c "import sys; sys.exit(0 if sys.version_info >= (3, 10) else 1)" >nul 2>nul && set "PYBASE=python"
if not defined PYBASE call :baixar_python || exit /b 1
echo    Primeira vez: preparando o Python (precisa de internet, so desta vez)...
%PYBASE% -m venv "%VENV%" || (echo    Nao consegui criar o ambiente Python. Instale o Python 3.11 a 3.13 em https://python.org & exit /b 1)
"%VENV%\Scripts\python.exe" -m pip install -q --disable-pip-version-check pandas numpy scikit-learn openpyxl || (rmdir /s /q "%VENV%" & echo    Falhou a instalacao das bibliotecas. Confira a internet e rode de novo. & exit /b 1)
exit /b 0
