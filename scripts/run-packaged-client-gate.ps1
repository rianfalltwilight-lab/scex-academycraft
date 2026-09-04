param(
    [Parameter(Mandatory = $true)][string]$InstanceRoot,
    [string]$VersionId = 'neoforge-21.1.248',
    [string]$PlayerName = 'AcademyGate',
    [string]$JavaPath = 'java',
    [string]$AssetRoot = '',
    [string]$LibraryFallbackRoot = '',
    [switch]$Wait
)

$ErrorActionPreference = 'Stop'
$instance = (Resolve-Path -LiteralPath $InstanceRoot).Path
$childPath = Join-Path $instance "versions/$VersionId/$VersionId.json"
if (-not (Test-Path -LiteralPath $childPath -PathType Leaf)) {
    throw "NeoForge client profile is missing: $childPath"
}
$child = Get-Content -LiteralPath $childPath -Raw | ConvertFrom-Json
$baseId = [string]$child.inheritsFrom
$basePath = Join-Path $instance "versions/$baseId/$baseId.json"
$base = Get-Content -LiteralPath $basePath -Raw | ConvertFrom-Json
$libraryRoot = Join-Path $instance 'libraries'
$gameDirectory = Join-Path $instance 'game'
$nativeDirectory = Join-Path $instance 'natives'
New-Item -ItemType Directory -Path $gameDirectory,$nativeDirectory -Force | Out-Null

$fallbackLibraryFilesByName = @{}
if (-not [string]::IsNullOrWhiteSpace($LibraryFallbackRoot)) {
    $fallbackRoot = (Resolve-Path -LiteralPath $LibraryFallbackRoot).Path
    foreach ($file in Get-ChildItem -LiteralPath $fallbackRoot -Recurse -File -ErrorAction Stop) {
        if (-not $fallbackLibraryFilesByName.ContainsKey($file.Name)) {
            $fallbackLibraryFilesByName[$file.Name] = [System.Collections.Generic.List[System.IO.FileInfo]]::new()
        }
        $fallbackLibraryFilesByName[$file.Name].Add($file)
    }
}

function Restore-VerifiedLibrary {
    param(
        [string]$Target,
        [object]$Download
    )
    if (Test-Path -LiteralPath $Target -PathType Leaf) { return $true }
    $name = [System.IO.Path]::GetFileName($Target)
    if (-not $fallbackLibraryFilesByName.ContainsKey($name)) { return $false }
    foreach ($candidate in $fallbackLibraryFilesByName[$name]) {
        if (-not [string]::IsNullOrWhiteSpace([string]$Download.sha1)) {
            $actualSha1 = (Get-FileHash -Algorithm SHA1 -LiteralPath $candidate.FullName).Hash
            if ($actualSha1 -ne ([string]$Download.sha1).ToUpperInvariant()) { continue }
        }
        New-Item -ItemType Directory -Force -Path ([System.IO.Path]::GetDirectoryName($Target)) | Out-Null
        Copy-Item -LiteralPath $candidate.FullName -Destination $Target
        return $true
    }
    return $false
}

if ([string]::IsNullOrWhiteSpace($AssetRoot)) {
    $AssetRoot = Join-Path $instance 'assets'
}
$assetIndex = Join-Path $AssetRoot "indexes/$($base.assetIndex.id).json"
if (-not (Test-Path -LiteralPath $assetIndex -PathType Leaf)) {
    throw "Asset index is missing: $assetIndex"
}

function Test-RuleSet {
    param([object[]]$Rules)
    $effectiveRules = @($Rules | Where-Object { $null -ne $_ })
    if ($effectiveRules.Count -eq 0) { return $true }
    $allowed = $false
    foreach ($rule in $effectiveRules) {
        $matches = $true
        if ($null -ne $rule.os) {
            if ($null -ne $rule.os.name -and [string]$rule.os.name -ne 'windows') { $matches = $false }
            if ($null -ne $rule.os.arch -and [string]$rule.os.arch -ne 'x86_64') { $matches = $false }
            if ($null -ne $rule.os.version -and $env:OS -notmatch [string]$rule.os.version) { $matches = $false }
        }
        if ($null -ne $rule.features) {
            # The isolated gate deliberately enables no demo/custom-resolution/quick-play features.
            foreach ($property in $rule.features.PSObject.Properties) {
                if ([bool]$property.Value) { $matches = $false }
            }
        }
        if ($matches) { $allowed = [string]$rule.action -eq 'allow' }
    }
    return $allowed
}

function Add-ResolvedArguments {
    param(
        [System.Collections.Generic.List[string]]$Destination,
        [object[]]$Arguments
    )
    foreach ($entry in $Arguments) {
        if ($entry -is [string]) {
            $Destination.Add($entry)
            continue
        }
        if (-not (Test-RuleSet -Rules @($entry.rules))) { continue }
        if ($entry.value -is [string]) {
            $Destination.Add([string]$entry.value)
        } else {
            foreach ($value in @($entry.value)) { $Destination.Add([string]$value) }
        }
    }
}

function Get-LibraryKey {
    param([string]$Name)
    $parts = $Name.Split(':')
    if ($parts.Count -lt 2) { return $Name }
    # A classifier is a distinct artifact. Only an unclassified child artifact
    # should replace the unclassified base artifact with the same group/name.
    if ($parts.Count -ge 4) { return "$($parts[0]):$($parts[1]):$($parts[3])" }
    return "$($parts[0]):$($parts[1]):<main>"
}

