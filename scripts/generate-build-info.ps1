param(
    [string]$EvaluationDate = (Get-Date -Format 'yyyy-MM-dd'),
    [string]$GameTestEvidence = 'NOT VERIFIED for this build',
    [string]$ClientMachineGateEvidence = 'NOT VERIFIED for this build',
    [string]$PackagedServerGateEvidence = 'NOT VERIFIED for this build',
    [string]$PackagedClientGateEvidence = 'NOT VERIFIED for this build',
    [string]$ReleaseFixes = 'No release-specific fix summary supplied'
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$projectPrefix = $projectRoot + [System.IO.Path]::DirectorySeparatorChar
$modVersionLine = Get-Content -LiteralPath (Join-Path $projectRoot 'gradle.properties') |
    Where-Object { $_ -match '^mod_version=' } | Select-Object -First 1
if ($null -eq $modVersionLine) {
    throw 'mod_version is missing from gradle.properties'
}
$modVersion = ($modVersionLine -split '=', 2)[1].Trim()
. (Join-Path $PSScriptRoot 'source-files.ps1')
$source = Get-AcademyDirectoryTreeDigest -ProjectRoot $projectRoot
$treeHash = $source.Hash
$wrapperProperties = [System.IO.File]::ReadAllText((Join-Path $projectRoot 'gradle/wrapper/gradle-wrapper.properties'))
$wrapperMatch = [regex]::Match($wrapperProperties, '(?m)^distributionUrl=.*?/gradle-(.+)-(?:bin|all)\.zip\s*$')
if (-not $wrapperMatch.Success) { throw 'Cannot determine Gradle wrapper version from distributionUrl.' }
$wrapperVersion = $wrapperMatch.Groups[1].Value
$javaInfo = New-Object System.Diagnostics.ProcessStartInfo
$javaInfo.FileName = 'java'
$javaInfo.Arguments = '-version'
$javaInfo.UseShellExecute = $false
$javaInfo.RedirectStandardOutput = $true
$javaInfo.RedirectStandardError = $true
$javaProcess = [System.Diagnostics.Process]::Start($javaInfo)
$javaStdout = $javaProcess.StandardOutput.ReadToEnd()
$javaStderr = $javaProcess.StandardError.ReadToEnd()
$javaProcess.WaitForExit()
if ($javaProcess.ExitCode -ne 0) {
    throw "java -version failed with exit code $($javaProcess.ExitCode)"
}
$javaVersion = (($javaStderr + $javaStdout) -split "`r?`n" | Select-Object -First 1).Trim()

# JUnit XML proves only the assertions it contains. Keep the aggregate separate
# from the heuristic count of source/resource contract files so it can never be
# presented as a gameplay completion percentage.
$testResultFiles = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'build/test-results/test') `
    -Filter 'TEST-*.xml' -File -ErrorAction SilentlyContinue)
if ($testResultFiles.Count -eq 0) {
    $junitEvidence = 'NOT RUN for this build'
} else {
    $tests = 0
    $failures = 0
    $errors = 0
    $skipped = 0
    foreach ($resultFile in $testResultFiles) {
        [xml]$suite = Get-Content -LiteralPath $resultFile.FullName
        $tests += [int]$suite.testsuite.tests
        $failures += [int]$suite.testsuite.failures
        $errors += [int]$suite.testsuite.errors
        $skipped += [int]$suite.testsuite.skipped
    }
    $junitEvidence = "$tests tests, $failures failures, $errors errors, $skipped skipped; includes static contracts and is not a gameplay-parity rate"
}

$testSources = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'src/test/java') `
    -Filter '*.java' -File -Recurse -ErrorAction SilentlyContinue)
$sourceContractHeuristic = @($testSources | Where-Object {
    Select-String -LiteralPath $_.FullName -Pattern 'Files\.readString|\.contains\(' -Quiet
}).Count

$content = @(
    'Format-Version=2'
    'Project=AcademyCraft 1.21.1 reconstruction'
    "Mod-Version=$modVersion"
    'Minecraft-Version=1.21.1'
    'Loader=NeoForge 21.1.248'
    'MohistMC-Upstream-SHA=00e9cf09fc4c52d2f9b3b3af7d4cda140a4ccf1c'
    'Legacy-Upstream-SHA=7b1401cd420bd6888a2b9d8db5cd8a69fe314bb9'
    "Evaluation-Date=$EvaluationDate"
    "Source-Tree-SHA256=$treeHash"
    "Source-File-Count=$($source.Count)"
    'Source-Tree-Hash-Algorithm=SHA-256 over ordinal-relative-path-sorted UTF-8 lines: lowercase-file-sha256 two-spaces relative-path newline'
    'Source-Tree-Inventory=scripts/source-files.ps1; root build/license/readme files plus gradle/**,src/**,scripts/**,docs/**,.github/**; excludes BUILD-INFO.txt,linked paths,log files and runtime/cache/build/audit directories'
    "JUnit-Tests=$junitEvidence"
    "Source-Contract-Heuristic=$sourceContractHeuristic of $($testSources.Count) test source files contain Files.readString or .contains(; review docs/TESTING.md"
    "GameTests=$GameTestEvidence"
    "Client-Machine-Gate=$ClientMachineGateEvidence"
    "Packaged-Server-Gate=$PackagedServerGateEvidence"
    "Packaged-Client-Gate=$PackagedClientGateEvidence"
    "Release-Fixes=$ReleaseFixes"
    "Gradle-Version=$wrapperVersion"
    'Gradle-Version-Source=gradle/wrapper/gradle-wrapper.properties distributionUrl; use ./gradlew for the release build'
    "Java-Version=$javaVersion"
) -join "`n"

$path = Join-Path $projectRoot 'BUILD-INFO.txt'
[System.IO.File]::WriteAllText($path, $content + "`n", [System.Text.UTF8Encoding]::new($false))
Write-Output $path
