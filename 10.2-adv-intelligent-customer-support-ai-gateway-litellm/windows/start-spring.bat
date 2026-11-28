@echo off
setlocal
if "%LITELLM_BASE_URL%"=="" set "LITELLM_BASE_URL=http://localhost:4000"
if "%LITELLM_API_KEY%"=="" (
  if not "%LITELLM_MASTER_KEY%"=="" (
    set "LITELLM_API_KEY=%LITELLM_MASTER_KEY%"
  ) else (
    set "LITELLM_API_KEY=sk-local-development-change-this-key"
  )
)
if "%GATEWAY_API_KEY%"=="" set "GATEWAY_API_KEY=change-this-gateway-key"
if "%LITELLM_MODEL%"=="" set "LITELLM_MODEL=local-llama"
call mvn clean spring-boot:run
endlocal
