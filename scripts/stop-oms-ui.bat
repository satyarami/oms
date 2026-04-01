@echo off
REM Stop OMS UI by killing its console window and child processes
echo Stopping OMS UI...
taskkill /FI "WINDOWTITLE eq OMS UI*" /T /F >nul 2>&1
if %ERRORLEVEL%==0 (echo OMS UI stopped.) else (echo OMS UI was not running.)
