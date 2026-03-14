@echo off
REM Stop all 3 OMS applications by killing their console windows

call stop-media-driver.bat
call stop-order-subscriber.bat
call stop-aeron-stat.bat

echo All OMS applications have been stopped (if running).
