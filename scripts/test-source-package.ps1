param([string]$ResultDirectory = '')

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
if ([string]::IsNullOrWhiteSpace($ResultDirectory)) {
    $ResultDirectory = Join-Path $projectRoot ('build/source-package-tests-' + [guid]::NewGuid().ToString('N'))
}
$resultRoot = [System.IO.Path]::GetFullPath($ResultDirectory)
if (Test-Path -LiteralPath $resultRoot) { throw "Refusing to reuse an existing test directory: $resultRoot" }
[System.IO.Directory]::CreateDirectory($resultRoot) | Out-Null
$fixture = Join-Path $resultRoot 'fixture'
$utf8 = [System.Text.UTF8Encoding]::new($false)
$checks = [System.Collections.Generic.List[string]]::new()

function Write-Fixture {
    param([string]$Relative, [string]$Text)
    $path = Join-Path $fixture $Relative
    [System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($path)) | Out-Null
    [System.IO.File]::WriteAllText($path, $Text, $utf8)
}
function Assert-Check {
    param([bool]$Condition, [string]$Name)
    if (-not $Condition) { throw "FAILED: $Name" }
    $checks.Add($Name)
}
function Assert-Throws {
    param([scriptblock]$Action, [string]$Pattern, [string]$Name)
    $message = ''
    try { & $Action | Out-Null } catch { $message = $_.Exception.Message }
    Assert-Check ($message -match $Pattern) $Name
}
foreach ($name in @('build.gradle', 'settings.gradle', 'gradlew', 'gradlew.bat', 'LICENSE', 'NOTICE')) {
    Write-Fixture $name "fixture input: $name"
}
Write-Fixture 'gradle.properties' ('mod_version=fixture-1' + [char]10)
Write-Fixture 'gradle/wrapper/gradle-wrapper.properties' ('distributionUrl=https\://services.gradle.org/distributions/gradle-9.2.0-bin.zip' + [char]10)
Write-Fixture 'gradle/wrapper/gradle-wrapper.jar' 'fixture bytes; no build is attempted'
Write-Fixture 'src/main/java/Fixture.java' 'class Fixture {}'
Write-Fixture 'docs/TESTING.md' 'archive rebuild instructions'
Write-Fixture '.github/workflows/gradle.yml' 'name: fixture'
Write-Fixture 'src/main/resources/non_ascii.txt' ('Unicode content: 中文' + [char]10)
foreach ($relative in @('old.log', 'build/result.txt', 'audit/report.txt', 'run-machine-gate/world.dat',
        'src/generated/resources/.cache/cache', 'src/main/runtime.log', 'scripts/cache/temp.txt')) {
    Write-Fixture $relative 'must be excluded'
}
foreach ($name in @('source-files.ps1', 'verify-source-package.ps1', 'package-source.ps1', 'generate-build-info.ps1')) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination (Join-Path $fixture "scripts/$name")
}
. (Join-Path $PSScriptRoot 'source-files.ps1')
$names = @((Get-AcademySourceFiles -ProjectRoot $fixture).Relative)
Assert-Check ($names -contains 'docs/TESTING.md' -and $names -contains '.github/workflows/gradle.yml') 'docs and hidden GitHub workflow are included'
Assert-Check (-not @($names | Where-Object { $_ -match '(^audit/|^build/|^run-|\.log$|/cache/|/\.cache/)' }).Count) 'runtime, logs, caches, build and audit excluded'
foreach ($bad in @('../escape.txt', '/absolute.txt', 'src/../escape.txt', 'src\unsafe.txt', 'src/x:y', 'src/name.')) {
    Assert-Check (-not (Test-AcademySourceRelativePath $bad)) "unsafe path rejected: $bad"
}
& (Join-Path $fixture 'scripts/generate-build-info.ps1') -EvaluationDate '2000-01-01' | Out-Null
$info = Read-AcademyBuildInfo -Content ([System.IO.File]::ReadAllText((Join-Path $fixture 'BUILD-INFO.txt')))
Assert-Check ($info['Gradle-Version'] -eq '9.2.0') 'generator records the actual wrapper distribution version'
$first = Join-Path $resultRoot 'first.zip'
$second = Join-Path $resultRoot 'second.zip'
$packaged = & (Join-Path $fixture 'scripts/package-source.ps1') -Destination $first
Assert-Check ($packaged.SourceTreeSHA256 -ceq $info['Source-Tree-SHA256']) 'archive bytes reproduce declared tree hash'
& (Join-Path $fixture 'scripts/package-source.ps1') -Destination $second | Out-Null
$firstHash = (Get-FileHash -LiteralPath $first).Hash
Assert-Check ($firstHash -ceq (Get-FileHash -LiteralPath $second).Hash) 'identical inputs produce byte-identical ZIPs'
Assert-Throws { & (Join-Path $fixture 'scripts/package-source.ps1') -Destination $first } 'Refusing to overwrite' 'existing artifact overwrite refused'
Assert-Check ($firstHash -ceq (Get-FileHash -LiteralPath $first).Hash) 'refused overwrite preserves existing archive bytes'
# Run with only the two verifier scripts, no project files or checkout.
$independent = Join-Path $resultRoot 'standalone-verifier'
[System.IO.Directory]::CreateDirectory($independent) | Out-Null
foreach ($name in @('source-files.ps1', 'verify-source-package.ps1')) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination (Join-Path $independent $name)
}
$verified = & (Join-Path $independent 'verify-source-package.ps1') -ArchivePath $first
Assert-Check ($verified.SourceTreeSHA256 -ceq $info['Source-Tree-SHA256']) 'standalone verification needs no source checkout'
Write-Fixture 'docs/TESTING.md' 'changed after build metadata'
Assert-Throws { & (Join-Path $fixture 'scripts/package-source.ps1') -Destination (Join-Path $resultRoot 'stale.zip') } 'Source inputs have changed' 'stale BUILD-INFO blocks packaging'
Assert-Check (-not (Test-Path -LiteralPath (Join-Path $resultRoot 'stale.zip'))) 'stale metadata rejection creates no archive'

