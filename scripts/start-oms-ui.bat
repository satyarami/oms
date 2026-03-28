@echo off
REM Start OMS UI (Swing client) in a separate console window
cd /d C:\Project\oms-ui
start "OMS UI" cmd /k mvn exec:java -Dexec.mainClass="com.satya.oms.App"
pause
