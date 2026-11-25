@echo off
call "%~dp0env.bat" || (pause & exit /b 1)
cd /d "%~dp0server"
echo Server starts at http://localhost:8080/api/health  (stop: Ctrl+C)
call gradle bootRun --console=plain
pause
