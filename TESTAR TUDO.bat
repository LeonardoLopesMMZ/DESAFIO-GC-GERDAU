@echo off
setlocal
cd /d "%~dp0"
title MOKKA - testar tudo
curl -s -o nul http://127.0.0.1:8080/api/unidades
if errorlevel 1 (
  echo Ligando o MOKKA numa janela separada...
  set "SEM_NAVEGADOR=1"
  start "MOKKA - ligando" /min cmd /c call "%~dp0iniciar.bat"
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0testar.ps1"
echo.
pause
