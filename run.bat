@echo off
cd /d "%~dp0"
set "MVN_CMD=C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.1\plugins\maven-plugin\lib\maven3\bin\mvn.cmd"
if exist "%MVN_CMD%" (
    "%MVN_CMD%" javafx:run
) else (
    mvn javafx:run
)
