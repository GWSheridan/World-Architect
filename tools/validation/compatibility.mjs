import {promises as fs} from 'node:fs';
import {spawn} from 'node:child_process';
import {createHash} from 'node:crypto';
import {resolve,join,dirname,relative} from 'node:path';
import {fileURLToPath} from 'node:url';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'../..');
const version=(await fs.readFile(join(root,'gradle.properties'),'utf8')).match(/^mod_version=(.+)$/m)[1].trim();
const args=Object.fromEntries(Array.from({length:(process.argv.length-2)/2},(_,i)=>process.argv.slice(2+i*2,4+i*2)));
for(const key of Object.keys(args))if(!['--jar','--fixture','--output','--baseline-version','--network'].includes(key))throw Error('Unknown option '+key);
const baselineVersion=args['--baseline-version']||'1.2.0',checksums={'1.2.0':'4d4732da16802e1d85e1d821e287b78bf3b2ea70cf4f6acb28cb42f4f8bc64b1','1.0.0':'14fe208d2fed07e8dec98e33b1f06c519cb35a81a95412e60b1c808d94b332cd','1.1.0':'2bb93f40d6c9d03a1f461cb62c888ec650132ef44ba5f18052da5cc7d8e79efa'};
if(!checksums[baselineVersion])throw Error('Unknown verified baseline');
const network=args['--network']||'online';if(!['online','offline'].includes(network))throw Error('Invalid network mode');
const directory=resolve(root,args['--output']||'build/ci-compatibility');if(!relative(root,directory).startsWith('build'+(process.platform==='win32'?'\\':'/')))throw Error('Use a new build/ directory');await fs.mkdir(directory,{recursive:false});
await import('./fetch-mods.mjs');
const lock=JSON.parse(await fs.readFile(join(root,'tools/validation/mods.lock.json'),'utf8'));
const baseline=join(directory,`worldarchitect-${baselineVersion}.jar`);
const response=await fetch(`https://github.com/GWSheridan/World-Architect/releases/download/v${baselineVersion}/worldarchitect-${baselineVersion}.jar`);
if(!response.ok)throw new Error('Cannot fetch the published baseline');
const bytes=Buffer.from(await response.arrayBuffer());
if(createHash('sha256').update(bytes).digest('hex')!==checksums[baselineVersion])throw new Error('Published baseline checksum mismatch');
await fs.writeFile(baseline,bytes,{flag:'wx'});
const matrix={format:1,platform:process.platform,candidate:version,baseline:baselineVersion,cases:[],status:'passed'};
function cause(text){
    const lines=text.split(/\r?\n/);const linkage=lines.map(line=>line.match(/(?:NoSuchMethodError|NoClassDefFoundError|Mod \S+ requires):?\s*(.+)/)?.[0]).filter(Boolean);
    if(linkage.length)return linkage[0].replace(/\s+~?\[[^\]]+\].*$/,'').trim();
    const causes=lines.map(line=>line.match(/Caused by:\s*(.+)/)?.[1]).filter(s=>s&&!/^(?:java\.net\.|javax\.net\.|com\.mojang\.authlib\.)/.test(s));
    return causes.at(-1)?.replace(/\s+~?\[[^\]]+\].*$/,'').trim()||null;
}
for(const [pack,projects] of Object.entries({terralith:['8oi3bsk5','XaDC71GB'],biomesoplenty:['HXF82T3G','s3dmwKy5','kkmrDlKT'],combined:['8oi3bsk5','XaDC71GB','HXF82T3G','s3dmwKy5','kkmrDlKT']})) {
    const mods=join(directory,pack);await fs.mkdir(mods);
    const inputs=lock.filter(entry=>projects.includes(entry.project));
    if(inputs.length!==projects.length)throw new Error('Incomplete pinned dependency set');
    for(const input of inputs)await fs.copyFile(join(root,'build/compatibility-inputs',input.filename),join(mods,input.filename));
    for(const profile of ['normal','single-block']) {
        const results={};
        for(const kind of ['baseline','candidate']) {
            const output=relative(root,join(directory,`cases/${pack}-${kind}-${profile}`));await fs.mkdir(join(directory,'cases'),{recursive:true});
            const commandArgs=['tools/validation/run.mjs','--jar',kind==='baseline'?baseline:(args['--jar']||`build/libs/worldarchitect-${version}.jar`),'--fixture',args['--fixture']||`build/validation-tools/worldarchitect-${version}-validation.jar`,'--output',output,'--profile',profile,'--mods',mods,'--suite','compatibility','--network',network];
            const code=await new Promise((accept,reject)=>{const child=spawn(process.execPath,commandArgs,{cwd:root,stdio:'inherit'});child.on('error',reject);child.on('close',accept);});
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
if(matrix.status==='passed'&&matrix.cases.some(c=>c.status==='blocked')){matrix.status='passed_with_blocked';await fs.writeFile(join(directory,'matrix.json'),JSON.stringify(matrix,null,2)+'\n');}
console.log('Public compatibility matrix: '+matrix.status);if(matrix.status==='failed')process.exitCode=1;
