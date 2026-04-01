@echo off
REM Stop Aeron Media Driver by killing its console window and child processes
echo Stopping Aeron Media Driver...
taskkill /FI "WINDOWTITLE eq Aeron Media Driver*" /T /F >nul 2>&1
if %ERRORLEVEL%==0 (echo Aeron Media Driver stopped.) else (echo Aeron Media Driver was not running.)