# Child entries override base artifacts with the same Maven group/artifact.
$libraries = [System.Collections.Specialized.OrderedDictionary]::new()
foreach ($library in @($base.libraries)) {
    if (Test-RuleSet -Rules @($library.rules)) { $libraries[(Get-LibraryKey $library.name)] = $library }
}
foreach ($library in @($child.libraries)) {
    if (Test-RuleSet -Rules @($library.rules)) { $libraries[(Get-LibraryKey $library.name)] = $library }
}

$classpath = [System.Collections.Generic.List[string]]::new()
foreach ($library in $libraries.Values) {
    if ($null -eq $library.downloads.artifact) { continue }
    $artifact = Join-Path $libraryRoot ([string]$library.downloads.artifact.path)
    if (-not (Restore-VerifiedLibrary -Target $artifact -Download $library.downloads.artifact)) {
        throw "Required client library is missing: $artifact"
    }
    $classpath.Add($artifact)
}
# A vanilla profile launches its version jar directly. A modern NeoForge
# BootstrapLauncher profile instead supplies the transformed client as a
# library; adding the inherited vanilla jar creates a duplicate minecraft
# module and must be avoided.
if ([string]$child.mainClass -eq [string]$base.mainClass) {
    $clientJar = Join-Path $instance "versions/$baseId/$baseId.jar"
    if (-not (Test-Path -LiteralPath $clientJar -PathType Leaf)) {
        throw "Minecraft client jar is missing: $clientJar"
    }
    $classpath.Add($clientJar)
}

# Extract Windows native classifiers exactly once per isolated instance.
foreach ($library in @($base.libraries)) {
    if (-not (Test-RuleSet -Rules @($library.rules)) -or $null -eq $library.natives.windows) { continue }
    $classifierName = ([string]$library.natives.windows).Replace('${arch}', '64')
    $classifier = $library.downloads.classifiers.$classifierName
    if ($null -eq $classifier) { continue }
    $nativeJar = Join-Path $libraryRoot ([string]$classifier.path)
    if (-not (Restore-VerifiedLibrary -Target $nativeJar -Download $classifier)) {
        throw "Required native library is missing: $nativeJar"
    }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($nativeJar)
    try {
        foreach ($entry in $archive.Entries) {
            if ([string]::IsNullOrEmpty($entry.Name) -or $entry.FullName.StartsWith('META-INF/')) { continue }
            $target = Join-Path $nativeDirectory $entry.Name
            [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target, $true)
        }
    } finally {
        $archive.Dispose()
    }
}

$separator = [System.IO.Path]::PathSeparator
$variables = @{
    '${auth_player_name}' = $PlayerName
    '${version_name}' = $VersionId
    '${game_directory}' = $gameDirectory
    '${assets_root}' = $AssetRoot
    '${assets_index_name}' = [string]$base.assetIndex.id
    '${auth_uuid}' = '7c4d45c51ea34e74a444d70d25fc44d1'
    '${auth_access_token}' = '0'
    '${clientid}' = ''
    '${auth_xuid}' = ''
    '${user_type}' = 'legacy'
    '${version_type}' = 'AcademyCraft packaged gate'
    '${natives_directory}' = $nativeDirectory
    '${launcher_name}' = 'AcademyCraftPackagedGate'
    '${launcher_version}' = '1'
    '${library_directory}' = $libraryRoot
    '${classpath_separator}' = [string]$separator
    '${classpath}' = [string]::Join($separator, $classpath)
}

function Resolve-Variables {
    param([string]$Value)
    $resolved = $Value
    foreach ($pair in $variables.GetEnumerator()) { $resolved = $resolved.Replace($pair.Key, $pair.Value) }
    return $resolved
}

$jvmArguments = [System.Collections.Generic.List[string]]::new()
$jvmArguments.Add('-Xms512M')
$jvmArguments.Add('-Xmx2G')
Add-ResolvedArguments -Destination $jvmArguments -Arguments @($base.arguments.jvm)
Add-ResolvedArguments -Destination $jvmArguments -Arguments @($child.arguments.jvm)
$gameArguments = [System.Collections.Generic.List[string]]::new()
Add-ResolvedArguments -Destination $gameArguments -Arguments @($base.arguments.game)
Add-ResolvedArguments -Destination $gameArguments -Arguments @($child.arguments.game)

$allArguments = [System.Collections.Generic.List[string]]::new()
foreach ($argument in $jvmArguments) { $allArguments.Add((Resolve-Variables $argument)) }
$allArguments.Add([string]$child.mainClass)
foreach ($argument in $gameArguments) { $allArguments.Add((Resolve-Variables $argument)) }
$argumentLog = Join-Path $instance 'packaged-client-arguments.txt'
[System.IO.File]::WriteAllLines($argumentLog, $allArguments, [System.Text.Encoding]::UTF8)

$startInfo = [System.Diagnostics.ProcessStartInfo]::new()
$startInfo.FileName = $JavaPath
$startInfo.WorkingDirectory = $gameDirectory
$startInfo.UseShellExecute = $false
foreach ($argument in $allArguments) { $startInfo.ArgumentList.Add($argument) }
$process = [System.Diagnostics.Process]::Start($startInfo)
$pidPath = Join-Path $instance 'packaged-client.pid'
[System.IO.File]::WriteAllText($pidPath, [string]$process.Id, [System.Text.Encoding]::ASCII)
Write-Output "Started packaged client PID $($process.Id); gameDir=$gameDirectory"
if ($Wait) {
    $process.WaitForExit()
    Write-Output "Packaged client exited with code $($process.ExitCode)"
    exit $process.ExitCode
}
