@echo off
REM Start Aeron Stat in a separate console window
cd /d C:\Project\oms

REM Aeron requires --add-opens on Java 17+
set "MAVEN_OPTS=--add-opens=java.base/sun.nio.ch=ALL-UNNAMED --add-opens=java.base/java.nio=ALL-UNNAMED --add-opens=java.base/java.nio.channels.spi=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED --add-exports=java.base/sun.nio.ch=ALL-UNNAMED"

start "Aeron Stat" cmd /k mvn exec:java -Dexec.mainClass="AeronStat"
pause
