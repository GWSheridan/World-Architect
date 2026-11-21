import {promises as fs} from 'node:fs';
import {spawn} from 'node:child_process';
import {resolve,join,dirname,relative,sep} from 'node:path';
import {fileURLToPath} from 'node:url';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'../..');
const options=Object.fromEntries(Array.from({length:(process.argv.length-2)/2},(_,i)=>process.argv.slice(2+i*2,4+i*2)));
for(const key of Object.keys(options))if(!['--output','--network','--jar','--fixture'].includes(key))throw Error('Unknown option '+key);
if(options['--network']&&!['offline','online'].includes(options['--network']))throw Error('Invalid network mode');
const destination=options['--output']?resolve(root,options['--output']):null;
if(destination){if(!relative(root,destination).startsWith('build'+sep))throw Error('Use a new build directory');await fs.mkdir(destination,{recursive:false});}
const version=(await fs.readFile(join(root,'gradle.properties'),'utf8')).match(/^mod_version=(.+)$/m)[1].trim();
const cases=['single-block','layered','skyblock','custom-end'].map(name=>({name,bundle:`examples/creator-starters/bundles/${name}-${version}.zip`}))
    .concat(['partial','skyblock'].map(name=>({name:'locked-'+name,bundle:`examples/preset-studio/bundles/studio-${name}-${version}.zip`})));
cases.push({name:'assembly-demo',bundle:`examples/assembly-studio/bundles/assembly-demo-${version}.zip`});
cases.push({name:'regional-demo',bundle:`examples/regional-terrain/bundles/regional-demo-${version}.zip`});
cases.push({name:'pool-demo',bundle:`examples/procedural-pools/bundles/pool-demo-${version}.zip`});
cases.push({name:'shared-regions',bundle:`examples/shared-regions/bundles/shared-regions-${version}.zip`});
cases.push({name:'constrained-pool',bundle:`examples/constrained-pools/bundles/constrained-pool-${version}.zip`});
for(const [directory,name,site,id] of [['polygon-regions','polygon-regions','polygon','polygon_marker'],['terrain-anchors','coastal-ruins','coastal','coastal_ruin'],['vertical-pools','vertical-tower','vertical','vertical_tower'],['locked-archipelago','locked-archipelago',null,null]])cases.push({name,bundle:`examples/${directory}/bundles/${name}-${version}.zip`,site,id});
for(const [directory,name,site,id] of [['composite-regions','composite-regions','composite','composite_marker'],['structure-foundations','foundation-platform','prepared','foundation_platform'],['structure-themes','themed-platform','themed','themed_platform']])cases.push({name,bundle:`examples/${directory}/bundles/${name}-${version}.zip`,site,id});
const report={format:1,version,cases:[],status:'passed'};
cases.push({name:'corridor-regions',bundle:`examples/corridor-regions/bundles/corridor-regions-${version}.zip`,site:'corridor',id:'regional_marker'});
cases.push({name:'structure-districts',bundle:`examples/structure-districts/bundles/structure-districts-${version}.zip`,site:'district',id:'district_0'});
for(const name of ['reusable-skyblock','inherited-partial','inherited-skyblock'])cases.push({name,bundle:`examples/reusable-presets/bundles/${name}-${version}.zip`});
for(const entry of cases) {
    const output=destination?relative(root,join(destination,entry.name)):`build/ci-bundle-${entry.name}`;
    const args=['tools/validation/run.mjs','--jar',options['--jar']||`build/libs/worldarchitect-${version}.jar`,'--fixture',options['--fixture']||`build/validation-tools/worldarchitect-${version}-validation.jar`,'--output',output,'--bundle',entry.bundle,'--suite','compatibility','--network',options['--network']||'online'];
    if(entry.name==='assembly-demo')args.push('--assembly','worldarchitect_examples:assembly_demo','--dimension','minecraft:overworld');
    if(entry.name==='regional-demo')args.push('--regional','true');
    if(entry.name==='pool-demo')args.push('--pool','worldarchitect_examples:pool_demo','--dimension','minecraft:overworld');
    if(entry.name==='shared-regions')args.push('--named-regions','true');
    if(entry.name==='constrained-pool')args.push('--pool','worldarchitect_examples:constrained_pool','--dimension','minecraft:overworld');
    if(entry.site)args.push('--site',entry.site,'--site-structure','worldarchitect_examples:'+entry.id,'--dimension','minecraft:overworld');
    const code=await new Promise((accept,reject)=>{const child=spawn(process.execPath,args,{cwd:root,stdio:'inherit'});child.on('error',reject);child.on('close',accept);});
    const result=JSON.parse(await fs.readFile(join(root,output,'results.json'),'utf8'));
    report.cases.push({name:entry.name,...result});if(code!==0||result.status!=='passed')report.status='failed';
}
await fs.writeFile(destination?join(destination,'results.json'):join(root,'build/ci-bundles.json'),JSON.stringify(report,null,2)+'\n');
if(report.status!=='passed')process.exitCode=1;
