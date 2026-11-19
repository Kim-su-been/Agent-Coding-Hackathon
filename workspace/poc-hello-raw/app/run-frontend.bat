@echo off
title Hello Frontend (Vite :5174)
call "%~dp0..\setenv.bat"
call "%~dp0..\setup-env.bat"
cd /d "%~dp0frontend"
call npm run dev
pause
