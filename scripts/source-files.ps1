# Shared inventory for hashing, packaging and independent ZIP verification.
# BUILD-INFO.txt is packaged separately, excluded from its own source tree hash.
$script:AcademySourceTopFiles = @(
    '.gitattributes', '.gitignore', 'build.gradle', 'gradle.properties',
    'gradlew', 'gradlew.bat', 'settings.gradle', 'LICENSE', 'NOTICE',
    'README.md', 'AI-GENERATED.md'
)
$script:AcademySourceDirectories = @('gradle', 'src', 'scripts', 'docs', '.github')

function Test-AcademySourceRelativePath {
    param([Parameter(Mandatory = $true)][string]$RelativePath)
    if ($RelativePath -match '[\\:\x00-\x1f]' -or $RelativePath.StartsWith('/')) { return $false }
    $segments = $RelativePath.Split('/')
    if (@($segments | Where-Object { $_ -in @('', '.', '..') -or $_ -match '[. ]$' }).Count) { return $false }
    if ($segments.Count -eq 1) { return $RelativePath -cin $script:AcademySourceTopFiles }
    if ($segments[0] -cnotin $script:AcademySourceDirectories) { return $false }
    foreach ($segment in $segments[0..($segments.Length - 2)]) {
        if ($segment -in @('.git', '.gradle', '.cache', 'cache', 'caches', 'build', 'audit', 'net', 'run') -or
            $segment -like 'run-*') { return $false }
    }
    return $segments[-1] -notmatch '(?i)\.log(?:\..*)?$'
}

function Get-AcademySourceFiles {
    param([Parameter(Mandatory = $true)][string]$ProjectRoot)
    $root = (Resolve-Path -LiteralPath $ProjectRoot).Path.TrimEnd([char[]]'\/')
    $prefix = $root + [System.IO.Path]::DirectorySeparatorChar
    $files = [System.Collections.Generic.SortedDictionary[string,object]]::new([System.StringComparer]::Ordinal)
    $pending = [System.Collections.Generic.Stack[string]]::new()
    foreach ($name in $script:AcademySourceTopFiles + $script:AcademySourceDirectories) {
        $path = Join-Path $root $name
        if (Test-Path -LiteralPath $path) { $pending.Push($path) }
    }
    while ($pending.Count -gt 0) {
        $item = Get-Item -LiteralPath $pending.Pop() -Force
        if (-not $item.FullName.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase)) {
            throw "Source path is outside the project root: $($item.FullName)"
        }
        if ($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) {
            throw "Linked source path is not permitted: $($item.FullName)"
        }
        $relative = $item.FullName.Substring($prefix.Length).Replace('\', '/')
        if ($item.PSIsContainer) {
            if (Test-AcademySourceRelativePath -RelativePath ($relative + '/source-probe.txt')) {
                foreach ($child in Get-ChildItem -LiteralPath $item.FullName -Force) { $pending.Push($child.FullName) }
            }
        } elseif (Test-AcademySourceRelativePath -RelativePath $relative) {
            $files.Add($relative, [pscustomobject]@{ Relative = $relative; FullName = $item.FullName })
        }
    }
    return $files.Values
}

function Get-AcademyStreamSHA256 {
    param([Parameter(Mandatory = $true)][System.IO.Stream]$Stream)
    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try { return ([System.BitConverter]::ToString($sha256.ComputeHash($Stream))).Replace('-', '').ToLowerInvariant() }
    finally { $sha256.Dispose() }
}

function Get-AcademySourceTreeDigest {
    param([Parameter(Mandatory = $true)][object[]]$FileHashes)
    $lines = [System.Collections.Generic.SortedDictionary[string,string]]::new([System.StringComparer]::Ordinal)
    foreach ($file in $FileHashes) {
        if (-not (Test-AcademySourceRelativePath -RelativePath $file.Relative) -or $file.Hash -cnotmatch '^[0-9a-f]{64}$') {
            throw "Invalid source digest input: $($file.Relative)"
        }
        $lines.Add($file.Relative, "$($file.Hash)  $($file.Relative)" + [char]10)
    }
    $bytes = [System.Text.Encoding]::UTF8.GetBytes(($lines.Values -join ''))
    $stream = [System.IO.MemoryStream]::new($bytes, $false)
    try { return Get-AcademyStreamSHA256 -Stream $stream }
    finally { $stream.Dispose() }
}

function Get-AcademyDirectoryTreeDigest {
    param([Parameter(Mandatory = $true)][string]$ProjectRoot)
    $files = @(Get-AcademySourceFiles -ProjectRoot $ProjectRoot)
    $hashes = @($files | ForEach-Object {
        [pscustomobject]@{ Relative = $_.Relative; Hash = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant() }
    })
    return [pscustomobject]@{ Hash = (Get-AcademySourceTreeDigest -FileHashes $hashes); Count = $files.Count; Files = $files }
}

function Read-AcademyBuildInfo {
    param([Parameter(Mandatory = $true)][string]$Content)
    $values = @{}
    foreach ($line in ($Content -split '\r?\n')) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $parts = $line -split '=', 2
        if ($parts.Length -ne 2 -or $values.ContainsKey($parts[0])) { throw "Invalid or duplicate BUILD-INFO field: $line" }
        $values.Add($parts[0], $parts[1])
    }
    if ($values['Format-Version'] -ne '2' -or $values['Source-Tree-SHA256'] -cnotmatch '^[0-9a-f]{64}$' -or
        $values['Source-File-Count'] -notmatch '^[1-9][0-9]*$') {
        throw 'BUILD-INFO must contain Format-Version=2, Source-Tree-SHA256 and Source-File-Count; regenerate it first.'
    }
    return $values
}