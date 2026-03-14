@echo off
REM Stop Order Subscriber by killing its console window

title_to_kill="Order Subscriber"
for /f "tokens=2 delims==;" %%i in ('tasklist /v /fi "WINDOWTITLE eq Order Subscriber*" /fo csv ^| findstr /i "Order Subscriber"') do (
    echo Stopping Order Subscriber (PID %%i)...
    taskkill /PID %%i /F
)
