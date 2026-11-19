@echo off
setlocal EnableExtensions
REM ============================================================
REM  Hello bundle installer - React + Spring Boot + PostgreSQL
REM  Installs everything on a bare Windows PC, no internet:
REM  JDK 21 / Maven 3.9.16 / Node.js 24.22.2 / PostgreSQL 18.6
REM  Tools are shipped pre-extracted. This script checks them,
REM  writes Maven/npm offline config, and initializes + starts
REM  the bundled PostgreSQL. Safe to run again.
REM  The folder path must not contain spaces.
REM ============================================================
call "%~dp0setenv.bat"
if not defined DEV_HOME ( echo [ERROR] setenv.bat failed & goto :fail )
call "%~dp0setup-env.bat"
echo [1/4] Checking bundled tools ...
"%DEV_HOME%\jdk\bin\java.exe" -version 2>&1 | findstr /C:"version"
if errorlevel 1 goto :fail
call mvn -v 2>&1 | findstr /C:"Apache Maven"
if errorlevel 1 goto :fail
call node -v
if errorlevel 1 goto :fail
echo [2/4] Checking offline repositories ...
if not exist "%DEV_HOME%\m2\repository" ( echo [ERROR] m2\repository missing & goto :fail )
if not exist "%DEV_HOME%\app\frontend\node_modules" ( echo [ERROR] app\frontend\node_modules missing & goto :fail )
echo       m2 repository : %DEV_HOME%\m2\repository
echo       node_modules  : %DEV_HOME%\app\frontend\node_modules
echo [3/4] Maven/npm offline config written.
echo [4/4] PostgreSQL ...
if not exist "%DEV_HOME%\pgsql\bin\pg_ctl.exe" ( echo [ERROR] pgsql\bin\pg_ctl.exe missing & goto :fail )
if not exist "%PGDATA%\PG_VERSION" (
  echo       Initializing data directory ...
  echo postgres> "%TEMP%\pgpw.txt"
  "%DEV_HOME%\pgsql\bin\initdb.exe" -D "%PGDATA%" -U postgres -A password --pwfile="%TEMP%\pgpw.txt" -E UTF8 --locale=C
  if errorlevel 1 ( del "%TEMP%\pgpw.txt" 2>nul & echo [ERROR] initdb failed & goto :fail )
  del "%TEMP%\pgpw.txt"
  call "%~dp0start-db.bat"
  if errorlevel 1 goto :fail
  set "PGPASSWORD=postgres"
  "%DEV_HOME%\pgsql\bin\psql.exe" -h localhost -U postgres -c "CREATE ROLE app LOGIN PASSWORD 'app';"
  "%DEV_HOME%\pgsql\bin\psql.exe" -h localhost -U postgres -c "CREATE DATABASE app OWNER app;"
)
call "%~dp0start-db.bat"
if errorlevel 1 goto :fail
echo Done.
echo.
echo  ============================================================
echo   INSTALL COMPLETE  (%DEV_HOME%)
echo   1) start-db.bat         PostgreSQL  : localhost:5432 (postgres/postgres, app/app)
echo   2) app\run-backend.bat   Spring Boot : http://127.0.0.1:8081
echo   3) app\run-frontend.bat  React/Vite  : http://127.0.0.1:5174
echo   Open in browser: http://127.0.0.1:5174
echo   Stop the DB with stop-db.bat.
echo  ============================================================
if defined DEVBUNDLE_UNATTENDED exit /b 0
pause
exit /b 0
:fail
echo.
echo  INSTALL FAILED - fix the problem above and run INSTALL.bat again.
if defined DEVBUNDLE_UNATTENDED exit /b 1
pause
exit /b 1
