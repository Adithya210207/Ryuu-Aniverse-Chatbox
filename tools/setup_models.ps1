# Ryuu Aniverse - one-time model setup (end users).
# Installs the two local AI models the app needs, using the Ollama you already have installed:
#   aniverse-judge  = a thin layer on phi4-mini (about 2.5 GB download)
#   aniverse-brain  = a thin layer on Stheno 8B Q8_0 (about 8.5 GB download, or your own GGUF file)
# Run it by double-clicking "Setup models.bat". It is safe to run again: models that exist are kept.
# Optional: put your own L3-8B-Stheno-v3.2-Q8_0.gguf in the "models" folder next to this script's parent to skip the download.
$ErrorActionPreference = 'Continue'
$Root   = Split-Path -Parent $PSScriptRoot
$Models = Join-Path $Root 'models'
if (-not (Test-Path $Models)) { $Models = Join-Path $PSScriptRoot '..\models' }

function Find-Ollama {
    foreach ($p in @("$env:LOCALAPPDATA\Programs\Ollama\ollama.exe", "$env:ProgramFiles\Ollama\ollama.exe")) { if (Test-Path $p) { return $p } }
    $cmd = Get-Command ollama.exe -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    return $null
}
function Run($exe, [string[]]$a) { & $exe @a 2>&1 | ForEach-Object { "$_" } | Out-Host; return $LASTEXITCODE }
function Has($exe, $name) {
    $t = (& $exe list 2>$null | Out-String)
    return ($t -match "(?m)^$([regex]::Escape($name))(:latest)?\s")
}

Write-Host ''
Write-Host '=== Ryuu Aniverse: model setup ===' -ForegroundColor Magenta
$ollama = Find-Ollama
if (-not $ollama) {
    Write-Host 'Ollama is not installed. Install it first from https://ollama.com/download then run this again.' -ForegroundColor Yellow
    exit 1
}
& $ollama list 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host 'Ollama is installed but not running. Start the Ollama app (Start menu), wait a few seconds, then run this again.' -ForegroundColor Yellow
    exit 1
}

# 1. judge
if (Has $ollama 'aniverse-judge') { Write-Host 'aniverse-judge: already installed.' }
else {
    Write-Host 'Downloading phi4-mini (about 2.5 GB) ...'
    if ((Run $ollama @('pull', 'phi4-mini')) -ne 0) { Write-Host 'Could not download phi4-mini. Check your internet and run again.' -ForegroundColor Red; exit 2 }
    $mf = Join-Path $Models 'Modelfile_judge'
    if ((Run $ollama @('create', 'aniverse-judge', '-f', $mf)) -ne 0) { Write-Host 'Could not create aniverse-judge.' -ForegroundColor Red; exit 3 }
}

# 2. brain
if (Has $ollama 'aniverse-brain') { Write-Host 'aniverse-brain: already installed.' }
else {
    $gguf = Join-Path $Models 'L3-8B-Stheno-v3.2-Q8_0.gguf'
    $from = $null
    if (Test-Path $gguf) { $from = $gguf; Write-Host "Using your local file: $gguf" }
    else {
        $tag = 'hf.co/bartowski/L3-8B-Stheno-v3.2-GGUF:Q8_0'
        Write-Host 'Downloading Stheno 8B Q8_0 (about 8.5 GB, this takes a while) ...'
        Write-Host '(Model licence: CC BY-NC 4.0 - personal, non-commercial use. See NOTICE.txt.)'
        if ((Run $ollama @('pull', $tag)) -ne 0) { Write-Host 'Could not download the brain model. Check your internet and disk space (about 10 GB free) and run again.' -ForegroundColor Red; exit 4 }
        $from = $tag
    }
    $lines = Get-Content (Join-Path $Models 'Modelfile_brain') | ForEach-Object { if ($_ -match '^FROM\s') { "FROM $from" } else { $_ } }
    $tmp = Join-Path $env:TEMP 'aniverse_brain.Modelfile'
    Set-Content -Path $tmp -Value $lines -Encoding UTF8
    if ((Run $ollama @('create', 'aniverse-brain', '-f', $tmp)) -ne 0) { Write-Host 'Could not create aniverse-brain.' -ForegroundColor Red; exit 5 }
    Remove-Item $tmp -ErrorAction SilentlyContinue
}

Write-Host ''
Write-Host 'Done. Both models are installed. Start "Ryuu Aniverse" now.' -ForegroundColor Green
exit 0
