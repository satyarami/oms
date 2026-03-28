@echo off
REM Stop Order Subscriber by killing its console window using WMIC
wmic process where "CommandLine like '%%Order Subscriber%%'" call terminate