@echo off
REM Start all 3 applications in separate console windows
REM This master script launches: Media Driver, Order Subscriber, and Aeron Stat

echo Starting all applications...
echo.

REM Start Media Driver
echo [1/3] Starting Aeron Media Driver...
call start-media-driver.bat
timeout /t 2 /nobreak

REM Start Order Subscriber
echo [2/3] Starting Order Subscriber...
call start-order-subscriber.bat
timeout /t 2 /nobreak

REM Start Aeron Stat
echo [3/3] Starting Aeron Stat (Monitoring)...
call start-aeron-stat.bat

echo.
echo All applications have been started in separate console windows.
echo Close each window individually to stop the applications.
