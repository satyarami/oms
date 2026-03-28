@echo off
REM Stop Aeron Listener by killing its console window using WMIC
wmic process where "CommandLine like '%%Aeron Listener%%'" call terminate
