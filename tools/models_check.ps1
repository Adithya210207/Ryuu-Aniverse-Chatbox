# Ryuu Aniverse - model check (plan Phase 0).
# 1. Starts the app's PRIVATE Ollama server on 127.0.0.1:11436 (2 models allowed) if nothing answers there.
#    The normal Ollama on 11434 is never touched.
# 2. Creates aniverse-brain and aniverse-judge from models\Modelfile_* (thin layers, no extra disk).
# 3. Loads both at the same time, checks both stay in memory, runs one short reply from each.
# 4. Unloads both and stops the private server if this script started it.
#
# Run:  powershell -ExecutionPolicy Bypass -File tools\models_check.ps1          (keeps existing models)
#       powershell -ExecutionPolicy Bypass -File tools\models_check.ps1 -Recreate (re-creates them)
param([switch]$Recreate)

$ErrorActionPreference = 'Stop'
$Port   = 11436
$Url    = "http://127.0.0.1:$Port"
$Root   = Split-Path -Parent $PSScriptRoot
$Brain  = 'aniverse-brain'
$Judge  = 'aniverse-judge'

function Find-Ollama {
    $c = @("$env:LOCALAPPDATA\Programs\Ollama\ollama.exe", "$env:ProgramFiles\Ollama\ollama.exe")
    foreach ($p in $c) { if (Test-Path $p) { return $p } }
    $cmd = Get-Command ollama.exe -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    throw 'ollama.exe not found'
}
function Ping { try { Invoke-RestMethod "$Url/api/tags" -TimeoutSec 3 | Out-Null; return $true } catch { return $false } }
function Post($path, $obj, $timeout = 300) {
    $json  = $obj | ConvertTo-Json -Depth 10 -Compress
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
    return Invoke-RestMethod -Method Post "$Url$path" -Body $bytes -ContentType 'application/json; charset=utf-8' -TimeoutSec $timeout
}
function Has-Model($name) {
    $t = Invoke-RestMethod "$Url/api/tags"
    foreach ($m in $t.models) { if ($m.name -eq $name -or $m.name -eq "$name`:latest") { return $true } }
    return $false
}
function Loaded { (Invoke-RestMethod "$Url/api/ps").models }

