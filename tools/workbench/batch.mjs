// Fingerprinted matrices run sequentially, using the existing disposable-instance runner.
import {promises as fs,createWriteStream} from 'node:fs';
import {resolve,join,dirname,relative,isAbsolute,sep} from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import {spawn} from 'node:child_process';
import {validateScenario} from './scenario.mjs';
import {validateBatch,expandBatch,batchHtml,acceptsExpectedFailure} from './batch-schema.mjs';

const root=resolve(dirname(fileURLToPath(import.meta.url)),'../..'),args={};
for(let i=2;i<process.argv.length;i+=2){const key=process.argv[i],value=process.argv[i+1];if(!['--matrix','--output','--network','--port'].includes(key)||!value||key in args)throw Error('Invalid or repeated batch option');args[key]=value;}
if(!args['--matrix']||!args['--output']||!['online','offline'].includes(args['--network']||'offline'))throw Error('Use --matrix FILE --output NEW_BUILD_DIRECTORY [--network online] [--port NUMBER]');
const port=Number(args['--port']||25974);if(!Number.isInteger(port)||port<1024||port>65535)throw Error('Invalid loopback port');
const sha=bytes=>createHash('sha256').update(bytes).digest('hex');let preparedBytes=0;
async function input(file,expected,max){const stat=await fs.lstat(file);if(!stat.isFile()||stat.isSymbolicLink()||stat.size>max)throw Error('Batch input is not a bounded regular file');const bytes=await fs.readFile(file);if(expected&&sha(bytes)!==expected)throw Error('Batch input fingerprint differs');preparedBytes+=bytes.length;if(preparedBytes>536870912)throw Error('Batch inputs exceed the 512 MiB shared budget');return bytes;}
const matrixPath=resolve(root,args['--matrix']),matrixBytes=await input(matrixPath,null,65536),base=dirname(matrixPath),matrix=validateBatch(JSON.parse(matrixBytes.toString('utf8')));
const scenarios=new Map(),bundles=new Map(),versions=new Map(),providers=new Map();
for(const source of matrix.scenarios){const file=resolve(base,source.file),bytes=await input(file,source.sha256,262144);const scenario=validateScenario(JSON.parse(bytes.toString('utf8')));scenarios.set(source.name,scenario);bundles.set(source.name,await input(join(dirname(file),scenario.bundle.file),scenario.bundle.sha256,33554432));}
for(const version of matrix.versions)versions.set(version.name,{jar:await input(resolve(base,version.jar),version.sha256,33554432),fixture:await input(resolve(base,version.fixture),version.fixture_sha256,33554432)});
for(const provider of matrix.providers||[]){const directory=resolve(base,provider.mods),files=(await fs.readdir(directory)).filter(n=>n.endsWith('.jar')).sort();if(JSON.stringify(files)!==JSON.stringify(Object.keys(provider.files).sort()))throw Error('Provider directory differs from its manifest');const data=[];for(const file of files)data.push({name:file,bytes:await input(join(directory,file),provider.files[file],134217728)});providers.set(provider.name,data);}
const expanded=expandBatch(matrix,scenarios);
const build=await fs.realpath(join(root,'build')),output=resolve(root,args['--output']);
function inside(path){const rel=relative(build,path);return rel&&!isAbsolute(rel)&&rel!=='..'&&!rel.startsWith('..'+sep);}
if(!inside(output)||(!inside(await fs.realpath(dirname(output)))&&await fs.realpath(dirname(output))!==build))throw Error('Batch output must be a new directory under build/');
try{await fs.lstat(output);throw Error('Batch output already exists');}catch(e){if(e.code!=='ENOENT')throw e;}
await fs.mkdir(output,{recursive:false});
const report={format:1,status:'running',matrix_sha256:sha(matrixBytes),requested_chunks:expanded.chunks,inputs:{versions:matrix.versions,providers:matrix.providers||[],scenarios:matrix.scenarios},jobs:expanded.jobs.map(({document,...job})=>({...job,status:'pending'}))};
const record=async()=>{await fs.writeFile(join(output,'results.json'),JSON.stringify(report,null,2)+'\n');await fs.writeFile(join(output,'report.html'),batchHtml(report));};
let active,interrupted=false,abortForce;
function forceStop(child){if(child.exitCode!==null||child.signalCode!==null)return;if(process.platform==='win32')spawn('taskkill',['/PID',String(child.pid),'/T','/F'],{windowsHide:true,stdio:'ignore'});else try{process.kill(-child.pid,'SIGKILL');}catch(e){if(e.code!=='ESRCH')throw e;}}
function abort(){if(interrupted)return;interrupted=true;const child=active;if(child?.connected)child.send({type:'workbench_abort'});if(child)abortForce=setTimeout(()=>forceStop(child),30000);}
process.once('SIGINT',abort);process.once('SIGTERM',abort);
process.on('message',message=>{if(message?.type==='workbench_abort')abort();});
async function launch(params,logFile,remaining){
    const log=createWriteStream(logFile,{flags:'wx'}),child=spawn(process.execPath,[join(root,'tools/workbench/run.mjs'),...params],{cwd:root,windowsHide:true,detached:process.platform!=='win32',stdio:['ignore','pipe','pipe','ipc']});active=child;
    child.stdout.pipe(log,{end:false});child.stderr.pipe(log,{end:false});let timedOut=false,force;
    const timer=setTimeout(()=>{timedOut=true;if(child.connected)child.send({type:'workbench_abort'});force=setTimeout(()=>forceStop(child),30000);},remaining);
    try{const code=await new Promise((done,fail)=>{child.once('error',fail);child.once('close',done);});return {code,timedOut};}
    finally{clearTimeout(timer);clearTimeout(force);clearTimeout(abortForce);log.end();active=undefined;}
}
async function failureText(instance,result){let text=JSON.stringify(result);for(const run of result.runs||[])for(const stage of ['fresh','restart']){try{const file=join(instance,run.name,stage+'.log'),stat=await fs.stat(file);const handle=await fs.open(file);try{const length=Math.min(stat.size,8388608),buffer=Buffer.alloc(length);await handle.read(buffer,0,length,Math.max(0,stat.size-length));text+='\n'+buffer.toString('utf8');}finally{await handle.close();}}catch(e){if(e.code!=='ENOENT')throw e;}}return text;}
const started=performance.now();
try{
    await record();
    for(let i=0;i<expanded.jobs.length;i++){
        if(interrupted)throw Error('Batch interrupted');const remaining=matrix.budgets.timeout_seconds*1000-(performance.now()-started);if(remaining<=0)throw Error('Batch deadline exhausted');
        const job=expanded.jobs[i],row=report.jobs[i],directory=join(output,job.name),instance=join(directory,'instance');await fs.mkdir(directory);
        const version=versions.get(job.version),scenarioFile=join(directory,'scenario.json');
        await fs.writeFile(scenarioFile,JSON.stringify(job.document)+'\n',{flag:'wx'});await fs.writeFile(join(directory,job.document.bundle.file),bundles.get(job.scenario),{flag:'wx'});
        const jar=join(directory,'worldarchitect.jar'),fixture=join(directory,'validation.jar');await fs.writeFile(jar,version.jar,{flag:'wx'});await fs.writeFile(fixture,version.fixture,{flag:'wx'});
        const params=['--scenario',scenarioFile,'--jar',jar,'--fixture',fixture,'--output',instance,'--network',args['--network']||'offline','--port',String(port)];
        if(providers.has(job.provider)){const mods=join(directory,'mods');await fs.mkdir(mods);for(const mod of providers.get(job.provider))await fs.writeFile(join(mods,mod.name),mod.bytes,{flag:'wx'});params.push('--mods',mods);}
        row.status='running';await record();const begin=performance.now(),outcome=await launch(params,join(directory,'runner.log'),Math.max(1,matrix.budgets.timeout_seconds*1000-(performance.now()-started)));row.elapsed_ms=Math.round(performance.now()-begin);
        let observed;try{observed=JSON.parse(await fs.readFile(join(instance,'results.json'),'utf8'));}catch(e){row.error='Runner did not produce a complete result';}
        row.actual=outcome.code===0&&observed?.status==='passed'?'passed':'failed';row.exit_code=outcome.code;row.result=relative(output,join(instance,'results.json'));row.scenario_sha256=sha(await fs.readFile(scenarioFile));
        row.failure_kind=outcome.timedOut?'timeout':interrupted?'interrupted':observed?.failure_kind;
        const expectedFailure=acceptsExpectedFailure(job,observed,outcome,interrupted,job.expected==='failed'&&observed?await failureText(instance,observed):'');
        row.status=!outcome.timedOut&&!interrupted&&(job.expected==='passed'?row.actual==='passed':expectedFailure)?'passed':'failed';
        if(observed){row.jar_sha256=observed.jar_sha256;row.bundle_sha256=observed.bundle_sha256;row.mods=observed.mods;row.runs=observed.runs;}
        await record();console.log(`${job.name}: ${job.scenario} / ${job.version} / ${job.provider}: ${row.status}`);
        if(outcome.timedOut)throw Error('Batch deadline exhausted; interrupted results cannot count as expected failures');
    }
    if(interrupted)throw Error('Batch interrupted');report.status=report.jobs.every(j=>j.status==='passed')?'passed':'failed';if(report.status==='failed')report.error='One or more jobs did not match their expected result';
}catch(error){report.status='failed';report.error=error.message;const running=report.jobs.find(j=>j.status==='running');if(running)running.status='failed';}
finally{if(active?.connected)active.send({type:'workbench_abort'});await record();console.log(`Batch ${report.status}: ${relative(root,output)}/report.html`);if(report.status!=='passed')process.exitCode=1;if(process.connected)process.disconnect();}
