@echo off
REM Stop KDB Writer by killing its console window and child processes
echo Stopping KDB Writer...
taskkill /FI "WINDOWTITLE eq KDB Writer*" /T /F >nul 2>&1
if %ERRORLEVEL%==0 (echo KDB Writer stopped.) else (echo KDB Writer was not running.)
