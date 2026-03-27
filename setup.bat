@echo off
REM ─── Glowzi Backend — Setup Script ────────────────────────
REM Run this once to set up the project structure
REM ──────────────────────────────────────────────────────────

echo [1/3] Creating .github/workflows directory...
mkdir ".github\workflows" 2>nul

echo [2/3] Copying CI/CD workflow files...
copy "infra\identity-ci.yml" ".github\workflows\identity-ci.yml"
copy "infra\identity-cd.yml" ".github\workflows\identity-cd.yml"

echo [3/3] Done!
echo.
echo Workflow files are now in .github/workflows/
echo You can now push to GitHub and the pipelines will activate.
echo.
echo ─── Next: Run the service locally ───
echo.
echo   Step 1: Start databases
echo     cd infra ^&^& docker compose up -d
echo.
echo   Step 2: Run identity service
echo     cd glowzi-identity-service ^&^& mvnw.cmd spring-boot:run
echo.
echo   Step 3: Test the API
echo     curl -X POST http://localhost:8081/auth/register -H "Content-Type: application/json" -d "{\"fullName\":\"Ahmed Ali\",\"phone\":\"+966501234567\",\"password\":\"Pass123\",\"role\":\"CUSTOMER\"}"
echo.
pause
