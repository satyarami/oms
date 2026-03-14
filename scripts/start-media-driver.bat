@echo off
REM Start Media Driver in a separate console window
cd /d C:\Project\oms

start "Aeron Media Driver" cmd /k mvn exec:java -Dexec.mainClass="com.satya.oms.driver.MediaDriverMain" -Dexec.jvmArgs="--add-opens java.base/sun.nio.ch=ALL-UNNAMED"
pause