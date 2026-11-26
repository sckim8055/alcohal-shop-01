@echo off
rem Portable tool paths. All tools live next to this project folder (F:\works).
set "WORKS=%~dp0.."
set "JAVA_HOME=%WORKS%\jdk-25.0.5.1+2"
set "GRADLE_HOME=%WORKS%\gradle-9.9.2"
set "NODE_HOME=%WORKS%\node-v26.13.1-win-x64"
set "GRADLE_USER_HOME=%WORKS%\_gradle-cache"
if not exist "%JAVA_HOME%\bin\java.exe" (echo [ERROR] JDK not found: %JAVA_HOME% & exit /b 1)
if not exist "%GRADLE_HOME%\bin\gradle.bat" (echo [ERROR] Gradle not found: %GRADLE_HOME% & exit /b 1)
if not exist "%NODE_HOME%\node.exe" (echo [ERROR] Node.js not found: %NODE_HOME% & exit /b 1)
set "PATH=%JAVA_HOME%\bin;%GRADLE_HOME%\bin;%NODE_HOME%;%PATH%"
rem Video compression: optional. Put ffmpeg.exe in F:\works\tools\ffmpeg\bin (or F:\works\ffmpeg\bin).
if exist "%WORKS%\ffmpeg\bin\ffmpeg.exe" set "PATH=%WORKS%\ffmpeg\bin;%PATH%"
if exist "%WORKS%\tools\ffmpeg\bin\ffmpeg.exe" set "PATH=%WORKS%\tools\ffmpeg\bin;%PATH%"
rem Database password and address: db.local.bat (copy db.local.bat.example).
if exist "%~dp0db.local.bat" call "%~dp0db.local.bat"
rem Show Korean text correctly in this console.
chcp 65001 >nul
set "GRADLE_OPTS=-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
exit /b 0
