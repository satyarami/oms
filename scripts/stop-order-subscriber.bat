@echo off
REM Stop Order Subscriber by killing its console window and child processes
echo Stopping Order Subscriber...
taskkill /FI "WINDOWTITLE eq Order Subscriber*" /T /F >nul 2>&1
if %ERRORLEVEL%==0 (echo Order Subscriber stopped.) else (echo Order Subscriber was not running.)