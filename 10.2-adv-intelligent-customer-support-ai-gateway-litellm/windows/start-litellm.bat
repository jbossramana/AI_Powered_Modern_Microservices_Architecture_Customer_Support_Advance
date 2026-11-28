
@echo off
setlocal

cd /d "%~dp0.."

echo Starting LiteLLM...

if not exist "litellm\config.yaml" (
    echo ERROR: litellm\config.yaml not found.
    pause
    exit /b 1
)

if "%LITELLM_MASTER_KEY%"=="" set "LITELLM_MASTER_KEY=sk-local-secret"

"D:\Python313\Scripts\litellm.exe" --config "litellm\config.yaml" --host 127.0.0.1 --port 4000

pause
