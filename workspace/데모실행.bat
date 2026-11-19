@echo off
rem ============================================================
rem  outputhub one-click demo launcher
rem  - starts the packaged Spring Boot server (port 8080)
rem  - serves the built frontend and the API from the same server
rem  - opens http://localhost:8080 in the default browser
rem  - stop the demo: close the minimized "outputhub-demo" window
rem ============================================================
setlocal
set "ROOT=%~dp0"
set "BACKEND_DIR=%ROOT%Agent-Coding-Hackathon\backend"
set "CURL_EXE=%SystemRoot%\System32\curl.exe"
set "HTTP_CODE=000"

rem --- pick a Java 17+ runtime: JAVA_HOME, PATH, installed JDK, bundled JDK
set "JAVA_EXE="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
if not defined JAVA_EXE for %%P in (java.exe) do set "JAVA_EXE=%%~$PATH:P"
if not defined JAVA_EXE if exist "C:\Program Files\Java\jdk-27.0.1.1\bin\java.exe" set "JAVA_EXE=C:\Program Files\Java\jdk-27.0.1.1\bin\java.exe"
if not defined JAVA_EXE if exist "%ROOT%poc-hello-raw\jdk\bin\java.exe" set "JAVA_EXE=%ROOT%poc-hello-raw\jdk\bin\java.exe"
if not defined JAVA_EXE (
    echo [ERROR] Java 17+ runtime not found.
    echo         Install a JDK 17+ or keep the poc-hello-raw folder next to this file.
    pause
    exit /b 1
)

rem --- find the packaged demo jar (build once with tools\build-demo.cmd)
set "JAR_FILE="
for %%F in ("%BACKEND_DIR%\build\libs\*.jar") do set "JAR_FILE=%%~fF"
if not defined JAR_FILE (
    echo [ERROR] Demo jar not found in:
    echo         %BACKEND_DIR%\build\libs
    echo         Build it once with: Agent-Coding-Hackathon\tools\build-demo.cmd
    pause
    exit /b 1
)

rem --- check the demo front page (HTTP 200 only counts as running)
if not exist "%CURL_EXE%" goto START_SERVER
"%CURL_EXE%" -s -o NUL -w "%%{http_code}" http://localhost:8080/ > "%TEMP%\outputhub-http.txt" 2>NUL
set /p HTTP_CODE=<"%TEMP%\outputhub-http.txt"
:START_SERVER
if not "%HTTP_CODE%"=="200" goto LAUNCH
echo Demo server is already running. Opening browser...
start "" http://localhost:8080
exit /b 0

:LAUNCH
echo Starting outputhub demo server ... first start takes a few seconds.
cd /d "%BACKEND_DIR%"
start "outputhub-demo" /min "%JAVA_EXE%" -jar "%JAR_FILE%"

rem --- wait until the front page answers with HTTP 200, then open the browser
if not exist "%CURL_EXE%" (
    ping -n 16 127.0.0.1 >nul
    goto OPEN_BROWSER
)
set /a TRIES=0
:WAIT_SERVER
ping -n 3 127.0.0.1 >nul
"%CURL_EXE%" -s -o NUL -w "%%{http_code}" http://localhost:8080/ > "%TEMP%\outputhub-http.txt" 2>NUL
set /p HTTP_CODE=<"%TEMP%\outputhub-http.txt"
if "%HTTP_CODE%"=="200" goto OPEN_BROWSER
set /a TRIES+=1
if %TRIES% lss 30 goto WAIT_SERVER
echo [ERROR] Server did not respond within 60 seconds.
echo         Check the minimized "outputhub-demo" window for the error log.
pause
exit /b 1

:OPEN_BROWSER
start "" http://localhost:8080
echo Demo is ready: http://localhost:8080
echo To stop the demo, close the minimized "outputhub-demo" window.
ping -n 9 127.0.0.1 >nul
exit /b 0
