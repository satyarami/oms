@echo off
REM Stop Chronicle Queue Monitor by killing its console window using WMIC
wmic process where "CommandLine like '%%Queue Monitor%%'" call terminate
