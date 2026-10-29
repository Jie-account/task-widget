param([string]$JavaHome = $env:JAVA_HOME, [string[]]$Tasks = @('testDebugUnitTest', 'assembleDebug', 'lintDebug'))
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
$env:JAVA_HOME = $JavaHome
$env:PATH = "$JavaHome/bin;$env:PATH"
$env:GRADLE_USER_HOME = Join-Path $projectRoot '.tools/gradle-user-home'
$gradle = Join-Path $projectRoot '.tools/gradle-9.1.0/bin/gradle.bat'
if (-not (Test-Path -LiteralPath $gradle)) { throw '请先运行 tools/prepare-android.ps1。' }
& $gradle @Tasks --console=plain --no-daemon
exit $LASTEXITCODE
