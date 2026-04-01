@echo off
REM Stop Aeron Stat by killing its console window and child processes
echo Stopping Aeron Stat...
taskkill /FI "WINDOWTITLE eq Aeron Stat*" /T /F >nul 2>&1
if %ERRORLEVEL%==0 (echo Aeron Stat stopped.) else (echo Aeron Stat was not running.)