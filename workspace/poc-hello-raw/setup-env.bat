@echo off
REM Rewrites Maven/npm offline config for THIS folder.
REM Called by INSTALL.bat and run-*.bat, so moving the folder is fine.
if not defined DEV_HOME ( echo [ERROR] DEV_HOME is not set. Run setenv.bat first. & exit /b 1 )
> "%DEV_HOME%\maven\conf\settings.xml" echo ^<settings^>^<localRepository^>%DEV_HOME%\m2\repository^</localRepository^>^<offline^>true^</offline^>^</settings^>
if not exist "%DEV_HOME%\node\etc" mkdir "%DEV_HOME%\node\etc"
(
  echo cache=%DEV_HOME%\npm-cache
  echo offline=true
  echo audit=false
  echo fund=false
  echo update-notifier=false
) > "%DEV_HOME%\node\etc\npmrc"
if not exist "%DEV_HOME%\npm-cache" mkdir "%DEV_HOME%\npm-cache"
exit /b 0
