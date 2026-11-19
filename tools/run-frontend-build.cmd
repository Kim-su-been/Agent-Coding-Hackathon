@echo off
rem Run frontend production build (absolute node/npm paths)
set "PATH=C:\Program Files\nodejs;%PATH%"
cd /d "%~dp0..\frontend"
call "C:\Program Files\nodejs\npm.cmd" run build
exit /b %errorlevel%
