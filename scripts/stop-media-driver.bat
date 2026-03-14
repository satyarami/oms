@echo off
REM Stop Aeron Media Driver by killing its console window

title_to_kill="Aeron Media Driver"
for /f "tokens=2 delims==;" %%i in ('tasklist /v /fi "WINDOWTITLE eq Aeron Media Driver*" /fo csv ^| findstr /i "Aeron Media Driver"') do (
    echo Stopping Aeron Media Driver (PID %%i)...
    taskkill /PID %%i /F
)
