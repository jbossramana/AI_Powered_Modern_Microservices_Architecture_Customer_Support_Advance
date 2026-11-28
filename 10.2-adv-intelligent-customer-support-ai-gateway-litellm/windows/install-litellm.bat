@echo off
setlocal
echo Checking Python...
py --version
if errorlevel 1 (
  echo ERROR: Python 3.10+ is required. Install from https://www.python.org/downloads/windows/
  echo During setup, enable "Add Python to PATH".
  exit /b 1
)

if not exist .venv\Scripts\python.exe (
  echo Creating local Python virtual environment...
  py -m venv .venv
  if errorlevel 1 exit /b 1
)

echo Upgrading pip...
.venv\Scripts\python.exe -m pip install --upgrade pip
if errorlevel 1 exit /b 1

echo Installing LiteLLM Proxy...
.venv\Scripts\python.exe -m pip install "litellm[proxy]"
if errorlevel 1 exit /b 1

echo.
echo LiteLLM installation completed.
echo Start it with: windows\start-litellm.bat
endlocal
