@echo off
REM Stops the bundled PostgreSQL (data in pgdata is kept).
call "%~dp0setenv.bat"
pg_ctl stop -D "%PGDATA%" -m fast -w -t 30
if errorlevel 1 pg_ctl stop -D "%PGDATA%" -m immediate
exit /b 0
