param([string[]]$Tasks = @(':core:test', ':app:testDebugUnitTest', ':app:assembleDebug', ':app:lintDebug'), [switch]$Connected)
$ErrorActionPreference = 'Stop'
$projectRootForBuild = $PSScriptRoot
$buildRootForTask = $projectRootForBuild
if ($projectRootForBuild -match '[^\x00-\x7F]') {
    $driveRootForBuild = [System.IO.Path]::GetPathRoot($projectRootForBuild)
    $buildRootForTask = Join-Path $driveRootForBuild 'sixmodel-build'
    if (Test-Path -LiteralPath $buildRootForTask) {
        $existingAliasForBuild = Get-Item -LiteralPath $buildRootForTask
        if ($existingAliasForBuild.LinkType -ne 'Junction' -or $existingAliasForBuild.Target -ne $projectRootForBuild) { throw "Existing build alias does not point to this project: $buildRootForTask" }
    } else {
        New-Item -ItemType Junction -Path $buildRootForTask -Target $projectRootForBuild | Out-Null
    }
}
$bundledJavaForTask = Join-Path $buildRootForTask '.tools\java\jdk-17.0.16+8'
if (Test-Path -LiteralPath $bundledJavaForTask) { $env:JAVA_HOME = $bundledJavaForTask }
if (-not $env:JAVA_HOME) { throw 'JAVA_HOME must point to JDK 17.' }
$sdkForBuild = Join-Path $buildRootForTask '.tools\sdk'
if (Test-Path -LiteralPath $sdkForBuild) {
    $env:ANDROID_HOME = $sdkForBuild
    Set-Content -LiteralPath (Join-Path $projectRootForBuild 'local.properties') -Value ('sdk.dir=' + $sdkForBuild.Replace('\','/')) -Encoding ascii
}
if ($Connected) { $Tasks += @(':data:connectedDebugAndroidTest', ':app:connectedDebugAndroidTest') }
Push-Location $buildRootForTask
try {
    & '.\gradlew.bat' @Tasks --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed: $LASTEXITCODE" }
} finally { Pop-Location }
