@echo off
REM ─── Glowzi — Start Infrastructure ─────────────────────────
echo.
echo ============================================
echo   Glowzi Backend — Starting Infrastructure
echo ============================================
echo.

REM Check Docker is running
docker info >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Docker is NOT running!
    echo.
    echo Please open Docker Desktop and wait until it's ready,
    echo then run this script again.
    echo.
    pause
    exit /b 1
)
echo [OK] Docker is running.

REM Stop any existing containers
echo.
echo [1/4] Stopping old containers...
docker compose down -v 2>nul

REM Remove old Keycloak container if stuck
echo [2/4] Cleaning up...
docker rm -f glowzi-keycloak 2>nul
docker rm -f glowzi-identity-db 2>nul
docker rm -f glowzi-catalog-db 2>nul
docker rm -f glowzi-booking-db 2>nul

REM Start services
echo [3/4] Starting services...
docker compose up -d

REM Wait for Keycloak
echo [4/4] Waiting for Keycloak to be ready (this can take 60-90 seconds)...
echo.
:WAIT_LOOP
timeout /t 5 /nobreak >nul
docker ps --filter "name=glowzi-keycloak" --filter "status=running" | findstr glowzi-keycloak >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo        Keycloak container not running yet... checking logs:
    docker logs --tail 5 glowzi-keycloak 2>&1
    echo.
    echo        Retrying in 5 seconds...
    goto WAIT_LOOP
)

REM Check if Keycloak is responding
curl -s -o nul -w "%%{http_code}" http://localhost:9090 | findstr /C:"200" >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo        Keycloak is starting up...
    goto WAIT_LOOP
)

echo.
echo ============================================
echo   All services are UP!
echo ============================================
echo.
echo   Keycloak:      http://localhost:9090
echo   Admin login:   admin / admin
echo.
echo   Identity DB:   localhost:5433
echo   Catalog  DB:   localhost:5434
echo   Booking  DB:   localhost:5435
echo.
pause
