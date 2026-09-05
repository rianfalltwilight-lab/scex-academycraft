param(
    [string]$Destination = ''
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'source-files.ps1')
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
if ([string]::IsNullOrWhiteSpace($Destination)) {
    $Destination = Join-Path $projectRoot 'build/distributions/AcademyCraft-1.21.1-rebuilt-source.zip'
}
$destinationPath = [System.IO.Path]::GetFullPath($Destination)
if (Test-Path -LiteralPath $destinationPath) { throw "Refusing to overwrite existing source archive: $destinationPath" }
$buildInfoPath = Join-Path $projectRoot 'BUILD-INFO.txt'
$infoItem = Get-Item -LiteralPath $buildInfoPath
if ($infoItem.Attributes -band [System.IO.FileAttributes]::ReparsePoint) { throw 'Linked BUILD-INFO.txt is not permitted.' }
$buildInfo = Read-AcademyBuildInfo -Content ([System.IO.File]::ReadAllText($buildInfoPath))
$source = Get-AcademyDirectoryTreeDigest -ProjectRoot $projectRoot
if ($source.Hash -cne $buildInfo['Source-Tree-SHA256'] -or $source.Count -ne [int]$buildInfo['Source-File-Count']) {
    throw 'Source inputs have changed since BUILD-INFO.txt was generated. Regenerate build info and rebuild the release before packaging.'
}
# No destructive staging cleanup. CreateNew also closes the existence-check race.
# If interrupted or verification fails, the incomplete archive remains for inspection.
[System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($destinationPath)) | Out-Null
$output = [System.IO.FileStream]::new($destinationPath, [System.IO.FileMode]::CreateNew,
    [System.IO.FileAccess]::Write, [System.IO.FileShare]::None)
try {
    $archive = [System.IO.Compression.ZipArchive]::new($output, [System.IO.Compression.ZipArchiveMode]::Create,
        $true, [System.Text.Encoding]::UTF8)
    try {
        $files = [System.Collections.Generic.SortedDictionary[string,string]]::new([System.StringComparer]::Ordinal)
        foreach ($file in $source.Files) { $files.Add($file.Relative, $file.FullName) }
        $files.Add('BUILD-INFO.txt', $buildInfoPath)
        foreach ($relative in $files.Keys) {
            $entry = $archive.CreateEntry($relative, [System.IO.Compression.CompressionLevel]::Optimal)
            $entry.LastWriteTime = [System.DateTimeOffset]::new(2000, 1, 1, 0, 0, 0, [System.TimeSpan]::Zero)
            $entry.ExternalAttributes = 0
            $input = [System.IO.File]::OpenRead($files[$relative])
            try {
                $target = $entry.Open()
                try { $input.CopyTo($target) }
                finally { $target.Dispose() }
            } finally { $input.Dispose() }
        }
    } finally { $archive.Dispose() }
} finally { $output.Dispose() }
& (Join-Path $PSScriptRoot 'verify-source-package.ps1') -ArchivePath $destinationPath