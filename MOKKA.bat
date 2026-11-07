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
set "PERFIL="
set "ZERAR="
set "BANCO_USO=H2"
set "BANCO_PREF=POSTGRES"
if exist "%FERR%\banco.txt" set /p BANCO_PREF=<"%FERR%\banco.txt"
set "BASE=dados\base_codigos_servico.xlsx"
if not exist "%PY_DIR%\%BASE%" set "BASE=exemplo\base_exemplo.csv"

if /i "%~1"=="ligar" (call :ligar & exit /b)
if /i "%~1"=="testar" (call :testar & exit /b)
if /i "%~1"=="parar" (call :parar & exit /b)
if /i "%~1"=="zerar" (set "ZERAR=--mokka.zerar=true" & call :ligar & exit /b)

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
echo    [6]  Trocar o banco (escolhido: %BANCO_PREF%)
echo    [7]  Zerar o banco e recomecar do zero
echo    [0]  Sair
echo.
choice /c 12345670 /n /m "   Escolha uma opcao: "
if errorlevel 8 exit /b 0
if errorlevel 7 goto op7
if errorlevel 6 goto op6
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
:op6
if /i "%BANCO_PREF%"=="POSTGRES" (set "BANCO_PREF=H2") else (set "BANCO_PREF=POSTGRES")
if not exist "%FERR%" mkdir "%FERR%"
>"%FERR%\banco.txt" echo %BANCO_PREF%
echo    Banco escolhido: %BANCO_PREF%. Ligando de novo com ele...
call :ligar
pause
goto menu
:op7
echo    Isso apaga pedidos, pessoas, fotos, aprendizado e codigos criados, e carrega a base de novo.
choice /c SN /n /m "   Tem certeza? [S/N] "
if errorlevel 2 goto menu
set "ZERAR=--mokka.zerar=true"
call :ligar
set "ZERAR="
pause
goto menu

:estado
set "AGORA=desligado"
curl -s -o nul %SITE%/api/sistema && set "AGORA=LIGADO em %SITE%, banco de reserva H2"
curl -s %SITE%/api/sistema 2>nul | findstr /c:"PostgreSQL" >nul && set "AGORA=LIGADO em %SITE%, banco PostgreSQL"
echo    Situacao: %AGORA%
if "%BASE%"=="exemplo\base_exemplo.csv" (echo    Base: exemplo ficticio) else (echo    Base: real da Gerdau)
if defined FRONT echo    Tela: pasta front ^(edite e aperte F5^)
exit /b 0

:abrir
curl -s -o nul %SITE%/api/sistema || call :ligar
start "" msedge "%SITE%%~1"
exit /b 0

:ligar
call :java_ok || call :baixar_java || exit /b 1
call :parar
if not exist "%VENV%\Scripts\python.exe" call :preparar_python || exit /b 1
echo    Montando o catalogo a partir da base...
pushd "%PY_DIR%"
"%VENV%\Scripts\python.exe" exportar.py "%BASE%" --tudo >nul || (popd & echo    Nao consegui ler a base. Confira o arquivo. & exit /b 1)
popd
set "COMPILAR="
if not exist "%JAR%" set "COMPILAR=1"
if exist "%JAR%" powershell -NoProfile -Command "$j = (Get-Item '%JAR%').LastWriteTime; if (Get-ChildItem '%APP%\src', '%APP%\pom.xml' -Recurse -File | Where-Object { $_.LastWriteTime -gt $j } | Select-Object -First 1) { exit 1 }" || set "COMPILAR=1"
if defined COMPILAR call :compilar || exit /b 1
if not exist "%FERR%" mkdir "%FERR%"
for /f "delims=" %%s in ('powershell -NoProfile -Command "[guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')"') do set "MOKKA_SEGREDO=%%s"
>"%FERR%\segredo.txt" echo %MOKKA_SEGREDO%
set "PERFIL="
set "BANCO_USO=H2"
if /i "%BANCO_PREF%"=="POSTGRES" call :postgres
echo    Ligando o servico de similaridade (Python)...
start "Similaridade - Python (nao feche)" /min /d "%PY_DIR%" "%VENV%\Scripts\python.exe" api.py "%BASE%"
call :ligar_java
echo    Esperando ligar, leva uns 30 segundos...
set /a N=0
:esperar
set /a N+=1
if %N% gtr 90 (echo    Demorou demais. Abra as janelas minimizadas na barra de tarefas pra ver o erro. & exit /b 1)
timeout /t 2 /nobreak >nul
curl -s -o nul http://127.0.0.1:8000/saude || goto esperar
curl -s -o nul %SITE%/api/sistema && goto ligou
if %N% equ 35 if defined PERFIL call :reserva
goto esperar
:ligou
if not defined SEM_NAVEGADOR start "" msedge "%SITE%/?%RANDOM%"
echo.
echo    PRONTO  (banco: %BANCO_USO%)
echo    Neste computador ......... %SITE%
echo    Swagger (API) ............ %SITE%/swagger
echo    Outros PCs na mesma rede:
for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /r /c:"IPv4"') do for /f "tokens=* delims= " %%b in ("%%a") do echo                               http://%%b:8080
echo    A voz funciona no Edge, no Chrome ou no Safari, pelo endereco localhost.
echo    Se o Windows perguntar sobre o Firewall, marque "Redes privadas" e clique em Permitir.
exit /b 0

:testar
curl -s -o nul %SITE%/api/sistema
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
powershell -NoProfile -ExecutionPolicy Bypass -File "%RAIZ%scripts\postgres.ps1" parar
exit /b 0

:compilar
echo    Compilando a aplicacao Java, o codigo mudou, precisa de internet...
pushd "%APP%"
call mvnw.cmd -q -DskipTests package
set "ERRO=%ERRORLEVEL%"
popd
if "%ERRO%"=="0" exit /b 0
if not exist "%JAR%" (echo    Falhou a compilacao do Java. & exit /b 1)
echo    Falhou a compilacao: usando a versao ja compilada.
copy /b "%JAR%"+,, "%JAR%" >nul
exit /b 0

:postgres
echo    Ligando o banco PostgreSQL...
powershell -NoProfile -ExecutionPolicy Bypass -File "%RAIZ%scripts\postgres.ps1" ligar || (echo    O PostgreSQL nao ligou: usando o banco de reserva H2, na memoria. & exit /b 0)
call "%FERR%\pg-env.bat"
set "PERFIL=--spring.profiles.active=postgres"
set "BANCO_USO=PostgreSQL"
exit /b 0

:ligar_java
echo    Ligando a aplicacao (Java), banco %BANCO_USO%...
start "Aplicacao - Java (nao feche)" /min /d "%APP%" "%JAVA%" -Dfile.encoding=UTF-8 -jar "%JAR%" --mokka.dados=../saneamento/saida/data.sql %PERFIL% %ZERAR% %FRONT%
exit /b 0

:reserva
echo    O Java nao ligou com o PostgreSQL: usando o banco de reserva H2.
taskkill /f /fi "WINDOWTITLE eq Aplicacao - Java*" >nul 2>&1
set "PERFIL="
set "BANCO_USO=H2"
call :ligar_java
exit /b 0

:java_ok
where java >nul 2>nul || exit /b 1
java -version 2>&1 | findstr /r /c:"version \"1[7-9]" /c:"version \"2[0-9]" >nul || exit /b 1
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
