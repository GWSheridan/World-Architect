param([Parameter(Mandatory)][int]$JavaProcessId,[Parameter(Mandatory)][string]$OutputPath,[Parameter(Mandatory)][string]$StopPath)
$ErrorActionPreference='Stop'
$stream=[IO.File]::Open($OutputPath,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::Read)
$writer=[IO.StreamWriter]::new($stream,[Text.UTF8Encoding]::new($false));$writer.AutoFlush=$true
try {
    $writer.WriteLine('epoch_ms,resident_bytes')
    while(-not(Test-Path -LiteralPath $StopPath)){
        $process=Get-Process -Id $JavaProcessId -ErrorAction SilentlyContinue
        if(-not $process){break}
        $process.Refresh();$writer.WriteLine(([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds().ToString()+','+$process.WorkingSet64.ToString()))
        $process.Dispose()
        Start-Sleep -Milliseconds 100
    }
} finally {$writer.Dispose()}
