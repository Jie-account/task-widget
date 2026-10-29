param([string]$JavaHome = $env:JAVA_HOME, [string]$ProxyUrl = $env:TASK_WIDGET_HTTPS_PROXY)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
if (-not (Test-Path -LiteralPath "$JavaHome/bin/java.exe")) { throw '请通过 -JavaHome 指定 JDK 17 或更新版本目录。' }
$env:JAVA_HOME = $JavaHome
$env:PATH = "$JavaHome/bin;$env:PATH"
$toolsRoot = Join-Path $projectRoot '.tools'
$sdkRoot = Join-Path $toolsRoot 'android-sdk'
New-Item -ItemType Directory -Force -Path "$toolsRoot/downloads", "$sdkRoot/cmdline-tools" | Out-Null
if ($ProxyUrl) {
    $proxyAddress = [uri]$ProxyUrl
    $env:TASK_WIDGET_HTTPS_PROXY = $ProxyUrl
    New-Item -ItemType Directory -Force -Path "$toolsRoot/gradle-user-home" | Out-Null
    $proxySettings = "systemProp.http.proxyHost=$($proxyAddress.Host)`nsystemProp.http.proxyPort=$($proxyAddress.Port)`nsystemProp.https.proxyHost=$($proxyAddress.Host)`nsystemProp.https.proxyPort=$($proxyAddress.Port)`nsystemProp.http.nonProxyHosts=localhost|127.*`n"
    [IO.File]::WriteAllText("$toolsRoot/gradle-user-home/gradle.properties", $proxySettings, [Text.UTF8Encoding]::new($false))
}

# 固定工具版本，校验值来自对应官方发布源。
function Get-Dependency([string]$Url, [string]$File) {
    if (-not (Test-Path -LiteralPath $File)) {
        & node tools/download.mjs $Url $File
        if ($LASTEXITCODE -ne 0) { throw "下载失败：$Url" }
    }
}
$cliZip = "$toolsRoot/downloads/commandlinetools-win-13114758_latest.zip"
if (-not (Test-Path -LiteralPath "$sdkRoot/cmdline-tools/19.0/bin/sdkmanager.bat")) {
    Get-Dependency 'https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip' $cliZip
    $repoXml = "$toolsRoot/android-repository.xml"
    Get-Dependency 'https://dl.google.com/android/repository/repository2-3.xml' $repoXml
    [xml]$repo = Get-Content -Raw -LiteralPath $repoXml
    $package = $repo.SelectNodes('//*[local-name()="remotePackage"]') | Where-Object { $_.path -eq 'cmdline-tools;19.0' }
    $archive = $package.archives.archive | Where-Object { $_.'host-os' -eq 'windows' }
    if (-not $archive.complete.checksum) { throw '官方清单中未找到固定版本的校验值。' }
    if ((Get-FileHash -Algorithm SHA1 -LiteralPath $cliZip).Hash.ToLower() -ne $archive.complete.checksum.InnerText) { throw '命令行工具校验失败。' }
    Expand-Archive -LiteralPath $cliZip -DestinationPath "$toolsRoot/cli-unpacked" -Force
    New-Item -ItemType Directory -Force -Path "$sdkRoot/cmdline-tools/19.0" | Out-Null
    Copy-Item -Path "$toolsRoot/cli-unpacked/cmdline-tools/*" -Destination "$sdkRoot/cmdline-tools/19.0" -Recurse -Force
}

# 复用本机已接受的 SDK 许可；新的许可由 sdkmanager 明确提示。
$existingLicenses = Join-Path $env:LOCALAPPDATA 'Android/Sdk/licenses'
if (Test-Path -LiteralPath $existingLicenses) {
    New-Item -ItemType Directory -Force -Path "$sdkRoot/licenses" | Out-Null
    Copy-Item -Path "$existingLicenses/*" -Destination "$sdkRoot/licenses" -Force
}
& "$sdkRoot/cmdline-tools/19.0/bin/sdkmanager.bat" "--sdk_root=$sdkRoot" 'platform-tools' 'platforms;android-35' 'build-tools;36.0.0'
if ($LASTEXITCODE -ne 0) { throw 'SDK 包安装失败，请查看上方 sdkmanager 输出。' }

$gradleZip = "$toolsRoot/downloads/gradle-9.1.0-bin.zip"
Get-Dependency 'https://downloads.gradle.org/distributions/gradle-9.1.0-bin.zip' $gradleZip
Get-Dependency 'https://downloads.gradle.org/distributions/gradle-9.1.0-bin.zip.sha256' "$gradleZip.sha256"
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $gradleZip).Hash.ToLower() -ne (Get-Content -Raw -LiteralPath "$gradleZip.sha256").Trim()) { throw 'Gradle 校验失败。' }
if (-not (Test-Path -LiteralPath "$toolsRoot/gradle-9.1.0/bin/gradle.bat")) { Expand-Archive -LiteralPath $gradleZip -DestinationPath $toolsRoot -Force }
# Java properties 中的 Windows 盘符冒号也需要转义。
$sdkProperty = 'sdk.dir=' + $sdkRoot.Replace('\', '/').Replace(':', '\:')
[IO.File]::WriteAllText((Join-Path $projectRoot 'local.properties'), "$sdkProperty`n", [Text.UTF8Encoding]::new($false))
Write-Output '本地构建依赖已就绪。使用 tools/build.ps1 构建。'
