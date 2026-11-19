@echo off
rem Run backend JUnit 5 tests (absolute JDK/Gradle paths; app PATH is stale)
set "JAVA_HOME=C:\Program Files\Java\jdk-27.0.1.1"
set "PATH=%JAVA_HOME%\bin;C:\Program Files\nodejs;%PATH%"
cd /d "%~dp0..\backend"
call "C:\Users\TYLI\tools\gradle-9.9.1\bin\gradle.bat" test --no-daemon
exit /b %errorlevel%
