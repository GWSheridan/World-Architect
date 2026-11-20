import {promises as fs} from 'node:fs';
import {spawn} from 'node:child_process';
import {resolve,join,dirname,relative,sep} from 'node:path';
import {fileURLToPath} from 'node:url';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'../..');
const args=Object.fromEntries(Array.from({length:(process.argv.length-2)/2},(_,i)=>process.argv.slice(2+i*2,4+i*2)));
for(const key of Object.keys(args))if(!['--source','--output','--jar','--fixture','--mods','--network'].includes(key))throw Error('Unknown option '+key);
const version=(await fs.readFile(join(root,'gradle.properties'),'utf8')).match(/^mod_version=(.+)$/m)[1].trim();
const source=resolve(root,args['--source']||'build/ci-validation'),output=resolve(root,args['--output']||'build/ci-sites');
for(const path of [source,output])if(!relative(root,path).startsWith('build'+sep))throw Error('Use disposable build directories');
await fs.mkdir(output,{recursive:false});
const exports=[...(await fs.readFile(join(source,'fresh.log'),'utf8')).matchAll(/^WA15_BUNDLE (vertical|coastal|polygon|stress) ([a-z0-9_.-]+:[a-z0-9_./-]+) ([a-z0-9_.-]+:[a-z0-9_./-]+) (.+)$/gm)];
if(exports.length<6||exports.length>7||new Set(exports.map(x=>x[1])).size!==4)throw Error('Missing 1.5 example and stress exports');
const extensions=[...(await fs.readFile(join(source,'fresh.log'),'utf8')).matchAll(/^WA16_BUNDLE (prepared|themed|composite) ([a-z0-9_.-]+:[a-z0-9_./-]+) ([a-z0-9_.-]+:[a-z0-9_./-]+) (.+)$/gm)];if(extensions.length<5||extensions.length>6)throw Error('Missing composite and prepared generation exports');
const report={format:1,version,cases:[],status:'passed'};
for(const [,kind,dimension,id,path] of [...exports,...extensions]){
    const bundle=resolve(path.trim());if(!relative(source,bundle).startsWith('server'+sep))throw Error('Export escaped the authoring fixture');
    const name=kind+'_'+dimension.replace(':','_');
    const command=['tools/validation/run.mjs','--jar',args['--jar']||`build/libs/worldarchitect-${version}.jar`,'--fixture',args['--fixture']||`build/validation-tools/worldarchitect-${version}-validation.jar`,'--output',relative(root,join(output,name)),'--suite','compatibility','--bundle',bundle,'--site',kind,'--site-structure',id,'--dimension',dimension,'--network',args['--network']||'online'];
    if(args['--mods'])command.push('--mods',args['--mods']);
    const code=await new Promise((accept,reject)=>{const child=spawn(process.execPath,command,{cwd:root,stdio:'inherit'});child.on('error',reject);child.on('close',accept);});
    const result=JSON.parse(await fs.readFile(join(output,name,'results.json'),'utf8'));report.cases.push({kind,dimension,...result});if(code!==0||result.status!=='passed')report.status='failed';
    await fs.writeFile(join(output,'results.json'),JSON.stringify(report,null,2)+'\n');if(code!==0)break;
}
if(report.status!=='passed')process.exitCode=1;
