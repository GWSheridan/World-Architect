import {promises as fs} from 'node:fs';
import {spawn} from 'node:child_process';
import {resolve,join,dirname,relative} from 'node:path';
import {fileURLToPath} from 'node:url';
import {gradle} from '../validation/process.mjs';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'../..');
const args=Object.fromEntries(Array.from({length:(process.argv.length-2)/2},(_,i)=>process.argv.slice(2+i*2,4+i*2)));
for(const k of Object.keys(args))if(!['--output','--network'].includes(k))throw Error('Unknown option '+k);
const output=resolve(root,args['--output']||'build/ci-workbench');if(!relative(root,output).startsWith('build'+(process.platform==='win32'?'\\':'/')))throw Error('Use new build directory');await fs.mkdir(output,{recursive:false});
const version=(await fs.readFile(join(root,'gradle.properties'),'utf8')).match(/^mod_version=(.+)$/m)[1].trim();
const params=['prepareWorkbenchInputs','--console=plain',`-PworkbenchInputs=${join(output,'inputs')}`];if(args['--network']!=='online')params.push('--offline');if(process.env.JAVA21_HOME)params.push(`-Porg.gradle.java.installations.paths=${process.env.JAVA21_HOME}`);
const prep=gradle(root,params,join(output,'prepare.log'));if(await prep.exit!==0)throw Error('Cannot prepare workbench cases');
const results={format:1,cases:[],status:'passed'};
for(const name of ['valid','bad-probe','locked-seed','missing-provider','unsafe-spawn']){
 const dir=join(output,name),argv=['tools/workbench/run.mjs','--scenario',join(output,'inputs',name+'.scenario.json'),'--jar',`build/libs/worldarchitect-${version}.jar`,'--fixture',`build/validation-tools/worldarchitect-${version}-validation.jar`,'--output',dir,'--network',args['--network']||'offline'];
 const exit=await new Promise((done,reject)=>{const child=spawn(process.execPath,argv,{cwd:root,stdio:'inherit'});child.on('error',reject);child.on('close',done);});
 const result=JSON.parse(await fs.readFile(join(dir,'results.json'),'utf8'));
 const probeFailure=['bad-probe','unsafe-spawn'].includes(name),log=probeFailure?await fs.readFile(join(dir,'clean-install/fresh.log'),'utf8'):'';
 const passed=probeFailure?exit!==0&&result.status==='failed'&&log.includes(name==='unsafe-spawn'?'No safe initial spawn':'Block probe failed'):exit===0&&result.status==='passed';
 results.cases.push({name,status:passed?'passed':'failed',expected_probe_failure:probeFailure});if(!passed)results.status='failed';await fs.writeFile(join(output,'results.json'),JSON.stringify(results,null,2)+'\n');
}
console.log('Workbench integration '+results.status);if(results.status!=='passed')process.exitCode=1;
