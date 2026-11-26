@echo off
call "%~dp0env.bat" || (pause & exit /b 1)
set "npm_config_cache=%WORKS%\_npm-cache"
set "NEXT_TELEMETRY_DISABLED=1"
cd /d "%~dp0web"
if not exist node_modules (
  echo Installing packages. This runs only once and takes a few minutes.
  call npm install --no-audit --no-fund || (pause & exit /b 1)
)
echo Web starts at http://localhost:3105  (stop: Ctrl+C)
echo The API server must be running too: run.bat
call npm run dev
pause
