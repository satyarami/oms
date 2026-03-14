@echo off
REM Stop all 3 OMS applications by killing their console windows

call "%~dp0stop-media-driver.bat"
call "%~dp0stop-order-subscriber.bat"
call "%~dp0stop-aeron-stat.bat"

echo All OMS applications have been stopped (if running).