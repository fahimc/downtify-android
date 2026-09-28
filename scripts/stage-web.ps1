$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$source = Join-Path $repo 'web\frontend\dist'
$destination = Join-Path $repo 'app\src\main\assets'
if (-not (Test-Path (Join-Path $source 'index.html'))) { throw 'Frontend dist is missing. Run npm --prefix web\frontend run build first.' }
if (Test-Path $destination) { Remove-Item -LiteralPath $destination -Recurse -Force }
New-Item -ItemType Directory -Path $destination -Force | Out-Null
Copy-Item -Path (Join-Path $source '*') -Destination $destination -Recurse -Force
Write-Host "Bundled web UI staged at $destination"
