// Node 24+, JDK 25. Fresh isolated worlds only; all outputs remain under build/.
import {promises as fs,createWriteStream} from 'node:fs';
import {spawn} from 'node:child_process';
import {resolve,relative,join,dirname,sep} from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import {gradle} from './process.mjs';
import {analyzeBenchmarks,analyzeControls} from './benchmark-analysis.mjs';

const root=resolve(dirname(fileURLToPath(import.meta.url)),'../..'),args=process.argv.slice(2);
if(args.length%2)throw Error('Use --option value pairs');
const flags=Object.fromEntries(Array.from({length:args.length/2},(_,i)=>args.slice(i*2,i*2+2)));
for(const flag of Object.keys(flags))if(!['--baseline','--candidate','--probe','--output','--pairs','--control-pairs','--warmup-size','--measured-size','--samples','--profiles','--port','--network','--purpose','--deadline-seconds'].includes(flag))throw Error('Unknown benchmark option '+flag);
for(const flag of ['--baseline','--candidate','--probe','--output'])if(!flags[flag])throw Error('Missing '+flag);
const number=(key,fallback,min,max)=>{const value=Number(flags[key]??fallback);if(!Number.isInteger(value)||value<min||value>max)throw Error('Invalid '+key);return value;};
const purpose=flags['--purpose']||'performance',network=flags['--network']||'offline',pairs=number('--pairs',3,1,5),controls=number('--control-pairs',1,1,3),warmup=number('--warmup-size',32,8,32),size=number('--measured-size',24,6,32),samples=number('--samples',5,2,5),port=number('--port',25919,1024,65535);
if(!['performance','smoke'].includes(purpose)||!['online','offline'].includes(network)||purpose==='performance'&&pairs<3)throw Error('Performance runs need at least three pairs; use --purpose smoke to check the runner');
const profiles=(flags['--profiles']||'normal,void,single-block,layered,structure-heavy').split(',');if(!profiles.length||new Set(profiles).size!==profiles.length||profiles.some(p=>!['normal','void','single-block','layered','structure-heavy'].includes(p)))throw Error('Invalid profiles');
const output=resolve(root,flags['--output']);if(!output.startsWith(join(root,'build')+sep))throw Error('Benchmark output must stay under build/');
for(const key of ['--baseline','--candidate','--probe']){const stat=await fs.stat(resolve(root,flags[key]));if(!stat.isFile()||stat.size>33554432)throw Error('Benchmark inputs must be JAR files within 32 MiB');}
const deadline=Date.now()+number('--deadline-seconds',7200,30,14400)*1000;
const bytes={baseline:await fs.readFile(resolve(root,flags['--baseline'])),candidate:await fs.readFile(resolve(root,flags['--candidate'])),probe:await fs.readFile(resolve(root,flags['--probe']))},sha=value=>createHash('sha256').update(value).digest('hex');
await fs.mkdir(output,{recursive:false});
const input={format:2,method:'direct_generation',purpose,pairs,control_pairs:controls,warmup_size:warmup,measured_size:size,samples,profiles,baseline_sha256:sha(bytes.baseline),candidate_sha256:sha(bytes.candidate),probe_sha256:sha(bytes.probe),generation_limit_percent:10,resident_limit_percent:15,heap_limit_percent:15,platform:process.platform,init_sha256:sha(await fs.readFile(join(root,'tools/validation/benchmark.init.gradle')))};
await fs.writeFile(join(output,'inputs.json'),JSON.stringify(input,null,2)+'\n',{flag:'wx'});
const results={format:2,status:'running',input,runs:[],compatibility:[]};let current,memory,interrupted=false;
const stop=()=>{interrupted=true;};process.once('SIGINT',stop);process.once('SIGTERM',stop);
const persist=()=>fs.writeFile(join(output,'results.json'),JSON.stringify(results,null,2)+'\n');
async function waiting(promise){let timer;try{return await Promise.race([promise,new Promise((_,reject)=>{timer=setInterval(()=>{if(interrupted||Date.now()>deadline)reject(Error(interrupted?'Benchmark interrupted':'Benchmark total deadline exceeded'));else fs.stat(join(output,'stop')).then(()=>reject(Error('Benchmark interrupted')),()=>{});},200);})]);}finally{clearInterval(timer);}}
async function json(path,seconds=30){const deadline=Date.now()+seconds*1000;while(Date.now()<deadline){if(interrupted||await fs.stat(join(output,'stop')).then(()=>true,()=>false))throw Error('Benchmark interrupted');try{return JSON.parse(await fs.readFile(path,'utf8'));}catch(error){if(error.code!=='ENOENT'&&!(error instanceof SyntaxError))throw error;}if(current?.child.exitCode!==null)throw Error('Server exited before its benchmark record');await new Promise(done=>setTimeout(done,100));}throw Error('Benchmark record timed out');}
async function stopMemory(directory){if(!memory)return;if(memory.exitCode!==null){memory=undefined;return;}await fs.writeFile(join(directory,'memory-stop'),'stop\n');await Promise.race([new Promise(done=>memory.once('close',done)),new Promise(done=>setTimeout(done,3000))]);if(memory.exitCode===null)memory.kill();memory=undefined;}
function toolchain(){return process.env.JAVA21_HOME?[`-Porg.gradle.java.installations.paths=${process.env.JAVA21_HOME}`]:[];}
try{
 matrix: for(const kind of ['control','comparison'])for(let pair=1;pair<=(kind==='control'?controls:pairs);pair++){
  if(kind==='comparison'&&pair===1){results.controls=analyzeControls(input,results.runs);if(results.controls.status!=='passed'){results.analysis=results.controls;results.status='inconclusive';await persist();await fs.writeFile(join(output,'comparison.json'),JSON.stringify(results.analysis,null,2)+'\n',{flag:'wx'});console.log('Identical-JAR controls were inconclusive; candidate comparisons were not started.');process.exitCode=1;break matrix;}}
  const directory=join(output,kind,`pair-${pair}`),order=pair%2?['baseline','candidate']:['candidate','baseline'];
  for(const role of order)for(const profile of profiles){
   if(interrupted)throw Error('Benchmark interrupted');
   if(sha(await fs.readFile(join(root,'tools/validation/benchmark.init.gradle')))!==input.init_sha256)throw Error('Benchmark runtime configuration changed');
   const server=join(directory,'bench-'+role,profile);await fs.mkdir(join(server,'mods'),{recursive:true});
   const jar=kind==='control'?bytes.baseline:bytes[role];await fs.writeFile(join(server,'mods/worldarchitect.jar'),jar,{flag:'wx'});await fs.writeFile(join(server,'mods/benchmark-probe.jar'),bytes.probe,{flag:'wx'});
   let properties=await fs.readFile(join(root,'examples/performance/profiles',profile,'server.properties'),'utf8');properties=properties.split(/\r?\n/).filter(line=>!/^(server-port|max-tick-time|management-server|rcon\.|log-ips)=/.test(line)).join('\n');
   await fs.writeFile(join(server,'server.properties'),properties+`\nserver-port=${port}\nmax-tick-time=-1\nmanagement-server-enabled=false\nlog-ips=false\n`);await fs.writeFile(join(server,'eula.txt'),'eula=true\n');
   try{await fs.cp(join(root,'examples/performance/profiles',profile,'datapacks'),join(server,'world/datapacks'),{recursive:true});}catch(error){if(error.code!=='ENOENT')throw error;}
   const run={kind,pair,role,profile,status:'running',jar_sha256:sha(jar),probe_sha256:input.probe_sha256,samples:[]};results.runs.push(run);await persist();
   const launch=['-I','tools/validation/benchmark.init.gradle','runServer','--no-daemon','-x','compileJava','-x','processResources','--console=plain',`-PtestWorldDirectory=${server}`,...toolchain()];if(network==='offline')launch.push('--offline');
   current=gradle(root,launch,join(server,'console.log'));
   try{
    await waiting(current.wait('Done (',180));run.header=await json(join(server,'benchmark-process.json'));
    if(process.platform==='win32'){
     const log=createWriteStream(join(server,'memory-console.log'));memory=spawn('pwsh',['-NoLogo','-NoProfile','-NonInteractive','-File',join(root,'tools/validation/resident-memory.ps1'),'-JavaProcessId',String(run.header.pid),'-OutputPath',join(server,'resident.csv'),'-StopPath',join(server,'memory-stop')],{windowsHide:true,stdio:['ignore','pipe','pipe']});memory.stdout.pipe(log,{end:false});memory.stderr.pipe(log,{end:false});memory.once('close',()=>log.end());memory.once('error',error=>{run.memory_error=error.message;});
    }
    for(let id=0;id<=samples;id++){
     const base=id===0?50000:[1000,-1000,2000,-2000,3000][id-1],width=id===0?warmup:size;
     current.send(`wa_benchmark ${base} ${width} ${id}`);await waiting(current.wait('WA_BENCHMARK_SAMPLE '+id,180,['WA_BENCHMARK_FAILED']));const sample=await json(join(server,`benchmark-sample-${id}.json`));
     if(sample.id!==id||sample.chunks!==width*width)throw Error('Unexpected benchmark sample identity');
     if(id>0){if(process.platform==='win32'){const csv=await fs.readFile(join(server,'resident.csv'),'utf8').catch(()=>''),values=csv.trim().split(/\r?\n/).slice(1).map(line=>line.split(',').map(Number)).filter(([time,rss])=>time>=sample.started_epoch_ms&&time<=sample.finished_epoch_ms&&rss>0).map(row=>row[1]);sample.sampled_resident_peak_bytes=values.length?Math.max(...values):null;}run.samples.push(sample);await persist();}
     else run.warmup=sample;
     await new Promise(done=>setTimeout(done,500));
    }
    current.send('stop');if(await current.exit!==0)throw Error('Benchmark server did not stop cleanly');run.status='passed';
   }finally{await stopMemory(server);await current.stop();current=undefined;await persist();}
   console.log(`WA_BENCHMARK_RUN ${kind} ${pair} ${role} ${profile}`);
  }
  if(purpose==='performance'){
   const log=join(directory,'terrain-comparison.log'),auditArgs=['auditBenchmarkWorlds','--console=plain',`-PbenchmarkBaseline=${join(directory,'bench-baseline')}`,`-PbenchmarkCandidate=${join(directory,'bench-candidate')}`,`-PbenchmarkSize=${size}`,`-PbenchmarkSamples=${samples}`,`-PbenchmarkProfiles=${profiles.join(',')}`,...toolchain()];if(network==='offline')auditArgs.push('--offline');
   const audit=gradle(root,auditArgs,log),code=await audit.exit;let native=false;
   if(code!==0){const text=await fs.readFile(log,'utf8');for(const profile of profiles.filter(p=>!['normal','structure-heavy'].includes(p)))if(!text.includes(`${profile}: PASS, ${size*size*samples} identical managed chunks`))throw Error('Managed benchmark terrain changed');if(!/AssertionError: structure-heavy /.test(text))throw Error('Benchmark terrain comparison failed outside the native pyramid case');
    const args=['auditNativePyramidPair','--console=plain',`-PbaselineWorld=${join(directory,'bench-baseline/structure-heavy/world')}`,`-PcandidateWorld=${join(directory,'bench-candidate/structure-heavy/world')}`,`-PbenchmarkSize=${size}`,`-PbenchmarkSamples=${samples}`,...toolchain()];if(network==='offline')args.push('--offline');const narrow=gradle(root,args,join(directory,'native-pyramid-comparison.log'));if(await narrow.exit!==0)throw Error('Native pyramid comparison failed');native=true;
   }
   results.compatibility.push({kind,pair,status:'passed',native_pyramid_diagnostic:native});await persist();
  }
 }
 if(purpose==='smoke'&&results.runs.some(run=>run.samples.some(sample=>sample.sampled_resident_peak_bytes===null)))throw Error('The benchmark smoke check needs working resident-memory sampling');
 if(results.status!=='inconclusive'){
 results.analysis=analyzeBenchmarks(input,results.runs);
 if(results.compatibility.some(r=>r.kind==='comparison'&&r.native_pyramid_diagnostic)&&!results.compatibility.some(r=>r.kind==='control'&&r.native_pyramid_diagnostic))throw Error('Native differences need an identical-JAR reproduction');
 results.status=purpose==='smoke'?'smoke_complete':results.analysis.status;await persist();await fs.writeFile(join(output,'comparison.json'),JSON.stringify(results.analysis,null,2)+'\n',{flag:'wx'});console.log(JSON.stringify(results.analysis,null,2));if(purpose==='performance'&&results.status!=='passed')process.exitCode=1;
 }
}catch(error){results.status='failed';results.error=error.message;await persist();console.error(error.message);process.exitCode=1;}
finally{process.removeListener('SIGINT',stop);process.removeListener('SIGTERM',stop);if(current)await current.stop();}
