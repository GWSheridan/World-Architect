import {promises as fs} from 'node:fs';
import {spawn} from 'node:child_process';
import {createHash} from 'node:crypto';
import {resolve,join,dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'../..');
const version=(await fs.readFile(join(root,'gradle.properties'),'utf8')).match(/^mod_version=(.+)$/m)[1].trim();
const directory=join(root,'build/ci-compatibility');await fs.mkdir(directory,{recursive:false});
await import('./fetch-mods.mjs');
const lock=JSON.parse(await fs.readFile(join(root,'tools/validation/mods.lock.json'),'utf8'));
const baseline=join(directory,'worldarchitect-1.0.0.jar');
const response=await fetch('https://github.com/GWSheridan/World-Architect/releases/download/v1.0.0/worldarchitect-1.0.0.jar');
if(!response.ok)throw new Error('Cannot fetch the published baseline');
const bytes=Buffer.from(await response.arrayBuffer());
if(createHash('sha256').update(bytes).digest('hex')!=='14fe208d2fed07e8dec98e33b1f06c519cb35a81a95412e60b1c808d94b332cd')throw new Error('Published baseline checksum mismatch');
await fs.writeFile(baseline,bytes,{flag:'wx'});
const matrix={format:1,platform:process.platform,candidate:version,baseline:'1.0.0',cases:[],status:'passed'};
function cause(text){return text.split(/\r?\n/).map(line=>line.match(/(?:Caused by:|NoSuchMethodError:|NoClassDefFoundError:|Mod \S+ requires)\s*(.+)/)?.[0]).filter(Boolean)[0]?.replace(/\s+~?\[[^\]]+\].*$/,'').trim()||null;}
for(const [pack,projects] of Object.entries({terralith:['8oi3bsk5','XaDC71GB'],biomesoplenty:['HXF82T3G','s3dmwKy5','kkmrDlKT']})) {
    const mods=join(directory,pack);await fs.mkdir(mods);
    const inputs=lock.filter(entry=>projects.includes(entry.project));
    if(inputs.length!==projects.length)throw new Error('Incomplete pinned dependency set');
    for(const input of inputs)await fs.copyFile(join(root,'build/compatibility-inputs',input.filename),join(mods,input.filename));
    for(const profile of ['normal','single-block']) {
        const results={};
        for(const kind of ['baseline','candidate']) {
            const output=`build/ci-compat-${pack}-${kind}-${profile}`;
            const args=['tools/validation/run.mjs','--jar',kind==='baseline'?baseline:`build/libs/worldarchitect-${version}.jar`,'--fixture',`build/validation-tools/worldarchitect-${version}-validation.jar`,'--output',output,'--profile',profile,'--mods',mods,'--suite','compatibility','--network','online'];
            const code=await new Promise((accept,reject)=>{const child=spawn(process.execPath,args,{cwd:root,stdio:'inherit'});child.on('error',reject);child.on('close',accept);});
            const result=JSON.parse(await fs.readFile(join(root,output,'results.json'),'utf8'));
            if(code!==0&&result.status==='passed')throw new Error('Inconsistent compatibility result');
            let failure=null;if(result.status!=='passed')failure=cause(await fs.readFile(join(root,output,'fresh.log'),'utf8'));
            results[kind]={status:result.status,jar_sha256:result.jar_sha256,failure};
        }
        let status='passed';
        if(results.candidate.status!=='passed')status=results.baseline.status!=='passed'&&results.baseline.failure!==null&&results.baseline.failure===results.candidate.failure?'blocked':'failed';
        if(status==='failed')matrix.status='failed';
        matrix.cases.push({pack,profile,status,inputs:inputs.map(({filename,version,sha256})=>({filename,version,sha256})),...results});
        await fs.writeFile(join(directory,'matrix.json'),JSON.stringify(matrix,null,2)+'\n');
    }
}
console.log('Public compatibility matrix: '+matrix.status);if(matrix.status==='failed')process.exitCode=1;
