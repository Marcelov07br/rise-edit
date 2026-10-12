param([switch]$Release)
$ErrorActionPreference = 'Stop'
$riseRoot = Split-Path -Parent $PSScriptRoot
$riseJava = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.16.8-hotspot'
if (Test-Path -LiteralPath "$riseJava\bin\java.exe") { $env:JAVA_HOME = $riseJava }
New-Item -ItemType Directory -Path "$riseRoot\.tmp" -Force | Out-Null
$riseShortTemp = (& cmd /c "for %I in (`"$riseRoot\.tmp`") do @echo %~sI").Trim()
$env:JAVA_TOOL_OPTIONS = "-Djdk.net.unixdomain.tmpdir=$riseShortTemp"
$riseTask = if ($Release) { ':app:assembleRelease' } else { ':app:assembleDebug' }
$riseLint = if ($Release) { ':app:lintRelease' } else { ':app:lintDebug' }
& "$riseRoot\gradlew.bat" $riseTask $riseLint :core:compileTestKotlin --no-daemon
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
# Direct JUnit invocation avoids Java 17's argument-file encoding issue in paths with accents.
$riseCache = Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1'
$riseJUnit = (Get-ChildItem "$riseCache\junit\junit\4.13.2" -Recurse -Filter 'junit-4.13.2.jar' | Select-Object -First 1).FullName
$riseHamcrest = (Get-ChildItem "$riseCache\org.hamcrest\hamcrest-core\1.3" -Recurse -Filter 'hamcrest-core-1.3.jar' | Select-Object -First 1).FullName
$riseKotlin = (Get-ChildItem "$riseCache\org.jetbrains.kotlin\kotlin-stdlib\2.0.21" -Recurse -Filter 'kotlin-stdlib-2.0.21.jar' | Select-Object -First 1).FullName
$riseClasspath = @("$riseRoot\core\build\classes\kotlin\main", "$riseRoot\core\build\classes\kotlin\test", $riseJUnit, $riseHamcrest, $riseKotlin) -join ';'
$riseTestClasses = Get-ChildItem "$riseRoot\core\src\test" -Recurse -Filter '*Test.kt' | ForEach-Object {
    $risePackage = [regex]::Match([IO.File]::ReadAllText($_.FullName), '(?m)^package\s+([\w.]+)').Groups[1].Value
    "$risePackage.$($_.BaseName)"
}
& "$env:JAVA_HOME\bin\java.exe" -cp $riseClasspath org.junit.runner.JUnitCore $riseTestClasses
exit $LASTEXITCODE
