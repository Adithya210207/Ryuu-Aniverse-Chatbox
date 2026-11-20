@echo off
rem Builds the self-contained release in dist\ (see release.ps1)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0release.ps1"
if errorlevel 1 (echo RELEASE FAILED & pause & exit /b 1)
pause
