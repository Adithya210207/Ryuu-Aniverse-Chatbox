# Builds the self-contained release:  dist\Ryuu Aniverse v2\  (+ dist\Ryuu-Aniverse-v2.zip)
# Run:  powershell -ExecutionPolicy Bypass -File release.ps1        (or double-click release.bat)
# The release folder holds the exe + its own Java, the knowledge and portraits (content\), the model setup and the docs.
# The user's chats and settings go to %LOCALAPPDATA%\Ryuu Aniverse, so the folder can sit anywhere (even a read-only one).
$ErrorActionPreference = 'Continue'   # native tools (javac) print notes on stderr; failures are caught by Check()
Set-Location $PSScriptRoot
$Name = 'Ryuu Aniverse v2'
$FX   = 'lib\javafx-sdk-26.0.1'
$Dist = Join-Path $PSScriptRoot 'dist'
$Out  = Join-Path $Dist $Name
$Zip  = Join-Path $Dist 'Ryuu-Aniverse-v2.zip'
$Work = Join-Path $env:TEMP 'aniverse_release_build'

function Step($t) { Write-Host "== $t" -ForegroundColor Magenta }
function Check($what) { if ($LASTEXITCODE -ne 0) { Write-Host "$what failed (exit $LASTEXITCODE)" -ForegroundColor Red; exit 1 } }

Step 'Compiling'
if (Test-Path $Work) { Remove-Item $Work -Recurse -Force }
New-Item -ItemType Directory -Force "$Work\classes", "$Work\input\fx\lib", "$Work\input\fx\bin" | Out-Null
$src = Get-ChildItem src\aniverse\*.java | ForEach-Object { $_.FullName }
javac --module-path "$FX\lib" --add-modules javafx.controls -encoding UTF-8 -d "$Work\classes" $src 2>&1 | Where-Object { $_ -notmatch '^Note:' } | Out-Host
Check 'javac'
jar --create --file "$Work\input\aniverse.jar" -C "$Work\classes" .
Check 'jar'
foreach ($j in 'javafx.base', 'javafx.controls', 'javafx.graphics') { Copy-Item "$FX\lib\$j.jar" "$Work\input\fx\lib" }
Copy-Item "$FX\bin\*.dll" "$Work\input\fx\bin"
foreach ($d in 'jfxwebkit', 'gstreamer-lite', 'glib-lite', 'jfxmedia', 'fxplugins') { Remove-Item "$Work\input\fx\bin\$d.dll" -ErrorAction SilentlyContinue }

Step 'Packaging the app (about a minute)'
if (Test-Path $Out) { Remove-Item $Out -Recurse -Force }
New-Item -ItemType Directory -Force $Dist | Out-Null
$icon = @()
if (Test-Path 'assets\app.ico') { $icon = @('--icon', 'assets\app.ico') }
jpackage --type app-image --name $Name --dest $Dist --input "$Work\input" --main-jar aniverse.jar --main-class aniverse.Main @icon `
  --add-modules java.se,jdk.unsupported,jdk.charsets `
  --java-options '--module-path=$APPDIR\fx\lib' --java-options '--add-modules=javafx.controls' `
  --java-options '--enable-native-access=javafx.graphics' `
  --java-options '-Daniverse.home=$APPDIR\..\content' --java-options '-Daniverse.packaged=true' `
  --java-options '-Xmx1500m'
Check 'jpackage'
# JavaFX runs from the module path: take its jars off the classpath jpackage added them to
$cfg = Join-Path $Out "app\$Name.cfg"
(Get-Content $cfg) | Where-Object { $_ -notlike 'app.classpath=$APPDIR\fx\*' } | Set-Content $cfg

Step 'Adding content, setup and docs'
$content = Join-Path $Out 'content'
New-Item -ItemType Directory -Force "$content\knowledge", "$content\assets", "$Out\models", "$Out\tools" | Out-Null
foreach ($sub in 'characters', 'series', 'global') { Copy-Item "knowledge\$sub" "$content\knowledge\$sub" -Recurse }
Copy-Item 'knowledge\roster.json' "$content\knowledge\"
Copy-Item 'assets\*' "$content\assets" -Recurse
Copy-Item 'models\Modelfile_brain', 'models\Modelfile_judge' "$Out\models\"
Copy-Item 'tools\setup_models.ps1' "$Out\tools\"
Copy-Item 'tools\release_README.txt' "$Out\README.txt"
Copy-Item 'tools\release_NOTICE.txt' "$Out\NOTICE.txt"
$bat = "@echo off`r`ntitle Ryuu Aniverse - model setup`r`npowershell -NoProfile -ExecutionPolicy Bypass -File `"%~dp0tools\setup_models.ps1`"`r`necho.`r`npause`r`n"
[IO.File]::WriteAllText((Join-Path $Out 'Setup models.bat'), $bat, (New-Object Text.UTF8Encoding $false))
# the .md cheat sheets are for people, not the app
Get-ChildItem "$content\knowledge\characters" -Filter *.md | Remove-Item

Step 'Zipping'
if (Test-Path $Zip) { Remove-Item $Zip -Force }
Compress-Archive -Path $Out -DestinationPath $Zip -CompressionLevel Optimal
Remove-Item $Work -Recurse -Force

$mb = [math]::Round(((Get-ChildItem $Out -Recurse | Measure-Object Length -Sum).Sum) / 1MB)
$zmb = [math]::Round((Get-Item $Zip).Length / 1MB)
Write-Host ''
Write-Host "Done: $Out  ($mb MB)" -ForegroundColor Green
Write-Host "Zip:  $Zip  ($zmb MB)" -ForegroundColor Green
