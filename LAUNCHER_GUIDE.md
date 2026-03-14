# Multi-Application Launcher Guide

This guide explains how to run all 3 OMS applications (MediaDriver, OrderSubscriber, and AeronStat) in separate console windows.

## Three Applications

1. **Aeron Media Driver** (MediaDriverMain)
   - Standalone communication driver for Aeron
   - Must start first before other applications
   - Location: `com.satya.oms.driver.MediaDriverMain`

2. **Order Subscriber** (OrderSubscriber)
   - Receives orders via Aeron IPC channel
   - Processes orders through Disruptor ring buffer
   - Location: `com.satya.oms.aeron.OrderSubscriber`

3. **Aeron Stat** (AeronStat)
   - Real-time monitoring tool for Aeron statistics
   - Shows driver health, counters, and performance metrics
   - Location: `AeronStat` (in MVN project)

---

## Option 1: Using Batch Scripts (Recommended for Windows)

### Start Individual Apps

From the command prompt, navigate to `C:\Project\oms\scripts\` and run:

```cmd
REM Start just the Media Driver
start-media-driver.bat

REM Start just the Order Subscriber
start-order-subscriber.bat

REM Start just the Aeron Stat
start-aeron-stat.bat
```

### Start All Apps at Once

From `C:\Project\oms\scripts\`, run:

```cmd
start-all.bat
```

This will launch all 3 applications in separate console windows automatically.

---

## Option 2: Using Eclipse Launch Configurations

### In Eclipse IDE

1. **Open Run Configurations**
   - Go to menu: `Run → Run Configurations`
   - Or press `Ctrl+Shift+D`

2. **Run Media Driver**
   - Select: `mediadriver`
   - Click `Run`

3. **Run Order Subscriber** (in separate console)
   - Select: `ordersubscriber`
   - Click `Run`

4. **Run Aeron Stat** (in MVN project, separate console)
   - Select: `aeronstat`
   - Click `Run`

Each application runs in its own Eclipse console.

---

## Startup Order

Always start applications in this order:

1. **Media Driver** → Wait 2-3 seconds for initialization
2. **Order Subscriber** → Waits for media driver to be ready
3. **Aeron Stat** → Monitors the driver and order flow

---

## Checking Application Status

### Media Driver Console
Should show:
```
Aeron Media Driver started. Press Ctrl+C to stop
```

### Order Subscriber Console
Should show:
```
Connected to standalone Aeron Media Driver.
OrderSubscriber started. Receiving orders...
```

### Aeron Stat Console
Should show:
```
HH:MM:SS - Aeron Stat (CnC v1.x.x), pid xxxxx, heartbeat age Xms
======================================================================
  X: counter values...
```

---

## Important Notes

### JVM Arguments
All applications require the following JVM flag for Java 21+:
```
--add-opens java.base/sun.nio.ch=ALL-UNNAMED
```

This is automatically included in all launch configurations.

### Aeron Directory
For AeronStat to work correctly, the `aeron.dir` property must point to the media driver's CnC file:
```
-Daeron.dir=C:\Project\aeron
```

### Stopping Applications
- Press `Ctrl+C` in each console window to stop the application
- Or close the console window directly

---

## Troubleshooting

### Port Already in Use
If you get "Address already in use" errors:
- Ensure no other instances of Media Driver are running
- Check: `netstat -ano | findstr :5555` (on Windows)
- Kill any stray Java processes: `taskkill /PID <pid> /F`

### Can't Connect to Media Driver
Ensure Media Driver is started FIRST and fully initialized before starting OrderSubscriber.

### Aeron Stat Shows "No Counters"
- Verify Media Driver is still running
- Check the aeron.dir path is correct
- Run: `mvn clean install` in the OMS project to rebuild

---

## File Locations

- **Batch Scripts**: `C:\Project\oms\scripts\`
  - `start-media-driver.bat`
  - `start-order-subscriber.bat`
  - `start-aeron-stat.bat`
  - `start-all.bat` (master launcher)

- **Eclipse Configs** (oms-core): `C:\Project\oms\.eclipse\`
  - `mediadriver.launch`
  - `ordersubscriber.launch`

- **Eclipse Configs** (MVN): `C:\Users\satya\eclipse-workspace\MVN\.eclipse\`
  - `aeronstat.launch`

