param([switch]$CloudOnly, [switch]$NoWait)
$ErrorActionPreference = 'Stop'
$env:PYTHONUTF8 = '1'
if (-not (Get-Command python -ErrorAction SilentlyContinue)) {
    throw 'Python 3 is required. Install Python, Git and GitHub CLI, then run gh auth login.'
}
$buildArgs = @((Join-Path $PSScriptRoot 'scripts/build_all.py'))
if ($CloudOnly) { $buildArgs += '--cloud-only' }
if ($NoWait) { $buildArgs += '--no-wait' }
& python @buildArgs
exit $LASTEXITCODE
