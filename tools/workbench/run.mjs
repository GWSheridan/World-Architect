// Run from the source distribution with Java 25, Java 21 and Node 22+ installed.
import {promises as fs} from 'node:fs';
import {resolve,join,dirname,relative,isAbsolute,sep} from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import {gradle} from '../validation/process.mjs';
import {readScenario} from './scenario.mjs';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'../..'),args={};
for(let i=2;i<process.argv.length;i+=2){const k=process.argv[i],v=process.argv[i+1];if(!['--scenario','--jar','--fixture','--output','--mods','--network','--port'].includes(k)||!v||k in args)throw Error('Invalid or repeated option');args[k]=v;}
for(const k of ['--scenario','--jar','--fixture','--output'])if(!args[k])throw Error('Use --scenario FILE --jar JAR --fixture VALIDATION_JAR --output NEW_BUILD_DIRECTORY [--mods DIRECTORY] [--network online]');
if(!['online','offline'].includes(args['--network']||'offline'))throw Error('Invalid network mode');
const port=Number(args['--port']||25974);if(!Number.isInteger(port)||port<1024||port>65535)throw Error('Invalid loopback port');
const sha=b=>createHash('sha256').update(b).digest('hex'),scenarioPath=resolve(root,args['--scenario']),scenario=await readScenario(scenarioPath);
async function input(path,max=33554432){const st=await fs.lstat(path);if(!st.isFile()||st.isSymbolicLink()||st.size>max)throw Error('Input must be a bounded regular file');return fs.readFile(path);}
const bundle=await input(join(dirname(scenarioPath),scenario.bundle.file));if(sha(bundle)!==scenario.bundle.sha256)throw Error('Bundle SHA-256 does not match scenario');
const jar=await input(resolve(root,args['--jar'])),fixture=await input(resolve(root,args['--fixture'])),mods=[];
if(args['--mods'])for(const name of (await fs.readdir(resolve(root,args['--mods']))).filter(n=>n.endsWith('.jar')).sort()){if(!/^[a-zA-Z0-9+_.-]{1,160}\.jar$/.test(name)||['worldarchitect.jar','validation.jar'].includes(name))throw Error('Invalid or reserved mod name');mods.push({name,bytes:await input(join(resolve(root,args['--mods']),name),134217728)});}
const build=await fs.realpath(join(root,'build')),output=resolve(root,args['--output']),rel=relative(build,output);
if(!rel||isAbsolute(rel)||rel==='..'||rel.startsWith('..'+sep))throw Error('Output must be a new directory under build/');
// Resolve the existing parent too: a symlink must not redirect job writes outside build/.
const parent=await fs.realpath(dirname(output)),parentRel=relative(build,parent);if(isAbsolute(parentRel)||parentRel==='..'||parentRel.startsWith('..'+sep))throw Error('Output parent escapes build/');
await fs.mkdir(output,{recursive:false});await fs.writeFile(join(output,'workbench-instance.json'),JSON.stringify({format:1,scenario_sha256:sha(await fs.readFile(scenarioPath))})+'\n',{flag:'wx'});
const results={format:1,jar_sha256:sha(jar),bundle_sha256:sha(bundle),mods:mods.map(m=>({file:m.name,sha256:sha(m.bytes)})),runs:[],status:'running'};let active;
const record=()=>fs.writeFile(join(output,'results.json'),JSON.stringify(results,null,2)+'\n');
async function pins(server){const dir=join(server,'world/data'),p={};for(const name of ['worldarchitect-generation-v1.dat','minecraft/world_gen_settings.dat'])p[name]=sha(await fs.readFile(join(dir,name)));const archive=join(dir,'worldarchitect/generation');try{for(const n of (await fs.readdir(archive)).filter(n=>n.endsWith('.zip')).sort())p[n]=sha(await fs.readFile(join(archive,n)));}catch(e){if(e.code!=='ENOENT')throw e;}return p;}
async function bounded(promise,seconds){let timer;try{return await Promise.race([promise,new Promise((_,reject)=>{timer=setTimeout(()=>reject(Error('Job exceeded its time budget')),seconds*1000);})]);}finally{clearTimeout(timer);}}
try{
 for(const job of scenario.runs){
    const dir=join(output,job.name),server=join(dir,'server'),config=join(server,'config/worldarchitect');await fs.mkdir(join(server,'mods'),{recursive:true});await fs.mkdir(join(config,'bundles'),{recursive:true});
    for(const [name,bytes]of [['worldarchitect.jar',jar],['validation.jar',fixture],...mods.map(m=>[m.name,m.bytes])])await fs.writeFile(join(server,'mods',name),bytes,{flag:'wx'});
    await fs.writeFile(join(config,'bundles/preset.zip'),bundle,{flag:'wx'});await fs.writeFile(join(config,'developer.json'),JSON.stringify({format:1,bundle:'preset.zip',developer_mode:false})+'\n',{flag:'wx'});await fs.writeFile(join(config,'workbench-job.json'),JSON.stringify(job)+'\n',{flag:'wx'});
    await fs.writeFile(join(server,'eula.txt'),'eula=true\n',{flag:'wx'});await fs.writeFile(join(server,'server.properties'),`server-ip=127.0.0.1\nserver-port=${port}\nonline-mode=false\nwhite-list=true\nenforce-whitelist=true\nmax-players=1\nview-distance=2\nsimulation-distance=2\nlog-ips=false\nmanagement-server-enabled=false\nenable-rcon=false\npause-when-empty-seconds=-1\nlevel-seed=${job.seed}\n`,{flag:'wx'});
    let original;const result={name:job.name,status:'running',stages:[]};results.runs.push(result);await record();
    for(const stage of (job.restart&&job.expect_startup==='accept'?['fresh','restart']:['fresh'])){
        const params=['-I','tools/validation/server.init.gradle','runValidationServer','--console=plain',`-PtestWorldDirectory=${server}`,`-PworkbenchHeapMib=${scenario.budgets.heap_mib}`];if(args['--network']!=='online')params.push('--offline');if(process.env.JAVA21_HOME)params.push(`-Porg.gradle.java.installations.paths=${process.env.JAVA21_HOME}`);
        const log=join(dir,stage+'.log');active=gradle(root,params,log);
        if(job.expect_startup==='reject'){
            const exit=await bounded(active.exit,scenario.budgets.timeout_seconds);const text=await fs.readFile(log,'utf8');if(exit===0||text.includes('Done (')||!text.includes(job.expected_error))throw Error('Startup rejection did not match the expected failure');result.stages.push({stage,expected_rejection:true});active=undefined;break;
        }
        await bounded((async()=>{await active.wait('Done (',scenario.budgets.timeout_seconds);active.send('wa_validate workbench');await active.wait('WA_WORKBENCH_PASS',scenario.budgets.timeout_seconds);active.send('save-all flush');await active.wait('Saved the game');active.send('stop');if(await active.exit!==0)throw Error('Server exited with an error');})(),scenario.budgets.timeout_seconds);
        active=undefined;const current=await pins(server);if(original&&JSON.stringify(original)!==JSON.stringify(current))throw Error('Pinned generation changed across restart');original=current;result.stages.push({stage,status:'passed'});
    }
    result.generation_sha256=original;result.status='passed';await record();
 }
 results.status='passed';
}catch(error){results.status='failed';results.error=error.message;const job=results.runs.at(-1);if(job&&job.status==='running'){job.status='failed';job.error=error.message;}process.exitCode=1;}finally{if(active)await active.stop();await record();console.log(`Workbench ${results.status}: ${relative(root,output)}/results.json`);}
