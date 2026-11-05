# PowerShell script to start dedicated single-node MongoDB replica set on drive D:
$ErrorActionPreference = "Stop"

$ProjectRoot = "D:\MyProjects\railbound-asset-portal"
$DbPath = "$ProjectRoot\local-data\mongo"
$LogPath = "$ProjectRoot\local-logs\mongod.log"
$Port = 27018
$ReplSetName = "rs0"
$MongoBin = "C:\Program Files\MongoDB\Server\7.0\bin\mongod.exe"

if (-not (Test-Path $DbPath)) {
    New-Item -ItemType Directory -Path $DbPath -Force | Out-Null
}
if (-not (Test-Path (Split-Path $LogPath))) {
    New-Item -ItemType Directory -Path (Split-Path $LogPath) -Force | Out-Null
}

Write-Host "[MongoDB RS] Checking if mongod is already running on port $Port..."
$testConn = Test-NetConnection -ComputerName 127.0.0.1 -Port $Port -WarningAction SilentlyContinue
if ($testConn.TcpTestSucceeded) {
    Write-Host "[MongoDB RS] mongod is already listening on port $Port."
} else {
    Write-Host "[MongoDB RS] Starting mongod on port $Port with replica set '$ReplSetName' and dbPath '$DbPath'..."
    Start-Process -FilePath $MongoBin -ArgumentList "--dbpath `"$DbPath`" --port $Port --replSet $ReplSetName --bind_ip 127.0.0.1 --logpath `"$LogPath`" --logappend"
    Start-Sleep -Seconds 3
    Write-Host "[MongoDB RS] mongod started."
}

# One-time replica set initiation (idempotent). Without it the backend cannot start:
# the driver reports REPLICA_SET_GHOST and times out waiting for a primary.
Write-Host "[MongoDB RS] Ensuring replica set '$ReplSetName' is initiated..."
$mongosh = Get-Command mongosh -ErrorAction SilentlyContinue
if ($mongosh) {
    $initScript = "try { rs.status().ok } catch (e) { rs.initiate({_id:'$ReplSetName',members:[{_id:0,host:'127.0.0.1:$Port'}]}).ok }"
    & $mongosh.Source --quiet --port $Port --eval $initScript | Out-Host
    Start-Sleep -Seconds 5
} else {
    Write-Host "[MongoDB RS] mongosh was not found on PATH, so the replica set was not initiated automatically."
    Write-Host "             Run this once (see docs/PAYHERE_SETUP.md or the project README):"
    Write-Host "             mongosh --port $Port --eval `"rs.initiate({_id:'$ReplSetName',members:[{_id:0,host:'127.0.0.1:$Port'}]})`""
}
