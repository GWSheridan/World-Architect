import {promises as fs} from 'node:fs';
import {resolve,join,dirname,relative} from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import {gradle} from './process.mjs';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'../..');
const args=Object.fromEntries(Array.from({length:(process.argv.length-2)/2},(_,i)=>process.argv.slice(2+i*2,4+i*2)));
for(const key of Object.keys(args))if(!['--jar','--fixture','--output','--options','--bundle'].includes(key))throw Error('Unknown option '+key);
for(const key of ['--jar','--fixture','--output','--options','--bundle'])if(!args[key])throw Error('Missing '+key);
const output=resolve(root,args['--output']);if(!relative(root,output).startsWith('build'+(process.platform==='win32'?'\\':'/')))throw Error('Use a new build/ directory');await fs.mkdir(output,{recursive:false});
const jar=await fs.readFile(resolve(root,args['--jar'])),fixture=await fs.readFile(resolve(root,args['--fixture'])),sha=b=>createHash('sha256').update(b).digest('hex');
for(const name of ['server','client-alice','client-bob']){const dir=join(output,name);await fs.mkdir(join(dir,'mods'),{recursive:true});await fs.writeFile(join(dir,'mods/worldarchitect.jar'),jar);await fs.writeFile(join(dir,'mods/validation.jar'),fixture);if(name!=='server')await fs.copyFile(resolve(root,args['--options']),join(dir,'options.txt'));}
const config=join(output,'server/config/worldarchitect');await fs.mkdir(join(config,'bundles'),{recursive:true});await fs.copyFile(resolve(root,args['--bundle']),join(config,'bundles/preset.zip'));await fs.writeFile(join(config,'developer.json'),JSON.stringify({format:1,bundle:'preset.zip',developer_mode:true}));
await fs.writeFile(join(output,'server/eula.txt'),'eula=true\n');await fs.writeFile(join(output,'server/server.properties'),'server-ip=127.0.0.1\nserver-port=25963\nonline-mode=false\nwhite-list=true\nenforce-whitelist=true\nview-distance=2\nsimulation-distance=2\nallow-flight=true\nlog-ips=false\nmanagement-server-enabled=false\nenable-rcon=false\n');
await fs.writeFile(join(output,'server/whitelist.json'),JSON.stringify(['WA12Alice','WA12Bob'].map(name=>{const b=createHash('md5').update('OfflinePlayer:'+name).digest();b[6]=(b[6]&15)|48;b[8]=(b[8]&63)|128;const s=b.toString('hex');return{name,uuid:[s.slice(0,8),s.slice(8,12),s.slice(12,16),s.slice(16,20),s.slice(20)].join('-')};})));
const results={format:1,jar_sha256:sha(jar),tests:[],status:'running'},running=[];let stage,server,counters,pins,adminPins;
const read=async p=>{try{return await fs.readFile(p,'utf8');}catch(e){if(e.code==='ENOENT')return '';throw e;}};
const observed=p=>join(output,'evidence',stage,p,'observed.log');
const pause=ms=>new Promise(done=>setTimeout(done,ms));
async function wait(file,marker,seconds=180){const end=Date.now()+seconds*1000;while(!(await read(file)).includes(marker)){if(Date.now()>end)throw Error('Missing '+marker+' in '+file);await pause(200);}}
function start(task,key){const params=['-I','tools/validation/studio.init.gradle',task,'--offline','--console=plain',`-PstudioRoot=${output}`,`-PstudioStage=${stage}`];if(process.env.JAVA21_HOME)params.push(`-Porg.gradle.java.installations.paths=${process.env.JAVA21_HOME}`);const run=gradle(root,params,join(output,`${stage}-${key}.log`));running.push(run);return run;}
let sequence=0;
async function command(text){const marker='WA_STUDIO_COMMAND_'+sequence++;server.send(text);server.send('say '+marker);await server.wait(marker);}
async function act(player,commands){const index=counters[player]++,dir=join(output,'evidence',stage,player),before=(await read(observed(player))).length,pending=join(dir,`pending-${index}`);await fs.writeFile(pending,commands.join('\n')+'\n');await fs.rename(pending,join(dir,`action-${index}.txt`));await wait(observed(player),`DONE ${index} SCREEN`);return(await read(observed(player))).slice(before);}
const press=(p,label)=>act(p,['PRESS '+label]);
async function form(p,values){await act(p,values.map((v,i)=>'EDIT '+i+' '+v));return press(p,'Apply');}
async function view(p){const text=await act(p,['VIEW']);return text.split(/\r?\n/).find(s=>s.startsWith('VIEW '))||'';}
function require(value,message){if(!value)throw Error(message);}
async function stageReassign(p,target,slot){await press(p,'Reassign island');await act(p,['BOUNDS']);await form(p,[target,''+slot]);await act(p,['BOUNDS']);}
async function confirm(p){return press(p,'Confirm change');}
async function choose(p,index){await act(p,['ROW '+index]);await press(p,'Select');}
async function open(next){stage=next;counters={Alice:0,Bob:0};for(const p of ['Alice','Bob'])await fs.mkdir(join(output,'evidence',stage,p),{recursive:true});server=start('runStudioServer','server');await server.wait('Done (');const clients=['Alice','Bob'].map(p=>start('runStudio'+p,p));for(const p of ['Alice','Bob']){await server.wait('WA12'+p+' joined the game');await wait(observed(p),'AT ');}return clients;}
async function close(clients){for(const p of ['Alice','Bob'])await fs.writeFile(join(output,'evidence',stage,p,'stop'),'');for(const c of clients)require(await c.exit===0,'Client failed');await command('save-all flush');server.send('stop');require(await server.exit===0,'Server failed');server=undefined;const data=join(output,'server/world/data'),current={};for(const name of ['worldarchitect-generation-v1.dat','minecraft/world_gen_settings.dat'])current[name]=sha(await fs.readFile(join(data,name)));if(pins)require(JSON.stringify(pins)===JSON.stringify(current),'Generation pins changed');pins=current;await fs.writeFile(join(output,'results.json'),JSON.stringify(results,null,2)+'\n');}
try{
 let clients=await open('fresh');
 pins={};for(const name of ['worldarchitect-generation-v1.dat','minecraft/world_gen_settings.dat'])pins[name]=sha(await fs.readFile(join(output,'server/world/data',name)));
 await act('Bob',['COMMAND wa_admin']);require(!(await view('Bob')).includes('players:'),'Ordinary view disclosed player ledger');await act('Bob',['BOUNDS']);
 require((await act('Bob',['COMMAND wa_assembly'])).includes('Unknown or incomplete command'),'Ordinary authoring was allowed');
 require((await act('Bob',['COMMAND wa_inspect'])).includes('Unknown or incomplete command'),'Ordinary inspection was allowed');require((await act('Bob',['COMMAND wa_workbench export private one'])).includes('Unknown or incomplete command'),'Ordinary workbench export was allowed');
 await command('op WA12Alice');await command('op WA12Bob');await command('gamemode creative @a');
 await command('forceload add 4096 4096 4112 4112');await command('setblock 4096 200 4096 stone');await command('setblock 4097 200 4096 oak_stairs[facing=east]');await command('setblock 4098 200 4096 chest');await command('wa_structure capture validation:gui_module 4096 200 4096 4098 203 4098');await command('wa_structure marker set validation:gui_module entry 0 1 0 north');
 await act('Alice',['COMMAND wa_assembly']);await press('Alice','New draft');await form('Alice',['validation:gui','minecraft:plains']);
 for(const [name,x,rotation,mirror] of [['rotated',0,'cw90','left_right'],['original',8,'none','none']]){await press('Alice','Add module');await choose('Alice',0);await act('Alice',['BOUNDS']);await form('Alice',['validation:gui_module',name,''+x,'0','0',rotation,mirror]);}
 for(const scale of [1,2])await act('Alice',['SCALE '+scale,'BOUNDS','SHOT']);
 await press('Alice','Save draft');await press('Alice','Edit module');await form('Alice',['validation:gui_module','original','0','0','0','cw90','left_right']);require((await view('Alice')).includes('valid:0b'),'Overlapping assembly was accepted');
 await press('Alice','Edit module');await form('Alice',['validation:gui_module','original','8','0','0','none','none']);await press('Alice','Settings');await form('Alice',['minecraft:plains','0','4','false']);await press('Alice','Save draft');
 await press('Alice','Export datapack');await form('Alice',['validation:gui_export']);await press('Alice','Export preset');await form('Alice',['validation:gui_pack','minecraft:overworld','1.2-test','18']);
 require((await view('Alice')).includes('Preset exported:'),'Assembly bundle export failed');
 await press('Alice','Pools');await act('Alice',['BOUNDS']);await press('Alice','Enable pool');await act('Alice',['BOUNDS']);await form('Alice',['original','2','128','37']);
 await press('Alice','Add connection');await act('Alice',['BOUNDS']);await form('Alice',['original','door','0','1','0','north','rooms','default']);await press('Alice','Add choice');await act('Alice',['BOUNDS']);await form('Alice',['rooms','original','1','true']);
 await press('Alice','Sample layout seed');await form('Alice',['18']);for(const scale of [1,2])await act('Alice',['SCALE '+scale,'BOUNDS','SHOT']);await press('Alice','Back to assembly');require((await view('Alice')).includes('valid:1b'),'Pool GUI definition invalid');await press('Alice','Save draft');await press('Alice','Export datapack');await form('Alice',['validation:gui_pool']);await press('Alice','Export preset');await form('Alice',['validation:gui_pool_pack','minecraft:overworld','1.3-test','18']);await press('Alice','Close');
 await command('wa_preset capture gui_inspector one');await act('Alice',['COMMAND wa_inspect']);await act('Alice',['BOUNDS']);require((await view('Alice')).includes('healthy:1b'),'Inspector GUI did not show healthy snapshot');await press('Alice','Compare preset');await choose('Alice',2);require((await view('Alice')).includes('No dimension-generation differences.'),'Inspector GUI comparison differs');await press('Alice','Export report');await press('Alice','Close');
 results.tests.push({case:'pool-native-gui-settings-connections-caps-seeded-preview-save-export-inspector-permissions-comparison',status:'passed'});
 results.tests.push({case:'assembly-native-gui-two-modules-transforms-overlap-save-export-scales',status:'passed'});
 for(const p of ['Alice','Bob'])await act(p,['COMMAND wa_admin']);
 await stageReassign('Alice','WA12Alice',10);await stageReassign('Bob','WA12Alice',10);await confirm('Alice');await confirm('Bob');require((await view('Bob')).includes('Confirmation refused:'),'Stale administrator overwrote new state');
 await stageReassign('Bob','WA12Bob',12);const ledgerFile=join(output,'server/world/data/worldarchitect-islands-v1.dat'),beforeRevocation=sha(await fs.readFile(ledgerFile));await command('deop WA12Bob');const refusal=await confirm('Bob');require(/Incorrect argument|Unknown or incomplete command/.test(refusal),'Missing permission refusal');require(sha(await fs.readFile(ledgerFile))===beforeRevocation,'Revoked permission changed island assignments');await command('op WA12Bob');
 results.tests.push({case:'simultaneous-admin-stale-confirmation-and-permission-revocation',status:'passed'});
 await act('Alice',['COMMAND wa_admin']);await press('Alice','Islands');await press('Alice','Create team');await form('Alice',['builders']);await confirm('Alice');await press('Alice','Assign team island');await form('Alice',['builders','11']);await confirm('Alice');await press('Alice','Membership');await choose('Alice',0);await form('Alice',['WA12Bob','builders']);await confirm('Alice');
 await press('Alice','Teams');await press('Alice','Spawns');await act('Alice',['BOUNDS']);await press('Alice','containers: off');await confirm('Alice');
 await command('wa_spawn set initial team minecraft:overworld');await command('deop WA12Bob');await act('Bob',['COMMAND wa_island home']);
 const home=[...(await read(observed('Bob'))).matchAll(/^AT minecraft:overworld ([\d.-]+) ([\d.-]+) ([\d.-]+)/gm)].at(-1);require(home,'No team home');const x=Math.floor(Number(home[1]))+2,y=Math.floor(Number(home[2])),z=Math.floor(Number(home[3]));
 await command(`setblock ${x} ${y-1} ${z} stone`);await command(`setblock ${x} ${y} ${z} chest`);await command(`item replace block ${x} ${y} ${z} container.0 with diamond 3`);await act('Bob',['CLOSE',`USE ${x} ${y} ${z}`]);require((await act('Bob',['MENU'])).includes('ChestMenu'),'Team member could not open own container');
 await act('Alice',['COMMAND wa_admin']);await press('Alice','Protection');await press('Alice','Islands');await press('Alice','Membership');await choose('Alice',1);await form('Alice',['WA12Bob']);await confirm('Alice');require((await act('Bob',['MENU'])).includes('InventoryMenu'),'Team revocation left container open');
 await command(`execute if items block ${x} ${y} ${z} container.0 diamond[count=3] run say WA_STUDIO_CONTAINER_INTACT`);await server.wait('WA_STUDIO_CONTAINER_INTACT');
 results.tests.push({case:'team-reservation-membership-protection-container-revocation-inventory-intact',status:'passed'});
 await act('Alice',['COMMAND wa_admin']);await press('Alice','Teams');await press('Alice','Set spawn policy');await choose('Alice',0);await form('Alice',['respawn','minecraft:overworld','0','65','0']);await confirm('Alice');
 require((await view('Alice')).includes('fixed in minecraft:overworld'),'Fixed spawn policy not saved');await press('Alice','Clear override');await form('Alice',['respawn','minecraft:overworld']);await confirm('Alice');await press('Alice','Close');
 await close(clients);adminPins={};for(const name of ['worldarchitect-islands-v1.dat','worldarchitect-administration-v1.dat']){const b=await fs.readFile(join(output,'server/world/data',name));adminPins[name]=sha(b);}
 clients=await open('restart');await act('Alice',['COMMAND wa_assembly open validation:gui']);const restored=await view('Alice');require(restored.includes('rotated')&&restored.includes('original')&&restored.includes('valid:1b'),'Assembly did not survive restart');await press('Alice','Close');await act('Alice',['COMMAND wa_admin']);require((await view('Alice')).includes('builders'),'Team reservation lost');await act('Bob',['COMMAND wa_island home']);await close(clients);
 for(const [name,hash] of Object.entries(adminPins))require(sha(await fs.readFile(join(output,'server/world/data',name)))===hash,'Administration or allocations changed after restart');
 results.tests.push({case:'spawn-set-clear-generation-pins-ledger-settings-and-assembly-restart',status:'passed'});results.status='passed';
}catch(error){results.status='failed';results.error=error.stack;process.exitCode=1;}
finally{for(const stage of ['fresh','restart'])for(const p of ['Alice','Bob']){const dir=join(output,'evidence',stage,p);await fs.mkdir(dir,{recursive:true});await fs.writeFile(join(dir,'stop'),'');}for(const run of running.toReversed())await run.stop();await fs.writeFile(join(output,'results.json'),JSON.stringify(results,null,2)+'\n');console.log('Studio validation '+results.status+': '+relative(root,output));}
