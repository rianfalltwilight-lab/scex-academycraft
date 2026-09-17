param([Parameter(Mandatory)][string]$RunRoot, [Parameter(Mandatory)][string]$RuntimeRoot,
      [ValidateSet("MONARCH","VICERACH")][string]$ExpectedHost="MONARCH", [switch]$DesktopWindowHeld)
$ErrorActionPreference='Stop'
if ($env:COMPUTERNAME -ine $ExpectedHost) { throw 'Client profile host mismatch.' }
if ($DesktopWindowHeld -and (($env:COMPUTERNAME -ine 'VICERACH') -or (Get-Process -Id $PID).SessionId -le 0)) { throw 'Desktop adapter requires the interactive Vicerach session.' }
$run=[IO.Path]::GetFullPath($RunRoot)
if (!$run.StartsWith('D:\Minecraft-Validation\runs\academy-machineui28-', [StringComparison]::OrdinalIgnoreCase)) { throw 'Invalid isolated run.' }
$lease=$null
if (!$DesktopWindowHeld) { $lease=[IO.File]::Open('D:/Minecraft-Validation/control/client-window.lock','OpenOrCreate','ReadWrite','None') }
try {
    if ((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory * 1KB -lt 6GB) { throw 'Requires 6 GiB free RAM.' }
    $recipe=Get-Content -LiteralPath "$RuntimeRoot\client-recipe.json" -Raw | ConvertFrom-Json
    foreach($inputFile in $recipe.inputs) {
        if ((Get-FileHash -LiteralPath $inputFile.path).Hash -ine $inputFile.sha256) { throw "Runtime dependency changed: $($inputFile.path)" }
    }
    $start=[Diagnostics.ProcessStartInfo]::new($recipe.java)
    $start.WorkingDirectory=$run; $start.UseShellExecute=$false; $start.CreateNoWindow=$true
    $start.RedirectStandardOutput=$true; $start.RedirectStandardError=$true
    foreach($argument in $recipe.arguments) { $start.ArgumentList.Add($argument) }
    $process=[Diagnostics.Process]::new(); $process.StartInfo=$start
    $out=[IO.File]::Create("$run\stdout.log"); $err=[IO.File]::Create("$run\stderr.log")
    $forced=$false
    try {
        if (!$process.Start()) { throw 'Client did not start.' }
        $started=Get-Date
        @{pid=$process.Id;started=$started;java=$recipe.java}|ConvertTo-Json|Set-Content "$run\process.json"
        $copyOut=$process.StandardOutput.BaseStream.CopyToAsync($out)
        $copyErr=$process.StandardError.BaseStream.CopyToAsync($err)
        while (!$process.WaitForExit(2000)) {
            if (((Get-Date)-$started).TotalSeconds -gt 300 -or (Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory*1KB -lt 2.5GB) {
                [IO.File]::WriteAllText("$run/stop-request.txt", "resource or timeout gate"); if (!$process.WaitForExit(30000)) { $forced=$true; $process.Kill(); $process.WaitForExit() }; break
            }
        }
        $null=$copyOut.GetAwaiter().GetResult(); $null=$copyErr.GetAwaiter().GetResult()
        $code=$process.ExitCode
    } finally { $out.Dispose();$err.Dispose();$process.Dispose() }
    $gate=Join-Path $run 'academy-machineui-gate-result.txt'
    $text=if(Test-Path $gate){[IO.File]::ReadAllText($gate)}else{''}
    $log=if(Test-Path "$run\logs\latest.log"){[IO.File]::ReadAllText("$run\logs\latest.log")}else{''}
    $saved=$log.Contains('All dimensions are saved')
    $passed=!$forced -and $code -eq 0 -and $text.Contains('status=PASS') -and $text.Contains('completedCases=14/14') -and $saved
    @{passed=$passed;forced=$forced;exit_code=$code;process_exited=$true;saved_after_stop=$saved;cases=14;host=$env:COMPUTERNAME}|
        ConvertTo-Json|Set-Content "$run\result.json" -Encoding utf8
    if(!$passed){throw "Client gate failed: exit=$code forced=$forced saved=$saved"}
} finally { if ($lease) { $lease.Dispose() } }
