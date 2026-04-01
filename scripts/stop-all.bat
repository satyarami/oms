@echo off
REM Stop all 8 OMS applications by killing their console windows

REM --- oms-core ---
call "%~dp0stop-media-driver.bat"
call "%~dp0stop-order-subscriber.bat"

REM --- q (kdb+) ---
call "C:\Project\oms-kdb\scripts\stop-q.bat"

REM --- oms-ui ---
call "%~dp0stop-oms-ui.bat"

REM --- oms-kdb ---
call "%~dp0stop-aeron-listener.bat"
call "%~dp0stop-kdb-writer.bat"
call "%~dp0stop-queue-monitor.bat"

REM --- aeron-stat ---
call "%~dp0stop-aeron-stat.bat"

echo All OMS applications have been stopped (if running).