# Only rewrite this run's fixture ZIPs; retain every archive for inspection.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$read = [System.IO.Compression.ZipFile]::OpenRead($first)
try {
    foreach ($mode in @('tamper', 'unsafe', 'duplicate')) {
        $alteredPath = Join-Path $resultRoot ($mode + '.zip')
        $stream = [System.IO.FileStream]::new($alteredPath, [System.IO.FileMode]::CreateNew)
        try {
            $zip = [System.IO.Compression.ZipArchive]::new($stream, [System.IO.Compression.ZipArchiveMode]::Create, $true)
            try {
                foreach ($entry in $read.Entries) {
                    $target = $zip.CreateEntry($entry.FullName).Open()
                    try {
                        if ($mode -eq 'tamper' -and $entry.FullName -ceq 'docs/TESTING.md') {
                            $data = $utf8.GetBytes('tampered archive bytes')
                            $target.Write($data, 0, $data.Length)
                        } else {
                            $source = $entry.Open()
                            try { $source.CopyTo($target) } finally { $source.Dispose() }
                        }
                    } finally { $target.Dispose() }
                }
                if ($mode -eq 'unsafe') { $zip.CreateEntry('../escape.txt') | Out-Null }
                if ($mode -eq 'duplicate') { $zip.CreateEntry('build-info.txt') | Out-Null }
            } finally { $zip.Dispose() }
        } finally { $stream.Dispose() }
        $expected = @{ tamper = 'Source package mismatch'; unsafe = 'unsafe archive entry'; duplicate = 'Duplicate archive path' }[$mode]
        Assert-Throws { & (Join-Path $independent 'verify-source-package.ps1') -ArchivePath $alteredPath } $expected "archive $mode rejected"
    }
} finally { $read.Dispose() }
$result = [pscustomobject]@{
    Result = 'PASS'
    CheckCount = $checks.Count
    Checks = $checks.ToArray()
    EvidenceDirectory = $resultRoot
    Scope = 'PowerShell packaging fixture only; no Gradle or Minecraft run'
}
[System.IO.File]::WriteAllText((Join-Path $resultRoot 'results.json'), ($result | ConvertTo-Json -Depth 4), $utf8)
$result