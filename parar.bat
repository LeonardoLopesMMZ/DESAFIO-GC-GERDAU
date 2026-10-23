@echo off
powershell -NoProfile -Command "Get-NetTCPConnection -LocalPort 8000,8080 -State Listen -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }"
echo MOKKA desligado.
timeout /t 3 >nul
