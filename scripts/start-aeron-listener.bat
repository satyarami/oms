@echo off
REM Start Aeron Listener (Aeron -> Chronicle Queue) in a separate console window
cd /d C:\Project\oms-kdb

REM Chronicle Queue requires --add-opens on Java 17+
set "MAVEN_OPTS=--add-opens=java.base/java.nio.channels.spi=ALL-UNNAMED --add-opens=java.base/java.nio=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/sun.nio.ch=ALL-UNNAMED --add-exports=java.base/sun.nio.ch=ALL-UNNAMED"

start "Aeron Listener" cmd /k mvn exec:java -Dexec.mainClass="com.satya.oms.kdb.AeronListenerMain"
