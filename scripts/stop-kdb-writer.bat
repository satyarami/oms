@echo off
REM Stop KDB Writer by killing its console window using WMIC
wmic process where "CommandLine like '%%KDB Writer%%'" call terminate
