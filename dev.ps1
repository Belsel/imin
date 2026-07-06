# ImIn local development environment launcher

$repoRoot = $PSScriptRoot

# 1. Wake the database container
Write-Host "Starting imin-db container..."
docker start imin-db

# 2. Backend window
Write-Host "Starting backend..."
$backendDir = Join-Path $repoRoot "backend"
$backendCommand = @"
`$env:DATABASE_URL = 'jdbc:postgresql://localhost:5432/imin'
`$env:DATABASE_USERNAME = 'postgres'
`$env:DATABASE_PASSWORD = 'postgres'
`$env:JWT_SECRET = 'change-me-to-a-long-random-value'
`$env:GOOGLE_CLIENT_ID = 'placeholder-not-a-real-client-id'
`$env:GOOGLE_CLIENT_SECRET = 'placeholder-not-a-real-client-secret'
`$env:FRONTEND_URL = 'http://localhost:5173'
`$env:RESEND_API_KEY = ''
`$env:EMAIL_FROM_ADDRESS = 'no-reply@example.com'
`$env:ORS_API_KEY = ''
`$env:PORT = '8080'
Set-Location '$backendDir'
.\mvnw.cmd spring-boot:run
"@
Start-Process powershell -ArgumentList "-NoExit", "-Command", $backendCommand

# 3. Frontend window
Write-Host "Starting frontend..."
$frontendDir = Join-Path $repoRoot "frontend"
$frontendCommand = "Set-Location '$frontendDir'; npm run dev"
Start-Process powershell -ArgumentList "-NoExit", "-Command", $frontendCommand
