@echo off
rem Build the self-contained demo jar (frontend dist + backend bootJar)
set "JAVA_HOME=C:\Program Files\Java\jdk-27.0.1.1"
set "PATH=%JAVA_HOME%\bin;C:\Program Files\nodejs;%PATH%"
cd /d "%~dp0..\frontend"
call "C:\Program Files\nodejs\npm.cmd" run build
if errorlevel 1 exit /b %errorlevel%
cd /d "%~dp0..\backend"
call "C:\Users\TYLI\tools\gradle-9.9.1\bin\gradle.bat" bootJar --no-daemon
exit /b %errorlevel%
