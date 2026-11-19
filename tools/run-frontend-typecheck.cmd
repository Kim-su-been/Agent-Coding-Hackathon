@echo off
rem Run frontend type-check (absolute node/npm paths)
set "PATH=C:\Program Files\nodejs;%PATH%"
cd /d "%~dp0..\frontend"
call "C:\Program Files\nodejs\npm.cmd" run type-check
exit /b %errorlevel%
