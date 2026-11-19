@echo off
REM Starts the bundled PostgreSQL in the background (no window). Stop it with stop-db.bat.
call "%~dp0setenv.bat"
pg_isready -h localhost -p 5432 -q
if not errorlevel 1 (
  echo PostgreSQL already running on localhost:5432.
  exit /b 0
)
REM A postgres.exe that is alive but not answering is a broken leftover: stop it first.
tasklist /FI "IMAGENAME eq postgres.exe" 2>nul | find /I "postgres.exe" >nul
if not errorlevel 1 (
  pg_ctl stop -D "%PGDATA%" -m immediate >nul 2>&1
  ping -n 3 127.0.0.1 >nul
)
REM Remove a stale lock file left by a crash or forced shutdown (only if no postgres.exe is running).
if exist "%PGDATA%\postmaster.pid" (
  tasklist /FI "IMAGENAME eq postgres.exe" 2>nul | find /I "postgres.exe" >nul
  if errorlevel 1 del /f /q "%PGDATA%\postmaster.pid"
)
if not exist "%DEV_HOME%\logs" mkdir "%DEV_HOME%\logs"
REM Hidden console owned by postgres itself, so closing other windows cannot stop the DB.
powershell -NoProfile -ExecutionPolicy Bypass -Command "Start-Process -FilePath '%DEV_HOME%\pgsql\bin\pg_ctl.exe' -ArgumentList 'start','-D','%PGDATA%','-l','%DEV_HOME%\logs\postgres.log' -WindowStyle Hidden"
echo Waiting for PostgreSQL ...
for /L %%i in (1,1,60) do (
  pg_isready -h localhost -p 5432 -q && goto :up
  ping -n 2 127.0.0.1 >nul
)
echo [ERROR] PostgreSQL did not start. See %DEV_HOME%\logs\postgres.log
exit /b 1
:up
echo PostgreSQL is up (localhost:5432).
exit /b 0
