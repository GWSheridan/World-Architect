import {promises as fs} from 'node:fs';
import {spawn} from 'node:child_process';
import {resolve,join,dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'../..');
const version=(await fs.readFile(join(root,'gradle.properties'),'utf8')).match(/^mod_version=(.+)$/m)[1].trim();
const cases=['single-block','layered','skyblock','custom-end'].map(name=>({name,bundle:`examples/creator-starters/bundles/${name}-${version}.zip`}))
    .concat(['partial','skyblock'].map(name=>({name:'locked-'+name,bundle:`examples/preset-studio/bundles/studio-${name}-${version}.zip`})));
cases.push({name:'assembly-demo',bundle:`examples/assembly-studio/bundles/assembly-demo-${version}.zip`});
const report={format:1,version,cases:[],status:'passed'};
for(const entry of cases) {
    const output=`build/ci-bundle-${entry.name}`;
    const args=['tools/validation/run.mjs','--jar',`build/libs/worldarchitect-${version}.jar`,'--fixture',`build/validation-tools/worldarchitect-${version}-validation.jar`,'--output',output,'--bundle',entry.bundle,'--suite','compatibility','--network','online'];
    if(entry.name==='assembly-demo')args.push('--assembly','worldarchitect_examples:assembly_demo','--dimension','minecraft:overworld');
    const code=await new Promise((accept,reject)=>{const child=spawn(process.execPath,args,{cwd:root,stdio:'inherit'});child.on('error',reject);child.on('close',accept);});
    const result=JSON.parse(await fs.readFile(join(root,output,'results.json'),'utf8'));
    report.cases.push({name:entry.name,...result});if(code!==0||result.status!=='passed')report.status='failed';
}
await fs.writeFile(join(root,'build/ci-bundles.json'),JSON.stringify(report,null,2)+'\n');
if(report.status!=='passed')process.exitCode=1;
