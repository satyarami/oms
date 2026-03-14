@echo off
REM Start Aeron Stat in a separate console window
cd /d C:\Users\satya\eclipse-workspace\MVN
start "Aeron Stat" cmd /k mvn exec:java -Dexec.mainClass="AeronStat"
pause
