@echo off
REM Stop Chronicle Queue Monitor by killing its console window and child processes
echo Stopping Queue Monitor...
taskkill /FI "WINDOWTITLE eq Queue Monitor*" /T /F >nul 2>&1
if %ERRORLEVEL%==0 (echo Queue Monitor stopped.) else (echo Queue Monitor was not running.)
