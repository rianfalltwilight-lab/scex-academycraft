param(
    [string]$Destination = ""
)

$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$projectPrefix = $projectRoot + [System.IO.Path]::DirectorySeparatorChar
$distributionDir = Join-Path $projectRoot "build\distributions"
if ([string]::IsNullOrWhiteSpace($Destination)) {
    $Destination = Join-Path $distributionDir "AcademyCraft-1.21.1-rebuilt-source.zip"
}
$destinationPath = [System.IO.Path]::GetFullPath($Destination)
$staging = Join-Path $projectRoot "build\source-package-staging"

function Get-ProjectRelativePath {
    param([Parameter(Mandatory = $true)][string]$Path)

    if (-not $Path.StartsWith($projectPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Path is outside the project root: $Path"
    }
    return $Path.Substring($projectPrefix.Length)
}

if (Test-Path -LiteralPath $staging) { Remove-Item -LiteralPath $staging -Recurse -Force }
New-Item -ItemType Directory -Path $staging -Force | Out-Null

$allowedTopLevelFiles = @(
    '.gitattributes', '.gitignore', 'build.gradle', 'gradle.properties',
    'gradlew', 'gradlew.bat', 'settings.gradle', 'LICENSE', 'NOTICE', 'README.md',
    'AI-GENERATED.md', 'BUILD-INFO.txt'
)
$sourceFiles = @()
foreach ($name in $allowedTopLevelFiles) {
    $candidate = Join-Path $projectRoot $name
    if (Test-Path -LiteralPath $candidate -PathType Leaf) { $sourceFiles += Get-Item -LiteralPath $candidate }
}
foreach ($directory in @('gradle', 'src', 'scripts')) {
    $candidate = Join-Path $projectRoot $directory
    if (Test-Path -LiteralPath $candidate -PathType Container) {
        $sourceFiles += Get-ChildItem -LiteralPath $candidate -File -Recurse -Force |
            Where-Object {
                $relative = (Get-ProjectRelativePath -Path $_.FullName).Replace('\', '/')
                $segments = $relative.Split('/')
                -not ($segments -contains '.cache') -and
                -not $relative.StartsWith('src/generated/resources/.cache/', [System.StringComparison]::OrdinalIgnoreCase)
            }
    }
}

# This is deliberately an allowlist. Runtime state, build products, logs, worlds,
# caches, usercache files and unrelated secret material cannot enter the archive.
$sourceFiles | ForEach-Object {
    $relative = Get-ProjectRelativePath -Path $_.FullName
    $target = Join-Path $staging $relative
    $targetParent = Split-Path -Parent $target
    New-Item -ItemType Directory -Path $targetParent -Force | Out-Null
    Copy-Item -LiteralPath $_.FullName -Destination $target
    # ZIP cannot represent pre-1980 timestamps and variable mtimes impede reproducibility.
    (Get-Item -LiteralPath $target).LastWriteTimeUtc = [datetime]'2000-01-01T00:00:00Z'
}

$testClasses = @(Get-ChildItem -LiteralPath (Join-Path $staging 'src\test\java') -Filter '*Test.java' -File -Recurse)
if ($testClasses.Count -lt 9) {
    throw "Expected at least the 9 audited JUnit test classes, found $($testClasses.Count)."
}
New-Item -ItemType Directory -Path (Split-Path -Parent $destinationPath) -Force | Out-Null
if (Test-Path -LiteralPath $destinationPath) { Remove-Item -LiteralPath $destinationPath -Force }
Compress-Archive -Path (Join-Path $staging '*') -DestinationPath $destinationPath -CompressionLevel Optimal
Remove-Item -LiteralPath $staging -Recurse -Force
Write-Output $destinationPath