$exe = Find-Ollama
$startedByUs = $null
try {
    # ---------------------------------------------------------------- 1. private server
    if (Ping) {
        Write-Host "[server] something already answers on $Port; using it (will not stop it)."
    } else {
        $models = $env:OLLAMA_MODELS
        if (-not $models) { $models = [Environment]::GetEnvironmentVariable('OLLAMA_MODELS', 'User') }
        $psi = New-Object System.Diagnostics.ProcessStartInfo
        $psi.FileName = $exe; $psi.Arguments = 'serve'
        $psi.UseShellExecute = $false; $psi.CreateNoWindow = $true
        $psi.RedirectStandardOutput = $true; $psi.RedirectStandardError = $true
        $psi.EnvironmentVariables['OLLAMA_HOST'] = "127.0.0.1:$Port"
        $psi.EnvironmentVariables['OLLAMA_MAX_LOADED_MODELS'] = '2'
        if ($models) { $psi.EnvironmentVariables['OLLAMA_MODELS'] = $models }
        $startedByUs = [System.Diagnostics.Process]::Start($psi)
        # drain output so the pipe never fills up
        $null = $startedByUs.StandardOutput.ReadToEndAsync(); $null = $startedByUs.StandardError.ReadToEndAsync()
        for ($k = 0; $k -lt 30 -and -not (Ping); $k++) { Start-Sleep -Seconds 1 }
        if (-not (Ping)) { throw "private server did not start on $Port" }
        Write-Host "[server] started private ollama serve on $Port (pid $($startedByUs.Id)), models folder: $models"
    }

    # ---------------------------------------------------------------- 2. models
    $env:OLLAMA_HOST = "127.0.0.1:$Port"
    foreach ($pair in @(@($Brain, 'Modelfile_brain'), @($Judge, 'Modelfile_judge'))) {
        $name = $pair[0]; $file = Join-Path $Root "models\$($pair[1])"
        if ($Recreate -or -not (Has-Model $name)) {
            # ollama prints progress on stderr; PowerShell 5.1 would treat that as an error
            $old = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
            & cmd /c "`"$exe`" create $name -f `"$file`" >nul 2>&1"
            $ErrorActionPreference = $old
            if (-not (Has-Model $name)) { throw "could not create $name" }
            Write-Host "[create] $name created from $($pair[1])"
        } else { Write-Host "[create] $name already exists" }
    }

    # ---------------------------------------------------------------- 3. load both
    $sw = [Diagnostics.Stopwatch]::StartNew()
    Post '/api/generate' @{ model = $Brain; keep_alive = '10m' } | Out-Null
    $tBrain = $sw.Elapsed.TotalSeconds; $sw.Restart()
    Post '/api/generate' @{ model = $Judge; keep_alive = '10m' } | Out-Null
    $tJudge = $sw.Elapsed.TotalSeconds
    Write-Host ("[load] brain {0:N1} s, judge {1:N1} s" -f $tBrain, $tJudge)

    $ps = Loaded
    $names = @($ps | ForEach-Object { $_.name })
    foreach ($m in $ps) {
        Write-Host ("[memory] {0,-24} total {1,5:N2} GB  on GPU {2,5:N2} GB  ctx {3}" -f $m.name, ($m.size / 1GB), ($m.size_vram / 1GB), $m.context_length)
    }
    $bothIn = ($names -match "^$Brain") -and ($names -match "^$Judge")
    if (-not $bothIn) { throw "both models are NOT in memory together: $($names -join ', ')" }
    Write-Host "[load] OK: both models are in memory together"

    # ---------------------------------------------------------------- 4. one reply each
    $sys = 'You are Naruto Uzumaki from Naruto, the Seventh Hokage. You are loud, warm and never give up. ' +
           'Write only your spoken words, one or two short sentences. Keep everything PG-13.'
    $sw.Restart()
    $r = Post '/api/chat' @{ model = $Brain; stream = $false; keep_alive = '10m'
        messages = @(@{ role = 'system'; content = $sys }, @{ role = 'user'; content = 'Hey Naruto! Long day at the office?' })
        options = @{ num_predict = 80 } }
    $tok = $r.eval_count; $genS = $r.eval_duration / 1e9
    Write-Host ("[brain] {0:N1} s total, {1} tokens at {2:N1} tok/s" -f $sw.Elapsed.TotalSeconds, $tok, ($tok / [math]::Max($genS, 0.001)))
    Write-Host "[brain] reply: $($r.message.content.Trim())"

    $sw.Restart()
    $j = Post '/api/chat' @{ model = $Judge; stream = $false; keep_alive = '10m'
        messages = @(
            @{ role = 'system'; content = 'You check if a fictional character stayed in character. Answer BREAK if the reply talks like an AI or assistant, mentions being an AI, a model, a prompt or roleplay, gives a list of tips, writes code, or sounds like customer service. Otherwise answer STAY. One word only.' },
            @{ role = 'user'; content = "Character: Naruto Uzumaki from Naruto.`nReply: `"As an AI language model, I can't attend the office.`"" })
        options = @{ temperature = 0; num_predict = 4 } }
    Write-Host ("[judge] {0:N1} s, answer: {1}  (expected BREAK)" -f $sw.Elapsed.TotalSeconds, $j.message.content.Trim())

    $after = @(Loaded | ForEach-Object { $_.name })
    if (($after -match "^$Brain") -and ($after -match "^$Judge")) { Write-Host '[result] PASS: brain + judge loaded together and both answered' }
    else { Write-Host "[result] FAIL: a model was pushed out: $($after -join ', ')" }
}
finally {
    # ---------------------------------------------------------------- cleanup
    try {
        if (Ping) {
            Post '/api/generate' @{ model = $Brain; keep_alive = 0 } 30 | Out-Null
            Post '/api/generate' @{ model = $Judge; keep_alive = 0 } 30 | Out-Null
            Write-Host '[cleanup] unloaded both models'
        }
    } catch {}
    if ($startedByUs) {
        try { Get-CimInstance Win32_Process -Filter "ParentProcessId=$($startedByUs.Id)" | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue } } catch {}
        try { $startedByUs.Kill() } catch {}
        Write-Host "[cleanup] stopped the private server on $Port"
    }
}
