param(
    [Parameter(Mandatory = $true)][string]$InstanceRoot,
    [string]$VersionId = 'neoforge-21.1.248',
    [string]$PlayerName = 'AcademyGate',
    [string]$JavaPath = 'java',
    [string]$AssetRoot = '',
    [string]$LibraryFallbackRoot = '',
    [ValidatePattern('^[A-Za-z0-9._-]+$')][string]$GameDirectoryName = 'game',
    [string]$QuickPlaySingleplayer = '',
    [string]$QuickPlayMultiplayer = '',
    [switch]$MachineVisualGate,
    [switch]$ExtraJeiGate,
    [switch]$ExtraSkillVisualGate,
    [switch]$ExtraJeiTransferGate,
    [string]$ConcurrentRoot = '',
    [ValidateSet('', 'a', 'b')][string]$ConcurrentRole = '',
    [string]$RecheckSessionRoot = '',
    [ValidateSet('', 'a', 'b')][string]$RecheckSessionRole = '',
    [string]$RecheckSessionAddress = 'localhost:25619',
    [switch]$RecheckSessionRestart,
    [ValidateRange(0, 7680)][int]$Width = 0,
    [ValidateRange(0, 4320)][int]$Height = 0,
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
if ($GameDirectoryName -in @('.', '..')) { throw 'GameDirectoryName must be a child directory' }
if ($QuickPlaySingleplayer -and $QuickPlayMultiplayer) { throw 'Select only one quick-play destination' }
$enabledGates = @($MachineVisualGate.IsPresent, $ExtraJeiGate.IsPresent,
    $ExtraSkillVisualGate.IsPresent, $ExtraJeiTransferGate.IsPresent,
    (-not [string]::IsNullOrWhiteSpace($ConcurrentRoot)), (-not [string]::IsNullOrWhiteSpace($RecheckSessionRoot))).Where({ $_ })
if ($enabledGates.Count -gt 1) { throw 'Select only one automated client gate per game directory' }
$gameDirectory = Join-Path $instance $GameDirectoryName
$nativeDirectory = Join-Path $gameDirectory 'natives'
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
    if (Test-Path -LiteralPath $Target -PathType Leaf) {
        if (-not [string]::IsNullOrWhiteSpace([string]$Download.sha1)) {
            $actual = (Get-FileHash -Algorithm SHA1 -LiteralPath $Target).Hash
            if ($actual -ne ([string]$Download.sha1).ToUpperInvariant()) {
                throw "Existing library failed SHA-1 verification: $Target"
            }
        }
        return $true
    }
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

# Match Java UUID.nameUUIDFromBytes("OfflinePlayer:" + name), including UUID version/variant.
# Distinct test players must not share the old hard-coded identity.
$identityBytes = [Security.Cryptography.MD5]::HashData([Text.Encoding]::UTF8.GetBytes("OfflinePlayer:$PlayerName"))
$identityBytes[6] = ($identityBytes[6] -band 0x0f) -bor 0x30
$identityBytes[8] = ($identityBytes[8] -band 0x3f) -bor 0x80
$offlineUuid = [Convert]::ToHexString($identityBytes).ToLowerInvariant()
$separator = [System.IO.Path]::PathSeparator
$variables = @{
    '${auth_player_name}' = $PlayerName
    '${version_name}' = $VersionId
    '${game_directory}' = $gameDirectory
    '${assets_root}' = $AssetRoot
    '${assets_index_name}' = [string]$base.assetIndex.id
    '${auth_uuid}' = $offlineUuid
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
if ($MachineVisualGate) { $jvmArguments.Add('-Dacademy.machineVisualGate=true') }
if ($ExtraJeiGate) { $jvmArguments.Add('-Dacademy.extraJeiGate=true') }
if ($ExtraSkillVisualGate) { $jvmArguments.Add('-Dacademy.extraSkillVisualGate=true') }
if ($ExtraJeiTransferGate) { $jvmArguments.Add('-Dacademy.extraJeiTransferGate=true') }
if ($ConcurrentRoot) {
    if (-not $ConcurrentRole -or $MachineVisualGate) { throw 'Concurrent gate requires a role and cannot use the singleplayer gate' }
    $jvmArguments.Add('-Dacademy.concurrentMenuGate=true')
    $jvmArguments.Add("-Dacademy.concurrentRoot=$ConcurrentRoot")
    $jvmArguments.Add("-Dacademy.concurrentRole=$ConcurrentRole")
}
if ($RecheckSessionRoot) {
    if (-not $RecheckSessionRole) { throw 'Recheck session gate requires a role' }
    if (-not (Test-Path -LiteralPath (Join-Path $RecheckSessionRoot 'ISOLATED-ACCEPTANCE') -PathType Leaf)) { throw 'Recheck isolated marker missing' }
    $jvmArguments.Add('-Dacademy.recheckSessionGate=true')
    $jvmArguments.Add("-Dacademy.recheckSessionRoot=$RecheckSessionRoot")
    $jvmArguments.Add("-Dacademy.recheckSessionRole=$RecheckSessionRole")
    $jvmArguments.Add("-Dacademy.recheckSessionAddress=$RecheckSessionAddress")
    if ($RecheckSessionRestart) { $jvmArguments.Add('-Dacademy.recheckSessionRestart=true') }
} elseif ($RecheckSessionRestart -or $RecheckSessionRole) { throw 'Recheck options require RecheckSessionRoot' }
Add-ResolvedArguments -Destination $jvmArguments -Arguments @($base.arguments.jvm)
Add-ResolvedArguments -Destination $jvmArguments -Arguments @($child.arguments.jvm)
$gameArguments = [System.Collections.Generic.List[string]]::new()
Add-ResolvedArguments -Destination $gameArguments -Arguments @($base.arguments.game)
Add-ResolvedArguments -Destination $gameArguments -Arguments @($child.arguments.game)
if ($QuickPlaySingleplayer) { $gameArguments.Add('--quickPlaySingleplayer'); $gameArguments.Add($QuickPlaySingleplayer) }
if ($QuickPlayMultiplayer) { $gameArguments.Add('--quickPlayMultiplayer'); $gameArguments.Add($QuickPlayMultiplayer) }
if ($Width -gt 0) { $gameArguments.Add('--width'); $gameArguments.Add([string]$Width) }
if ($Height -gt 0) { $gameArguments.Add('--height'); $gameArguments.Add([string]$Height) }

$allArguments = [System.Collections.Generic.List[string]]::new()
foreach ($argument in $jvmArguments) { $allArguments.Add((Resolve-Variables $argument)) }
$allArguments.Add([string]$child.mainClass)
foreach ($argument in $gameArguments) { $allArguments.Add((Resolve-Variables $argument)) }
$argumentLog = Join-Path $instance "packaged-client-$GameDirectoryName-arguments.txt"
[System.IO.File]::WriteAllLines($argumentLog, $allArguments, [System.Text.Encoding]::UTF8)

$startInfo = [System.Diagnostics.ProcessStartInfo]::new()
$startInfo.FileName = $JavaPath
$startInfo.WorkingDirectory = $gameDirectory
$startInfo.UseShellExecute = $false
$startInfo.CreateNoWindow = $true
$startInfo.WindowStyle = [System.Diagnostics.ProcessWindowStyle]::Hidden
foreach ($argument in $allArguments) { $startInfo.ArgumentList.Add($argument) }
$process = [System.Diagnostics.Process]::Start($startInfo)
$pidPath = Join-Path $instance "packaged-client-$GameDirectoryName.pid"
[System.IO.File]::WriteAllText($pidPath, [string]$process.Id, [System.Text.Encoding]::ASCII)
Write-Output "Started packaged client PID $($process.Id); gameDir=$gameDirectory"
if ($Wait) {
    $process.WaitForExit()
    Write-Output "Packaged client exited with code $($process.ExitCode)"
    exit $process.ExitCode
}
