@echo off
title POC hello dev shell (jdk/maven/node/pgsql on PATH)
call "%~dp0setenv.bat"
call "%~dp0setup-env.bat"
cd /d "%~dp0"
cmd /k echo POC hello dev shell ready. java, mvn, node, psql use this folder only.
