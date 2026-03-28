@echo off
REM Stop OMS UI by killing its console window using WMIC
wmic process where "CommandLine like '%%OMS UI%%'" call terminate
