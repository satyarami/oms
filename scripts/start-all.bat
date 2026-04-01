@echo off
REM Start all 8 applications in separate console windows
REM This master script launches: Media Driver, Order Subscriber, q (kdb+), OMS UI,
REM Aeron Listener, KDB Writer, Queue Monitor, and Aeron Stat

echo Starting all applications...
echo.

REM Start Media Driver
echo [1/8] Starting Aeron Media Driver...
call start-media-driver.bat
timeout /t 2 /nobreak

REM Start Order Subscriber
echo [2/8] Starting Order Subscriber...
call start-order-subscriber.bat
timeout /t 2 /nobreak

REM Start q with schema on port 5000
echo [3/8] Starting q (schema.q, port 5000)...
call "C:\Project\oms-kdb\scripts\start-q.bat"
timeout /t 2 /nobreak

REM Start OMS UI
echo [4/8] Starting OMS UI...
call start-oms-ui.bat
timeout /t 2 /nobreak

REM Start Aeron Listener (Aeron -> Chronicle Queue)
echo [5/8] Starting Aeron Listener...
call start-aeron-listener.bat
timeout /t 3 /nobreak

REM Start KDB Writer (Chronicle Queue -> kdb+)
echo [6/8] Starting KDB Writer...
call start-kdb-writer.bat
timeout /t 2 /nobreak

REM Start Chronicle Queue Monitor
echo [7/8] Starting Queue Monitor...
call start-queue-monitor.bat
timeout /t 2 /nobreak

REM Start Aeron Stat
echo [8/8] Starting Aeron Stat...
call start-aeron-stat.bat

echo.
echo All applications have been started in separate console windows.
echo Close each window individually to stop the applications.
