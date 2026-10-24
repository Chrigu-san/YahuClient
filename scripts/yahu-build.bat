@echo off
rem Builds Yahu Client locally (Release) and installs it into .\install
rem Local secrets (Microsoft client ID, CurseForge key) live in CMakeUserPresets.json, which is not committed.

setlocal
call "C:\Program Files (x86)\Microsoft Visual Studio\18\BuildTools\VC\Auxiliary\Build\vcvars64.bat" >nul || exit /b 1

for /d %%D in ("%LOCALAPPDATA%\Microsoft\WinGet\Packages\Kitware.CMake_*") do for /d %%E in ("%%D\cmake-*") do set "PATH=%%E\bin;%PATH%"
for /d %%D in ("%LOCALAPPDATA%\Microsoft\WinGet\Packages\Ninja-build.Ninja_*") do set "PATH=%%D;%PATH%"
set "JAVA_HOME=C:\Program Files\Zulu\zulu-17"
set "PATH=%JAVA_HOME%\bin;C:\Qt\6.11.2\msvc2022_64\bin;%PATH%"

cd /d "%~dp0.."
cmake --preset yahu || exit /b 1
cmake --build --preset yahu || exit /b 1
cmake --install build --config Release --prefix install || exit /b 1
echo.
echo Fertig: %CD%\install
