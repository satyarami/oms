@echo off
REM Start all 7 applications in separate console windows
REM This master script launches: Media Driver, Order Subscriber, Aeron Stat, OMS UI,
REM Aeron Listener, KDB Writer, and Queue Monitor

echo Starting all applications...
echo.

REM Start Media Driver
echo [1/7] Starting Aeron Media Driver...
call start-media-driver.bat
timeout /t 2 /nobreak

REM Start Order Subscriber
echo [2/7] Starting Order Subscriber...
call start-order-subscriber.bat
timeout /t 2 /nobreak

REM Start Aeron Stat
echo [3/7] Starting Aeron Stat (Monitoring)...
call start-aeron-stat.bat
timeout /t 2 /nobreak

REM Start OMS UI
echo [4/7] Starting OMS UI...
call start-oms-ui.bat
timeout /t 2 /nobreak

REM Start Aeron Listener (Aeron -> Chronicle Queue)
echo [5/7] Starting Aeron Listener...
call start-aeron-listener.bat
timeout /t 3 /nobreak

REM Start KDB Writer (Chronicle Queue -> kdb+)
echo [6/7] Starting KDB Writer...
call start-kdb-writer.bat
timeout /t 2 /nobreak

REM Start Chronicle Queue Monitor
echo [7/7] Starting Queue Monitor...
call start-queue-monitor.bat

echo.
echo All applications have been started in separate console windows.
echo Close each window individually to stop the applications.
