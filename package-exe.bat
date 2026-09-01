@echo off
setlocal

set JAR_NAME=prediction_client_3-0.0.1-SNAPSHOT.jar
set APP_VERSION=0.0.1
set JPACKAGE_INPUT=target\jpackage-input
set DIST_DIR=target\dist

echo Building jar with Maven...
call mvnw.cmd -q clean package
if errorlevel 1 (
    echo Maven build failed.
    exit /b 1
)

echo Preparing jpackage input...
if exist "%JPACKAGE_INPUT%" rmdir /s /q "%JPACKAGE_INPUT%"
mkdir "%JPACKAGE_INPUT%"
copy /y "target\%JAR_NAME%" "%JPACKAGE_INPUT%\" >nul

if exist "%DIST_DIR%" rmdir /s /q "%DIST_DIR%"

echo Running jpackage...
jpackage ^
  --type app-image ^
  --input "%JPACKAGE_INPUT%" ^
  --dest "%DIST_DIR%" ^
  --name PredictionClient3 ^
  --app-version %APP_VERSION% ^
  --main-jar %JAR_NAME% ^
  --icon icon.ico ^
  --add-modules ALL-MODULE-PATH
if errorlevel 1 (
    echo jpackage failed.
    exit /b 1
)

echo.
echo Done. Distribute the folder "%DIST_DIR%\PredictionClient3" - launch it via PredictionClient3.exe inside.
endlocal
