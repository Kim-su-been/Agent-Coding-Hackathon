@echo off
rem Adapter for the immutable QC check command shape.
rem The QC plan invokes: cmd.exe /c Agent-Coding-Hackathon/tools/(wrapper).cmd
rem cmd.exe parses that as command name "Agent-Coding-Hackathon" with switch
rem "/tools/(wrapper).cmd" (cmd does not accept forward slashes as a path in
rem the command position), so the direct call fails with "not recognized".
rem This adapter forwards the script name to the real wrapper under the
rem project folder and passes the exit code through unchanged.
if "%~1"=="" exit /b 1
call "%~dp0Agent-Coding-Hackathon\tools\%~nx1"
exit /b %errorlevel%
