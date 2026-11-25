@echo off
call "%~dp0env.bat" || (pause & exit /b 1)
if not defined SOOL_DB_PASSWORD echo [WARN] SOOL_DB_PASSWORD is not set. Copy db.local.bat.example to db.local.bat and type the password.
cd /d "%~dp0server"
echo Server starts at http://localhost:8080/api/health  (stop: Ctrl+C)
call gradle bootRun --console=plain
pause
