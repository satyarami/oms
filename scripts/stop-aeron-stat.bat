@echo off
REM Stop Aeron Stat by killing its console window

title_to_kill="Aeron Stat"
for /f "tokens=2 delims==;" %%i in ('tasklist /v /fi "WINDOWTITLE eq Aeron Stat*" /fo csv ^| findstr /i "Aeron Stat"') do (
    echo Stopping Aeron Stat (PID %%i)...
    taskkill /PID %%i /F
)
