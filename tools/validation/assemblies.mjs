import {promises as fs} from 'node:fs';
import {spawn} from 'node:child_process';
import {resolve,join,dirname,relative} from 'node:path';
import {fileURLToPath} from 'node:url';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'../..');
const args=Object.fromEntries(Array.from({length:(process.argv.length-2)/2},(_,i)=>process.argv.slice(2+i*2,4+i*2)));
for(const key of Object.keys(args))if(!['--source','--output','--jar','--fixture','--mods','--network'].includes(key))throw Error('Unknown option '+key);
const version=(await fs.readFile(join(root,'gradle.properties'),'utf8')).match(/^mod_version=(.+)$/m)[1].trim();
const source=resolve(root,args['--source']||'build/ci-validation'),output=resolve(root,args['--output']||'build/ci-assemblies');
for(const path of [source,output])if(!relative(root,path).startsWith('build'+(process.platform==='win32'?'\\':'/')))throw Error('Use build/ fixture directories');
await fs.mkdir(output,{recursive:false});
const exported=[...(await fs.readFile(join(source,'fresh.log'),'utf8')).matchAll(/^WA_ASSEMBLY_BUNDLE (\S+) (.+)$/gm)];if(exported.length<3||exported.length>4)throw Error('Missing assembly exports');
const pooled=[...(await fs.readFile(join(source,'fresh.log'),'utf8')).matchAll(/^WA_POOL_BUNDLE (\S+) (.+)$/gm)];if(pooled.length<3||pooled.length>4)throw Error('Missing pool exports');
const constrained=[...(await fs.readFile(join(source,'fresh.log'),'utf8')).matchAll(/^WA_CONSTRAINED_POOL_BUNDLE (\S+) (.+)$/gm)];if(constrained.length<3||constrained.length>4)throw Error('Missing constrained pool exports');
const report={format:1,version,cases:[],status:'passed'};
for(const [,dimension,path] of [...exported,...pooled,...constrained]){
    if(!/^[a-z0-9_]+:[a-z0-9_]+$/.test(dimension))throw Error('Unexpected fixture dimension');
    const bundle=resolve(path.trim());if(!relative(source,bundle).startsWith('server'+(process.platform==='win32'?'\\':'/')))throw Error('Export escaped the source fixture');
    const limited=constrained.some(match=>match[2]===path),pool=limited||pooled.some(match=>match[2]===path),name=dimension.replace(':','_'),destination=join(output,(limited?'constrained_':pool?'pool_':'')+name);
    const command=['tools/validation/run.mjs','--jar',args['--jar']||`build/libs/Worlds-Unwritten-${version}.jar`,'--fixture',args['--fixture']||`build/validation-tools/Worlds-Unwritten-${version}-validation.jar`,'--output',relative(root,destination),'--bundle',bundle,'--suite','compatibility',pool?'--pool':'--assembly','validation:'+(limited?'constrained_':pool?'pool_':'assembly_')+name,'--dimension',dimension,'--network',args['--network']||'online'];
    if(args['--mods'])command.push('--mods',args['--mods']);
    const code=await new Promise((accept,reject)=>{const child=spawn(process.execPath,command,{cwd:root,stdio:'inherit'});child.on('error',reject);child.on('close',accept);});
    const result=JSON.parse(await fs.readFile(join(destination,'results.json'),'utf8'));report.cases.push({dimension,...result});if(code!==0||result.status!=='passed')report.status='failed';
    await fs.writeFile(join(output,'results.json'),JSON.stringify(report,null,2)+'\n');
}
console.log('Exported assembly generation '+report.status);if(report.status!=='passed')process.exitCode=1;
