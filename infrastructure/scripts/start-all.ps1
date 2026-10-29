# PowerShell script to start entire Railbound local stack (MongoDB RS on D:, Backend, Frontend)
$ErrorActionPreference = "Stop"

$ProjectRoot = "D:\MyProjects\railbound-asset-portal"

Write-Host "=========================================================="
Write-Host "  STARTING RAILBOUND BATTLE ROYALE LOCAL STACK (D: DRIVE) "
Write-Host "=========================================================="

# 1. MongoDB Replica Set on port 27018
Write-Host "`n[1/3] Ensuring dedicated MongoDB replica set on port 27018..."
& "$ProjectRoot\infrastructure\scripts\start-mongo-rs.ps1"

# 2. Spring Boot Backend on port 8080
Write-Host "`n[2/3] Starting Spring Boot modular monolith..."
Start-Process -FilePath "cmd.exe" -ArgumentList "/c cd /d $ProjectRoot\backend && mvn spring-boot:run" -WindowStyle Minimized
Write-Host "Spring Boot backend launching on http://localhost:8080"

# 3. Next.js Frontend on port 3000
Write-Host "`n[3/3] Starting Next.js minimalist web portal..."
Start-Process -FilePath "cmd.exe" -ArgumentList "/c cd /d $ProjectRoot\frontend && npm run dev" -WindowStyle Minimized
Write-Host "Next.js frontend launching on http://localhost:3000"

Write-Host "`nAll services triggered."
Write-Host "Player Portal:    http://localhost:3000"
Write-Host "Admin Console:    http://localhost:3000/admin"
Write-Host "Backend API:      http://localhost:8080"
Write-Host "Swagger UI:       http://localhost:8080/swagger-ui.html"
