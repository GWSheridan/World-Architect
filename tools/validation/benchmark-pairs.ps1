param(
    [Parameter(Mandatory)][string]$BaselineJar,
    [Parameter(Mandatory)][string]$CandidateJar,
    [string]$ProbeJar,
    [string]$OutputRoot='build/performance-comparison',
    [string]$JavaHome=$env:JAVA_HOME,
    [string]$Jdk21=$env:JAVA21_HOME,
    [ValidateRange(8,32)][int]$WarmupSize=32,
    [ValidateRange(6,32)][int]$MeasuredSize=24,
    [ValidateRange(2,5)][int]$Samples=5,
    [ValidateRange(1,5)][int]$Pairs=3,
    [ValidateRange(1,3)][int]$ControlPairs=1,
    [ValidateSet('normal','void','single-block','layered','structure-heavy')][string[]]$Profiles=@('normal','void','single-block','layered','structure-heavy'),
    [ValidateSet('performance','smoke')][string]$Purpose='performance',
    [ValidateSet('offline','online')][string]$Network='offline',
    [ValidateRange(1024,65535)][int]$Port=25919
)
$ErrorActionPreference='Stop'
if(-not $JavaHome){throw 'Set JAVA_HOME to a Java 25 JDK.'}
$projectRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$previousLocation=Get-Location
try {
    Set-Location -LiteralPath $projectRoot
    $env:JAVA_HOME=$JavaHome
    if($Jdk21){$env:JAVA21_HOME=$Jdk21}
    if(-not $ProbeJar){$version=(Get-Content -LiteralPath 'gradle.properties'|Where-Object {$_ -like 'mod_version=*'}).Split('=',2)[1];$ProbeJar="build/validation-tools/worldarchitect-$version-benchmark-probe.jar"}
    if(-not(Test-Path -LiteralPath $ProbeJar)){throw 'Build the separate fixture with gradlew benchmarkProbeJar first.'}
    $arguments=@('tools/validation/benchmark-pairs.mjs','--baseline',$BaselineJar,'--candidate',$CandidateJar,'--probe',$ProbeJar,'--output',$OutputRoot,'--warmup-size',"$WarmupSize",'--measured-size',"$MeasuredSize",'--samples',"$Samples",'--pairs',"$Pairs",'--control-pairs',"$ControlPairs",'--profiles',($Profiles -join ','),'--purpose',$Purpose,'--network',$Network,'--port',"$Port")
    & node @arguments
    if($LASTEXITCODE -ne 0){throw 'Benchmark failed, is inconclusive, or needs investigation. Retain the complete output.'}
} finally {Set-Location -LiteralPath $previousLocation}
