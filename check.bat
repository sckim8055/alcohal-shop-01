@echo off
call "%~dp0env.bat" || (pause & exit /b 1)
echo ==== java ====
java -version
echo ==== gradle ====
call gradle -v
echo ==== node / npm ====
node -v
call npm -v
echo ==== ffmpeg (optional, for video compression) ====
where ffmpeg >nul 2>nul && (ffmpeg -version | findstr /b "ffmpeg version") || echo not installed
pause
