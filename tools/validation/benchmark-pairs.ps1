param(
    [Parameter(Mandatory)][string]$BaselineJar,
    [Parameter(Mandatory)][string]$CandidateJar,
    [string]$OutputRoot='build/performance-comparison',
    [string]$JavaHome=$env:JAVA_HOME,
    [string]$Jdk21=$env:JAVA21_HOME,
    [ValidateRange(4,8)][int]$WarmupSize=8,
    [ValidateRange(6,8)][int]$MeasuredSize=8,
    [ValidateRange(2,5)][int]$Samples=5
)
$ErrorActionPreference='Stop'
if(-not $IsWindows){throw 'The resident-memory sampler requires Windows. Dedicated and compatibility runners support both platforms.'}
if(-not $JavaHome){throw 'Set JAVA_HOME to Java 25.'}
$projectRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$destination=[IO.Path]::GetFullPath((Join-Path $projectRoot $OutputRoot))
if(-not $destination.StartsWith((Join-Path $projectRoot 'build')+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Benchmark output must stay under build/.'}
if(Test-Path -LiteralPath $destination){throw 'Use a new output directory.'}
$previous=Get-Location
try {
    Set-Location -LiteralPath $projectRoot
    $baseline=(Resolve-Path -LiteralPath $BaselineJar).Path;$candidate=(Resolve-Path -LiteralPath $CandidateJar).Path
    $env:JAVA_HOME=$JavaHome
    $inputs=@{baseline_sha256=(Get-FileHash -LiteralPath $baseline).Hash.ToLower();candidate_sha256=(Get-FileHash -LiteralPath $candidate).Hash.ToLower();pairs=3;profiles=@('normal','void','single-block','layered','structure-heavy');warmup_chunks=$WarmupSize*$WarmupSize;measured_size=$MeasuredSize;samples=$Samples;chunks_per_sample=$MeasuredSize*$MeasuredSize;generation_limit_percent=10;memory_limit_percent=15}
    New-Item -ItemType Directory -Path $destination|Out-Null
    $inputs|ConvertTo-Json -Depth 5|Set-Content -LiteralPath (Join-Path $destination 'inputs.json') -Encoding utf8NoBOM
    foreach($pair in 1,2,3){
        $directory=Join-Path $destination "pair-$pair"
        & ./examples/performance/prepare.ps1 -BaselineJar $baseline -CandidateJar $candidate -OutputRoot $directory
        $order=if($pair%2 -eq 1){@('baseline','candidate')}else{@('candidate','baseline')}
        foreach($version in $order){
            & ./examples/performance/benchmark.ps1 -Version $version -OutputRoot $directory -JavaHome $JavaHome -Jdk21 $Jdk21 -WarmupSize $WarmupSize -MeasuredSize $MeasuredSize -Samples $Samples
            if(-not $?){throw "Benchmark failed: $pair/$version"}
        }
        $toolchain=@();if($Jdk21){$toolchain+="-Porg.gradle.java.installations.paths=$Jdk21"}
        & ./gradlew.bat auditBenchmarkWorlds --offline --console=plain "-PbenchmarkBaseline=$directory/bench-baseline" "-PbenchmarkCandidate=$directory/bench-candidate" @toolchain
        if($LASTEXITCODE -ne 0){throw "Saved-region comparison failed: $pair"}
    }
    & node ./tools/validation/compare-performance.mjs $destination
    if($LASTEXITCODE -ne 0){throw 'A benchmark threshold needs investigation.'}
} finally {Set-Location -LiteralPath $previous}
