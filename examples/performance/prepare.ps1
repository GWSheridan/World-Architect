param([Parameter(Mandatory)][string]$BaselineJar, [Parameter(Mandatory)][string]$CandidateJar, [string]$OutputRoot='build/performance')
$ErrorActionPreference='Stop'
$baseline=(Resolve-Path -LiteralPath $BaselineJar).Path
$candidate=(Resolve-Path -LiteralPath $CandidateJar).Path
foreach($version in 'baseline','candidate') {
    foreach($profile in 'normal','void','single-block','layered','structure-heavy') {
        $run=Join-Path $OutputRoot "bench-$version/$profile"
        if(Test-Path -LiteralPath $run) {throw "Use a fresh output directory: $run"}
        New-Item -ItemType Directory -Path "$run/mods" -Force | Out-Null
        Copy-Item -LiteralPath "examples/performance/profiles/$profile/server.properties" -Destination $run
        # Running this acceptance harness acknowledges the same Minecraft EULA as runServer.
        'eula=true' | Set-Content -LiteralPath "$run/eula.txt" -Encoding utf8NoBOM
        $packs="examples/performance/profiles/$profile/datapacks"
        if(Test-Path -LiteralPath $packs) {New-Item -ItemType Directory -Path "$run/world" -Force | Out-Null; Copy-Item -LiteralPath $packs -Destination "$run/world/datapacks" -Recurse}
        Copy-Item -LiteralPath $(if($version -eq 'baseline'){$baseline}else{$candidate}) -Destination "$run/mods"
    }
}
