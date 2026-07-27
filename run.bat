@echo off
setlocal
cd /d "%~dp0"

echo Checking whether Spring Boot is already running...
powershell -NoProfile -Command "try { $response = Invoke-WebRequest -UseBasicParsing -Uri 'http://localhost:8080/api/nifty50' -TimeoutSec 2; if ($response.StatusCode -eq 200) { exit 0 } else { exit 1 } } catch { exit 1 }"
if not errorlevel 1 (
    echo.
    echo The application is already running at http://localhost:8080
    echo API: http://localhost:8080/api/nifty50
    echo.
    pause
    exit /b 0
)

echo Starting MySQL and Redis...
docker compose up -d mysql redis
if errorlevel 1 (
    echo Failed to start Docker services. Make sure Docker Desktop is running.
    pause
    exit /b 1
)

echo Starting Spring Boot on http://localhost:8080 ...
call mvnw.cmd spring-boot:run

echo.
echo Spring Boot stopped or failed to start.
echo Check application-error.log or the console output above.
pause

endlocal
