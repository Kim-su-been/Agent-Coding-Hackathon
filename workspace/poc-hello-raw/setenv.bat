@echo off
for %%I in ("%~dp0.") do set "DEV_HOME=%%~fI"
set "JAVA_HOME=%DEV_HOME%\jdk"
set "MAVEN_HOME=%DEV_HOME%\maven"
set "PGDATA=%DEV_HOME%\pgdata"
set "MAVEN_ARGS=-s %DEV_HOME%\maven\conf\settings.xml"
set "npm_config_cache=%DEV_HOME%\npm-cache"
set "npm_config_offline=true"
set "npm_config_audit=false"
set "npm_config_fund=false"
set "npm_config_update_notifier=false"
set "PATH=%DEV_HOME%\jdk\bin;%DEV_HOME%\maven\bin;%DEV_HOME%\node;%DEV_HOME%\pgsql\bin;%PATH%"
exit /b 0
