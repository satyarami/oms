@echo off
REM Start Order Subscriber in a separate console window
cd /d C:\Project\oms
start "Order Subscriber" cmd /k mvn exec:java -Dexec.mainClass="com.satya.oms.aeron.OrderSubscriber"
pause
