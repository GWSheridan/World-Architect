param([ValidateSet('baseline','candidate')][string]$Version='baseline', [string]$OutputRoot='build/performance', [string]$JavaHome=$env:JAVA_HOME, [string]$Jdk21=$env:JAVA21_HOME, [ValidateSet('normal','void','single-block','layered','structure-heavy')][string[]]$Profiles=@('normal','void','single-block','layered','structure-heavy'), [ValidateRange(4,8)][int]$WarmupSize=4, [ValidateRange(6,8)][int]$MeasuredSize=6, [ValidateRange(2,5)][int]$Samples=2)
$ErrorActionPreference='Stop'
$root=(Resolve-Path ($OutputRoot+'/bench-'+$Version)).Path
if (!$JavaHome) { throw 'Set JAVA_HOME to a Java 25 JDK' }; $env:JAVA_HOME=$JavaHome
if(Test-Path -LiteralPath '.gradle-local') { $env:GRADLE_USER_HOME=(Resolve-Path '.gradle-local').Path }
$env:TEMP=(Resolve-Path 'build/tmp').Path
$env:TMP=$env:TEMP
$toolchain=if($Jdk21){'"-Porg.gradle.java.installations.paths='+$Jdk21+'"'}else{''}
$summary="$root/performance.csv"
if (Test-Path -LiteralPath $summary) { throw 'A prior benchmark summary exists' }
function Start-Server([string]$fixture) {
    $start=[Diagnostics.ProcessStartInfo]::new()
    $start.FileName=$env:ComSpec
    $start.Arguments="/d /c gradlew.bat -I examples/performance/server.init.gradle runServer --no-daemon --offline -x compileJava -x processResources --console=plain -PtestWorldDirectory=`"$root/$fixture`" -PwaPerformance=$fixture $toolchain"
    $start.WorkingDirectory=(Get-Location).Path
    $start.UseShellExecute=$false
    $start.CreateNoWindow=$true
    $start.RedirectStandardInput=$true
    $start.RedirectStandardOutput=$true
    $start.RedirectStandardError=$true
    return [Diagnostics.Process]::Start($start)
}
function Send([string]$cmd) {
    $process.StandardInput.WriteLine($cmd)
    $process.StandardInput.Flush()
}
function Wait-ForBoot {
    $deadline=[DateTime]::UtcNow.AddSeconds(110)
    while (!$process.HasExited -and [DateTime]::UtcNow -lt $deadline) {
        if ((Test-Path -LiteralPath $log) -and (Select-String -LiteralPath $log -SimpleMatch 'Done (' -Quiet)) { return }
        Start-Sleep -Milliseconds 300
    }
    throw "Server did not boot: $log"
}
function Find-ServerJava([string]$name) {
    $markers=@(Get-ChildItem -LiteralPath "$root/$name" -Filter 'benchmark-jvm-*.log' -File)
    if ($markers.Count -ne 1) { throw "Could not identify the Java process for $name" }
    $match=[regex]::Match($markers[0].Name,'^benchmark-jvm-(\d+)\.log$')
    if (!$match.Success) { throw "Unexpected JVM memory log name: $($markers[0].Name)" }
    return [int]$match.Groups[1].Value
}
function Measure-Batch([int]$base,[int]$size,[string]$label) {
    $end=$base + 16*($size-1)
    $checks=[Collections.Generic.List[string]]::new()
    $checks.Add('execute')
    for ($z=$base;$z -le $end;$z+=16) {
        for ($x=$base;$x -le $end;$x+=16) { $checks.Add("if loaded $x 60 $z") }
    }
    $checks.Add("run say $label")
    $condition=$checks -join ' '
    $timer=[Diagnostics.Stopwatch]::StartNew()
    $peak=(Get-Process -Id $javaId).WorkingSet64
    Send "forceload add $base $base $end $end"
    do {
        Send $condition
        Start-Sleep -Milliseconds 200
        $peak=[math]::Max($peak,(Get-Process -Id $javaId).WorkingSet64)
        $done=Select-String -LiteralPath $log -SimpleMatch "[Server] $label" -Quiet
    } until ($done -or $process.HasExited -or $timer.Elapsed.TotalSeconds -ge 70)
    $timer.Stop()
    if (!$done) { throw "Chunks did not become entity-ticking for $label. See $log" }
    Send 'forceload remove all'
    return @{Elapsed=$timer.Elapsed.TotalSeconds;Resident=$peak}
}
foreach ($fixture in $Profiles) {
    $caseDirectory="$root/$fixture"
    $log="$caseDirectory/logs/latest.log"
    $regions="$caseDirectory/world/dimensions/minecraft/overworld/region"
    if ((Test-Path -LiteralPath $log) -or (Test-Path -LiteralPath $regions)) { throw "$fixture is not a fresh benchmark world" }
    $process=Start-Server $fixture
    $output=$process.StandardOutput.ReadToEndAsync()
    $errors=$process.StandardError.ReadToEndAsync()
    try {
        Wait-ForBoot
        $javaId=Find-ServerJava $fixture
        Measure-Batch 8000 $WarmupSize 'WA_BENCH_WARMUP' | Out-Null
        Start-Sleep -Milliseconds 500
        $idle=(Get-Process -Id $javaId).WorkingSet64
        foreach ($sample in 1..$Samples) {
            $offset=@(16000,-16000,32000,-32000,48000)[$sample-1]
            $measurement=Measure-Batch $offset $MeasuredSize "WA_BENCH_SAMPLE_$sample"
            $result=[PSCustomObject]@{
                Profile=$fixture
                Sample=$sample
                Chunks=$MeasuredSize*$MeasuredSize
                Seconds=[math]::Round($measurement.Elapsed,2)
                WarmWorkingSetMiB=[math]::Round($idle/1MB)
                PeakSampledWorkingSetMiB=[math]::Round($measurement.Resident/1MB)
            }
            $result | Export-Csv -LiteralPath $summary -NoTypeInformation -Append
            $result | Format-Table | Out-String | Write-Output
            Start-Sleep -Milliseconds 500
        }
        if ($fixture -eq 'structure-heavy') {
            Send 'execute positioned 16000 64 16000 run locate structure minecraft:desert_pyramid'
            Start-Sleep -Seconds 2
            if (!(Select-String -LiteralPath $log -Pattern 'nearest minecraft:desert_pyramid|nearest.*desert_pyramid' -Quiet)) {
                throw 'The structure workload did not locate its native desert pyramid'
            }
        }
        Send 'stop'
        if (!$process.WaitForExit(35000) -or $process.ExitCode -ne 0) { throw "$fixture did not stop cleanly" }
    } finally {
        if (!$process.HasExited) {
            Send 'stop'
            if (!$process.WaitForExit(15000)) { $process.Kill($true) }
        }
        $output.GetAwaiter().GetResult() | Set-Content -LiteralPath "$caseDirectory/console.log" -Encoding utf8NoBOM
        $errors.GetAwaiter().GetResult() | Set-Content -LiteralPath "$caseDirectory/console-errors.log" -Encoding utf8NoBOM
        $process.Dispose()
    }
}

