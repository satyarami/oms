@echo off
REM Stop Aeron Listener by killing its console window and child processes
echo Stopping Aeron Listener...
taskkill /FI "WINDOWTITLE eq Aeron Listener*" /T /F >nul 2>&1
if %ERRORLEVEL%==0 (echo Aeron Listener stopped.) else (echo Aeron Listener was not running.)
