param(
    [string]$Project = "D:\TafariCraft - Reborn Development\Traincraft",
    [string]$DestinationFolder = "$env:USERPROFILE\Desktop"
)

$ErrorActionPreference = "Stop"

if (!(Test-Path $Project)) {
    throw "Traincraft project not found: $Project"
}

$Stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$Stage = Join-Path $env:TEMP "Traincraft-PC-Move-$Stamp"
$Zip   = Join-Path $DestinationFolder "Traincraft-Current-Project-$Stamp.zip"

Write-Host ""
Write-Host "Traincraft PC handoff backup" -ForegroundColor Cyan
Write-Host "Source:      $Project"
Write-Host "Destination: $Zip"
Write-Host ""

Remove-Item $Stage -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $Stage -Force | Out-Null

# Copy the real project while skipping only disposable build/cache/log output.
# run\saves and run\config ARE intentionally retained so the test world/settings move too.
$xd = @(
    (Join-Path $Project ".gradle"),
    (Join-Path $Project "build"),
    (Join-Path $Project "run\logs"),
    (Join-Path $Project "run\crash-reports"),
    (Join-Path $Project "run\.mixin.out")
)

$RoboArgs = @(
    $Project,
    $Stage,
    "/E",
    "/COPY:DAT",
    "/DCOPY:DAT",
    "/R:1",
    "/W:1",
    "/NFL",
    "/NDL",
    "/NP",
    "/XD"
) + $xd

& robocopy @RoboArgs
$RoboCode = $LASTEXITCODE
if ($RoboCode -ge 8) {
    throw "Robocopy failed with exit code $RoboCode"
}

$Info = @"
Traincraft project PC handoff
Created: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss K")
Original project: $Project
Target: Minecraft 1.20.1 / Forge 47.3.22 / Java 17
Current active development: Step 7.2.1 multi-car corner stabilization
See the separate ChatGPT handoff kit for full milestone history.
"@
Set-Content -Path (Join-Path $Stage "PC-HANDOFF-INFO.txt") -Value $Info -Encoding UTF8

if (Test-Path $Zip) {
    Remove-Item $Zip -Force
}

Write-Host "Compressing project..." -ForegroundColor Yellow
Compress-Archive -Path (Join-Path $Stage "*") -DestinationPath $Zip -CompressionLevel Optimal

$Hash = (Get-FileHash $Zip -Algorithm SHA256).Hash

Write-Host ""
Write-Host "PROJECT BACKUP COMPLETE" -ForegroundColor Green
Write-Host "ZIP:    $Zip"
Write-Host "SHA256: $Hash" -ForegroundColor Cyan
Write-Host ""
Write-Host "Copy this ZIP plus Traincraft-PC-Handoff-Kit-2026-09-14.zip to the new PC."

Remove-Item $Stage -Recurse -Force -ErrorAction SilentlyContinue
