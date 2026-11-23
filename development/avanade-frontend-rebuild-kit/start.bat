@echo off
setlocal
where py >nul 2>nul
if %ERRORLEVEL% EQU 0 (
  py -3 "%~dp0run.py" --project "%CD%" %*
) else (
  python "%~dp0run.py" --project "%CD%" %*
)
