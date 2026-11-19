@echo off
title Hello Backend (Spring Boot :8081)
call "%~dp0..\setenv.bat"
call "%~dp0..\setup-env.bat"
cd /d "%~dp0backend"
call mvn -B -o spring-boot:run
pause
