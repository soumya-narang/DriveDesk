# Compiles the project and starts the web UI at http://localhost:8090/
# Usage: .\run.ps1 [port] [-Empty] [-Reset]
#   First run seeds demo data unless -Empty is given. -Reset deletes data/ first, so the demo starts fresh and dated today.
param([int]$Port = 8090, [switch]$Empty, [switch]$Reset)

Set-Location $PSScriptRoot
if ($Reset -and (Test-Path data)) { Remove-Item -Recurse -Force data; Write-Host 'Saved data cleared.' }
if (Test-Path out) { Remove-Item -Recurse -Force out }
New-Item -ItemType Directory out | Out-Null

$sources = (Get-ChildItem -Recurse -Filter *.java src).FullName
javac -encoding UTF-8 -d out $sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$javaArgs = @($Port)
if ($Empty) { $javaArgs += '--empty' }
java -cp out app.WebApp @javaArgs
