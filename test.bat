@echo off
call "%~dp0env.bat" || (pause & exit /b 1)
cd /d "%~dp0server"
call gradle test --console=plain
echo.
echo ==== exit code: %ERRORLEVEL% ====
pause
