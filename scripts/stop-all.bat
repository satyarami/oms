@echo off
REM Stop all 7 OMS applications by killing their console windows

REM ── oms-core ──
call "%~dp0stop-media-driver.bat"
call "%~dp0stop-order-subscriber.bat"
call "%~dp0stop-aeron-stat.bat"

REM ── oms-ui ──
call "%~dp0stop-oms-ui.bat"

REM ── oms-kdb ──
call "%~dp0stop-aeron-listener.bat"
call "%~dp0stop-kdb-writer.bat"
call "%~dp0stop-queue-monitor.bat"

echo All OMS applications have been stopped (if running).