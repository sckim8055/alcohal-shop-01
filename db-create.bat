@echo off
rem Creates the "sool" account and the sool / sool_test databases. Run once.
rem Usage: db-create.bat [admin account]   (default: postgres)
call "%~dp0env.bat" || (pause & exit /b 1)
set "PSQL=%WORKS%\tools\pgsql16\bin\psql.exe"
if not exist "%PSQL%" set "PSQL=psql"
set "PGADMIN=%~1"
if "%PGADMIN%"=="" set "PGADMIN=postgres"
echo Connecting to localhost:5433 as "%PGADMIN%". At the end, type a new password for the "sool" account twice.
"%PSQL%" -h localhost -p 5433 -U "%PGADMIN%" -d postgres -f "%~dp0db\create-db.sql"
echo.
echo ==== exit code: %ERRORLEVEL% ====
echo Next: copy db.local.bat.example to db.local.bat and type that password in it.
pause
