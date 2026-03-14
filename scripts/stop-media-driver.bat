@echo off
REM Stop Aeron Media Driver by killing its console window using WMIC
wmic process where "CommandLine like '%%Aeron Media Driver%%'" call terminate