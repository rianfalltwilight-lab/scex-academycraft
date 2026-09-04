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
$excludedTopDirectories = @(
    '.git', '.gradle', 'build', 'net', 'run', 'run-client-gate',
    'run-machine-gate', 'run-server-gate'
)

function Get-ProjectRelativePath {
    param([Parameter(Mandatory = $true)][string]$Path)

    if (-not $Path.StartsWith($projectPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Path is outside the project root: $Path"
    }
    return $Path.Substring($projectPrefix.Length).Replace('\', '/')
}

$inputs = Get-ChildItem -LiteralPath $projectRoot -File -Recurse -Force | Where-Object {
    $relative = Get-ProjectRelativePath -Path $_.FullName
    $segments = $relative.Split('/')
    $top = $segments[0]
    $top -notin $excludedTopDirectories -and
        -not $top.StartsWith('run-packaged-', [System.StringComparison]::OrdinalIgnoreCase) -and
        -not ($segments -contains '.cache') -and
        $relative -ne 'BUILD-INFO.txt' -and
        -not $relative.StartsWith('src/generated/resources/.cache/', [System.StringComparison]::OrdinalIgnoreCase)
} | ForEach-Object {
    $relative = Get-ProjectRelativePath -Path $_.FullName
    [pscustomobject]@{ Relative = $relative; Hash = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant() }
}

$sortedLines = [System.Collections.Generic.SortedDictionary[string,string]]::new([System.StringComparer]::Ordinal)
foreach ($inputFile in $inputs) {
    $sortedLines.Add($inputFile.Relative, "$($inputFile.Hash)  $($inputFile.Relative)`n")
}
$canonical = ($sortedLines.Values) -join ''
$bytes = [System.Text.Encoding]::UTF8.GetBytes($canonical)
$sha256 = [System.Security.Cryptography.SHA256]::Create()
try {
    $sha = $sha256.ComputeHash($bytes)
} finally {
    $sha256.Dispose()
}
$treeHash = ([System.BitConverter]::ToString($sha)).Replace('-', '').ToLowerInvariant()
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
    'Format-Version=1'
    'Project=AcademyCraft 1.21.1 reconstruction'
    "Mod-Version=$modVersion"
    'Minecraft-Version=1.21.1'
    'Loader=NeoForge 21.1.248'
    'MohistMC-Upstream-SHA=00e9cf09fc4c52d2f9b3b3af7d4cda140a4ccf1c'
    'Legacy-Upstream-SHA=7b1401cd420bd6888a2b9d8db5cd8a69fe314bb9'
    "Evaluation-Date=$EvaluationDate"
    "Source-Tree-SHA256=$treeHash"
    'Source-Tree-Hash-Algorithm=SHA-256 over ordinal-relative-path-sorted UTF-8 lines: lowercase-file-sha256 two-spaces relative-path newline'
    'Source-Tree-Hash-Excludes=BUILD-INFO.txt,.git/**,.gradle/**,build/**,net/**,run/**,run-client-gate/**,run-machine-gate/**,run-server-gate/**,run-packaged-*/**,**/.cache/**,src/generated/resources/.cache/**'
    "JUnit-Tests=$junitEvidence"
    "Source-Contract-Heuristic=$sourceContractHeuristic of $($testSources.Count) test source files contain Files.readString or .contains(; review docs/TESTING.md"
    "GameTests=$GameTestEvidence"
    "Client-Machine-Gate=$ClientMachineGateEvidence"
    "Packaged-Server-Gate=$PackagedServerGateEvidence"
    "Packaged-Client-Gate=$PackagedClientGateEvidence"
    "Release-Fixes=$ReleaseFixes"
    'Gradle-Version=9.2.1'
    "Java-Version=$javaVersion"
) -join "`n"

$path = Join-Path $projectRoot 'BUILD-INFO.txt'
[System.IO.File]::WriteAllText($path, $content + "`n", [System.Text.UTF8Encoding]::new($false))
Write-Output $path
