@echo off
REM Stop Aeron Stat by killing its console window using WMIC
wmic process where "CommandLine like '%%Aeron Stat%%'" call terminate