// Real disposable-server coverage for the batch runner. No third-party binaries are redistributed.
import {promises as fs,createWriteStream} from 'node:fs';
import {spawn} from 'node:child_process';
import {createHash} from 'node:crypto';
import {createServer} from 'node:net';
import {resolve,join,dirname,relative} from 'node:path';
import {fileURLToPath} from 'node:url';
import {baselineBytes} from '../validation/baseline.mjs';
import {gradle} from '../validation/process.mjs';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'../..'),args={};
for(let i=2;i<process.argv.length;i+=2){const k=process.argv[i],v=process.argv[i+1];if(!['--output','--network','--port'].includes(k)||!v||k in args)throw Error('Invalid batch case option');args[k]=v;}
const output=resolve(root,args['--output']||'build/ci-batch'),network=args['--network']||'offline',port=Number(args['--port']||25974);
if(!['online','offline'].includes(network)||!relative(root,output).startsWith('build'+(process.platform==='win32'?'\\':'/')))throw Error('Use a new build/ directory and online/offline network mode');
await fs.mkdir(output,{recursive:false});const inputs=join(output,'inputs');await fs.mkdir(inputs);
const sha=b=>createHash('sha256').update(b).digest('hex'),json=(p,v)=>fs.writeFile(p,JSON.stringify(v,null,2)+'\n',{flag:'wx'});
const version=(await fs.readFile(join(root,'gradle.properties'),'utf8')).match(/^mod_version=(.+)$/m)[1].trim();
const candidate=await fs.readFile(join(root,`build/libs/worldarchitect-${version}.jar`)),fixture=await fs.readFile(join(root,`build/validation-tools/worldarchitect-${version}-validation.jar`)),baseline=await baselineBytes(root,network);
for(const [name,bytes]of [['candidate.jar',candidate],['baseline.jar',baseline],['validation.jar',fixture]])await fs.writeFile(join(inputs,name),bytes,{flag:'wx'});
const prep=['-p','examples/api-fixture','jar','--no-daemon','--console=plain',`-PworldArchitectJar=${join(inputs,'baseline.jar')}`];if(network==='offline')prep.push('--offline');if(process.env.JAVA21_HOME)prep.push(`-Porg.gradle.java.installations.paths=${process.env.JAVA21_HOME}`);
const compiled=gradle(root,prep,join(output,'api-build.log'));if(await compiled.exit!==0)throw Error('Cannot compile the independent API consumer against 1.3');
await fs.mkdir(join(inputs,'plain'));await fs.mkdir(join(inputs,'api'));
const provider=await fs.readFile(join(root,'examples/api-fixture/build/libs/worldarchitect-api-fixture-1.0.0.jar'));await fs.writeFile(join(inputs,'api/provider.jar'),provider,{flag:'wx'});
function scenario(bundle,hash,seed){return {format:1,bundle:{file:bundle,sha256:hash},budgets:{timeout_seconds:120,heap_mib:1400},runs:[{name:'clean',seed,expect_startup:'accept',safe_spawn:true,restart:true,dimensions:['minecraft:overworld'],regions:[{dimension:'minecraft:overworld',chunk_x:-32,chunk_z:32,width:2,depth:2}],probes:[]}]};}
const legacyBytes=await fs.readFile(join(root,'examples/creator-starters/bundles/single-block-1.3.0.zip')),modernBytes=await fs.readFile(join(root,'examples/shared-regions/bundles/shared-regions-1.7.0.zip'));
await fs.writeFile(join(inputs,'legacy.zip'),legacyBytes,{flag:'wx'});await fs.writeFile(join(inputs,'modern.zip'),modernBytes,{flag:'wx'});
const legacy=scenario('legacy.zip',sha(legacyBytes),'9223372036854775807'),modern=scenario('modern.zip',sha(modernBytes),'-9223372036854775808');
modern.runs[0].regions=[{dimension:'minecraft:overworld',chunk_x:0,chunk_z:0,width:2,depth:2}];modern.runs[0].probes=[{dimension:'minecraft:overworld',x:1,y:64,z:1,block:'minecraft:diamond_block'}];
const bad=structuredClone(modern);bad.budgets.timeout_seconds=120;bad.runs[0].restart=false;bad.runs[0].probes[0].block='minecraft:gold_block';
for(const [name,value]of [['legacy',legacy],['modern',modern],['bad-probe',bad]])await json(join(inputs,name+'.json'),value);
const sources=[];for(const name of ['legacy','modern','bad-probe'])sources.push({name,file:name+'.json',sha256:sha(await fs.readFile(join(inputs,name+'.json'))),...(name==='legacy'?{}:{versions:['candidate']}),...(name==='bad-probe'?{expected_status:'failed',expected_error:'Block probe failed'}:{})});
const matrix={format:1,budgets:{max_jobs:8,timeout_seconds:1800},versions:[{name:'baseline',jar:'baseline.jar',sha256:sha(baseline),fixture:'validation.jar',fixture_sha256:sha(fixture)},{name:'candidate',jar:'candidate.jar',sha256:sha(candidate),fixture:'validation.jar',fixture_sha256:sha(fixture)}],scenarios:sources,providers:[{name:'plain',mods:'plain',files:{}},{name:'api',mods:'api',files:{'provider.jar':sha(provider)}}]};
await json(join(inputs,'matrix.json'),matrix);const results={format:1,status:'running',cases:[]};
const record=()=>fs.writeFile(join(output,'results.json'),JSON.stringify(results,null,2)+'\n');
let launches=0;
function start(file,destination){const log=createWriteStream(join(output,'process-'+String(launches++)+'.log'),{flags:'wx'}),child=spawn(process.execPath,[join(root,'tools/workbench/batch.mjs'),'--matrix',file,'--output',destination,'--network',network,'--port',String(port)],{cwd:root,windowsHide:true,stdio:['ignore','pipe','pipe','ipc']});let text='';for(const stream of [child.stdout,child.stderr])stream.on('data',b=>{text+=b;log.write(b);});const exit=new Promise((done,fail)=>{child.once('error',fail);child.once('close',code=>{log.end();done(code);});});return{child,exit,text:()=>text};}
async function releasedPort(){await new Promise((done,fail)=>{const listener=createServer();listener.once('error',fail);listener.listen(port,'127.0.0.1',()=>listener.close(done));});}
async function rejected(name,value,destination=join(output,name)){const file=join(inputs,name+'.json');await json(file,value);const run=start(file,destination),code=await run.exit;if(code===0)throw Error(name+' was accepted');if(name!=='existing-output'){try{await fs.lstat(destination);throw Error(name+' created output before preflight completed');}catch(e){if(e.code!=='ENOENT')throw e;}}results.cases.push({name,status:'passed',created_instance:false});await record();}
try{
 const wrong=structuredClone(matrix);wrong.versions[0].sha256='0'.repeat(64);await rejected('wrong-hash',wrong);
 const extra=structuredClone(matrix);await fs.writeFile(join(inputs,'plain/extra.jar'),'unexpected',{flag:'wx'});await rejected('extra-provider',extra);await fs.unlink(join(inputs,'plain/extra.jar'));
 const existing=join(output,'existing-output');await fs.mkdir(existing);await fs.writeFile(join(existing,'sentinel'),'keep',{flag:'wx'});await rejected('existing-output',matrix,existing);if((await fs.readdir(existing)).join()!=='sentinel')throw Error('Existing output changed');
 const escaped=join(output,'parent-link');await fs.symlink(root,escaped,process.platform==='win32'?'junction':'dir');await rejected('escaped-parent',matrix,join(escaped,'batch-output-must-not-exist'));await fs.unlink(escaped);
 const run=start(join(inputs,'matrix.json'),join(output,'matrix'));if(await run.exit!==0)throw Error('Real matrix failed: '+run.text());const report=JSON.parse(await fs.readFile(join(output,'matrix/results.json'),'utf8'));if(report.status!=='passed'||report.jobs.length!==8||report.jobs.some(j=>j.status!=='passed'))throw Error('Incomplete matrix report');results.cases.push({name:'versions-providers-new-rules-expected-failures',status:'passed',jobs:report.jobs.length});await record();
 const single=structuredClone(matrix);single.versions=[matrix.versions[1]];single.scenarios=[matrix.scenarios[0]];single.providers=[matrix.providers[0]];single.budgets={max_jobs:1,timeout_seconds:30};
 // Sixteen valid small runs ensure the global deadline interrupts a running batch.
 const long=structuredClone(legacy);long.runs=Array.from({length:16},(_,i)=>({...structuredClone(legacy.runs[0]),name:'run_'+i}));await json(join(inputs,'long.json'),long);single.scenarios=[{name:'long',file:'long.json',sha256:sha(await fs.readFile(join(inputs,'long.json')))}];
 await json(join(inputs,'deadline.json'),single);const deadline=start(join(inputs,'deadline.json'),join(output,'deadline'));if(await deadline.exit===0)throw Error('Global deadline was accepted');const timed=JSON.parse(await fs.readFile(join(output,'deadline/results.json'),'utf8'));if(timed.status!=='failed'||!timed.error.includes('deadline'))throw Error('Deadline did not fail explicitly');results.cases.push({name:'global-deadline',status:'passed'});await record();
 single.budgets.timeout_seconds=300;await json(join(inputs,'cancel.json'),single);const cancel=start(join(inputs,'cancel.json'),join(output,'cancel'));const until=Date.now()+180000;let ready=false;while(Date.now()<until){try{ready=(await fs.readFile(join(output,'cancel/job_01/instance/run_0/fresh.log'),'utf8')).includes('Done (');}catch(e){if(e.code!=='ENOENT')throw e;}if(ready)break;await new Promise(done=>setTimeout(done,200));}if(!ready){cancel.child.send({type:'workbench_abort'});await cancel.exit;throw Error('Cancellation fixture did not start its server');}cancel.child.send({type:'workbench_abort'});if(await cancel.exit===0)throw Error('Interrupted batch was accepted');const cancelled=JSON.parse(await fs.readFile(join(output,'cancel/results.json'),'utf8'));if(cancelled.status!=='failed'||!cancelled.error.includes('interrupted'))throw Error('Cancellation did not fail explicitly');results.cases.push({name:'active-server-cancellation',status:'passed'});results.status='passed';
 await releasedPort();results.cases.push({name:'server-port-released',status:'passed'});
}catch(e){results.status='failed';results.error=e.stack;process.exitCode=1;}finally{await record();console.log('Batch integration '+results.status+': '+relative(root,output));}
