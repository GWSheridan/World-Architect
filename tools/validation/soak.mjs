import {promises as fs} from 'node:fs';
import {resolve,join,relative,dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import {gradle} from './process.mjs';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'../..');
const args=Object.fromEntries(Array.from({length:(process.argv.length-2)/2},(_,i)=>process.argv.slice(2+i*2,4+i*2)));
for(const key of Object.keys(args))if(!['--jar','--fixture','--bundle','--output','--options','--port','--rounds','--round-seconds'].includes(key))throw Error('Unknown option '+key);
for(const key of ['--jar','--fixture','--bundle','--output','--options'])if(!args[key])throw Error('Missing '+key);
const output=resolve(root,args['--output']),port=Number(args['--port']||25960),rounds=Number(args['--rounds']||20),seconds=Number(args['--round-seconds']||180);
if(!relative(root,output).startsWith('build'+(process.platform==='win32'?'\\':'/')))throw Error('Use a new build/ directory');
if(!Number.isInteger(port)||port<1024||port>65535||!Number.isInteger(rounds)||rounds<1||rounds>40||!Number.isInteger(seconds)||seconds<1||seconds>180)throw Error('Invalid workload bounds');
await fs.mkdir(output,{recursive:false});
const players=['Alice','Bob','Carol','Dave'],jar=await fs.readFile(resolve(root,args['--jar'])),fixture=await fs.readFile(resolve(root,args['--fixture']));
const sha=bytes=>createHash('sha256').update(bytes).digest('hex');
for(const name of ['server',...players.map(p=>'client-'+p.toLowerCase())]){
 const dir=join(output,name);await fs.mkdir(join(dir,'mods'),{recursive:true});
 await fs.writeFile(join(dir,'mods/worldarchitect.jar'),jar);await fs.writeFile(join(dir,'mods/validation.jar'),fixture);
 if(name!=='server'){
  let options=await fs.readFile(resolve(root,args['--options']),'utf8');
  options=options.replace(/^maxFps:.*$/m,'maxFps:20').replace(/^renderDistance:.*$/m,'renderDistance:2').replace(/^simulationDistance:.*$/m,'simulationDistance:2');
  await fs.writeFile(join(dir,'options.txt'),options);
 }
}
const config=join(output,'server/config/worldarchitect');await fs.mkdir(join(config,'bundles'),{recursive:true});
const bundle=await fs.readFile(resolve(root,args['--bundle']));await fs.writeFile(join(config,'bundles/preset.zip'),bundle);await fs.writeFile(join(config,'developer.json'),JSON.stringify({format:1,bundle:'preset.zip',developer_mode:true}));
await fs.writeFile(join(output,'server/eula.txt'),'eula=true\n');
await fs.writeFile(join(output,'server/server.properties'),`server-ip=127.0.0.1\nserver-port=${port}\nonline-mode=false\nwhite-list=true\nenforce-whitelist=true\nview-distance=2\nsimulation-distance=2\nmax-players=8\nallow-flight=true\nlog-ips=false\nmanagement-server-enabled=false\nenable-rcon=false\nmotd=World Architect four-player validation\n`);
await fs.writeFile(join(output,'server/whitelist.json'),JSON.stringify(players.map(player=>{
 const name='WA12'+player,b=createHash('md5').update('OfflinePlayer:'+name).digest();b[6]=(b[6]&15)|48;b[8]=(b[8]&63)|128;const s=b.toString('hex');return{name,uuid:[s.slice(0,8),s.slice(8,12),s.slice(12,16),s.slice(16,20),s.slice(20)].join('-')};
}),null,2));
const results={format:1,jar_sha256:sha(jar),bundle_sha256:sha(bundle),players:4,tests:[],rounds:[],status:'running'};
const running=[];let server,stage,counters,pins,ledger,homes;
const read=async path=>{try{return await fs.readFile(path,'utf8');}catch(e){if(e.code==='ENOENT')return '';throw e;}};
const pause=ms=>new Promise(done=>setTimeout(done,ms));
const report=()=>fs.writeFile(join(output,'results.json'),JSON.stringify(results,null,2)+'\n');
const observed=p=>join(output,'evidence',stage,p,'observed.log');
async function wait(file,marker,timeout=180){const end=Date.now()+timeout*1000;while(!(await read(file)).includes(marker)){if(Date.now()>end)throw Error('Missing '+marker+' in '+file);await pause(200);}}
function start(task,key){const params=['-I','tools/validation/soak.init.gradle',task,'--offline','--console=plain',`-PsoakRoot=${output}`,`-PsoakStage=${stage}`,`-PsoakPort=${port}`];if(process.env.JAVA21_HOME)params.push(`-Porg.gradle.java.installations.paths=${process.env.JAVA21_HOME}`);const run=gradle(root,params,join(output,`${stage}-${key}.log`));running.push(run);return run;}
async function act(player,commands){const index=counters[player]++,dir=join(output,'evidence',stage,player),pending=join(dir,`pending-${index}`);await fs.writeFile(pending,commands.join('\n')+'\n');await fs.rename(pending,join(dir,`action-${index}.txt`));await wait(observed(player),`DONE ${index} SCREEN`);}
function location(text){const matches=[...text.matchAll(/^AT (\S+) (-?[\d.]+) (-?[\d.]+) (-?[\d.]+)/gm)];if(!matches.length)throw Error('Missing client position');return matches.at(-1).slice(1);}
async function at(player,dimension,x,y,z){const end=Date.now()+60000;while(Date.now()<end){const s=await read(observed(player));if(s){try{const p=location(s);if(p[0]===dimension&&Math.abs(Number(p[1])-x)<.05&&Math.abs(Number(p[2])-y)<.05&&Math.abs(Number(p[3])-z)<.05)return;}catch{}}await pause(200);}throw Error('Missing current position for '+player);}
let marker=0;
async function command(text){const key='WA_SOAK_COMMAND_'+marker++;server.send(text);server.send('say '+key);await server.wait(key);}
async function fight(){await command('dragon_test state');const matches=[...(await read(join(output,`${stage}-server.log`))).matchAll(/DRAGON_STATE (\{.*\}) ENTITIES (\d+)/g)];if(!matches.length)throw Error('No native fight state');const m=matches.at(-1);return{...JSON.parse(m[1]),entities:Number(m[2])};}
async function untilFight(predicate){const end=Date.now()+150000;while(Date.now()<end){const data=await fight();if(predicate(data))return data;await pause(1000);}throw Error('Native fight did not reach the expected state');}
async function protectedHashes(){const data=join(output,'server/world/data'),current={};for(const name of ['worldarchitect-generation-v1.dat','minecraft/world_gen_settings.dat','worldarchitect-islands-v1.dat'])current[name]=sha(await fs.readFile(join(data,name)));return current;}
async function open(next){stage=next;counters=Object.fromEntries(players.map(p=>[p,0]));for(const p of players)await fs.mkdir(join(output,'evidence',stage,p),{recursive:true});server=start('runSoakServer','server');await server.wait('Done (');const clients=players.map(p=>start('runSoak'+p,p));for(const p of players){await server.wait('WA12'+p+' joined the game');await wait(observed(p),'AT ');}return clients;}
async function close(clients){for(const p of players)await fs.writeFile(join(output,'evidence',stage,p,'stop'),'');for(const client of clients)if(await client.exit!==0)throw Error('Client did not stop');await command('save-all flush');server.send('stop');if(await server.exit!==0)throw Error('Server did not stop');server=undefined;const current=await protectedHashes();if(pins&&JSON.stringify(pins)!==JSON.stringify(current))throw Error('Generation or island pins changed');pins=current;results.generation_and_island_sha256=current;await report();}
try{
 let clients=await open('fresh');homes=Object.fromEntries(await Promise.all(players.map(async p=>[p,location(await read(observed(p)))])));
 if(new Set(Object.values(homes).map(p=>p[1]+','+p[3])).size!==4)throw Error('Players share starting islands');
 for(const p of players){await act(p,['COMMAND wa_structure list']);await wait(observed(p),'Unknown or incomplete command');}
 await command('op WA12Alice');await command('gamemode spectator @a');
 await command('forceload add 4096 4096 4112 4112');await command('forceload add 5120 5120 5136 5136');
 await command('setblock 4096 200 4096 stone');await command('setblock 4097 200 4096 oak_stairs[facing=east]');await command('setblock 4098 200 4096 chest');
 await command('wa_structure capture validation:soak 4096 200 4096 4098 203 4098');await command('wa_structure marker set validation:soak spawn 0 1 0 north');await command('tp @a 5126 204 5126');
 await act('Alice',['COMMAND wa_structure stage validation:soak 5120 200 5120']);await wait(observed('Alice'),'SCREEN Structure placement');await act('Alice',['PRESS Confirm']);await wait(observed('Alice'),'Placed 3 blocks');
 for(const p of players){await act(p,['BLOCK 5120 200 5120']);await wait(observed(p),'BLOCK 5120, 200, 5120 minecraft:stone');}
 await act('Alice',['COMMAND wa_structure undo']);await wait(observed('Alice'),'Undid 3 blocks');
 for(const p of players){await act(p,['BLOCK 5120 200 5120']);await wait(observed(p),'BLOCK 5120, 200, 5120 minecraft:air');}
 // Exercise server-owned authoring through the same screens and payloads used by players.
 await act('Bob',['COMMAND wa_workbench studio cycle14_soak one']);await wait(observed('Bob'),'Unknown or incomplete command');
 await act('Alice',['COMMAND wa_assembly new validation:connected minecraft:plains']);await wait(observed('Alice'),'SCREEN Assembly Studio');
 await act('Alice',['PRESS Add module']);await act('Alice',['ROW 0','PRESS Select']);await act('Alice',['PRESS Apply']);
 await act('Alice',['PRESS History / organize']);await act('Alice',['BOUNDS','PRESS Duplicate selected module']);await act('Alice',['PRESS History / organize']);await act('Alice',['PRESS Undo']);
 await act('Alice',['PRESS History / organize']);await act('Alice',['PRESS Redo']);await act('Alice',['PRESS History / organize']);await act('Alice',['PRESS Undo']);
 await act('Alice',['PRESS Add module']);await act('Alice',['ROW 0','PRESS Select']);await act('Alice',['PRESS Apply']);
 await act('Alice',['PRESS Pools']);await act('Alice',['PRESS Enable pool']);await act('Alice',['PRESS Apply']);
 await act('Alice',['PRESS Add connection']);await act('Alice',['EDIT 4 0','PRESS Apply']);
 await act('Alice',['PRESS Add connection']);await act('Alice',['EDIT 0 module_2','EDIT 4 0','PRESS Apply']);
 await act('Alice',['PRESS Add choice']);await act('Alice',['EDIT 1 module_2','EDIT 3 true','PRESS Apply']);
 await act('Alice',['PRESS Room constraints']);await act('Alice',['ROW 0','PRESS Select']);await act('Alice',['EDIT 0 1','EDIT 1 1','EDIT 2 2','BOUNDS','PRESS Apply']);
 await act('Alice',['BOUNDS','PRESS Preview report']);await act('Alice',['ROW 1','PRESS Select']);await pause(1000);await act('Alice',['VIEW','SHOT']);await wait(observed('Alice'),'Seed 15: 2 modules');
 await act('Alice',['PRESS Back to assembly']);await act('Alice',['PRESS Save draft']);await act('Alice',['PRESS Close']);
 await command('wa_preset capture cycle14_soak one');
 await act('Alice',['COMMAND wa_workbench studio cycle14_soak one']);await wait(observed('Alice'),'SCREEN Test workbench');
 await act('Alice',['PRESS Run settings']);await act('Alice',['EDIT 1 -9223372036854775808','BOUNDS','PRESS Apply']);
 await act('Alice',['PRESS History / recovery']);await act('Alice',['PRESS Undo']);await act('Alice',['PRESS History / recovery']);await act('Alice',['PRESS Redo']);
 await act('Alice',['FIELDS','BOUNDS','PRESS Export scenario']);await act('Alice',['EDIT 0 connected-author','PRESS Apply']);await pause(1000);await act('Alice',['FIELDS','SHOT']);await wait(observed('Alice'),'Exported connected-author.scenario.json');
 const authored=JSON.parse(await fs.readFile(join(config,'exports/connected-author.scenario.json'),'utf8'));if(authored.runs[0].seed!=='-9223372036854775808')throw Error('Connected scenario seed/history changed');
 await command('deop WA12Alice');await act('Alice',['PRESS Export scenario']);await act('Alice',['EDIT 0 refused-author','PRESS Apply']);await pause(1000);await act('Alice',['FIELDS']);await wait(observed('Alice'),'permission level 4');
 try{await fs.stat(join(config,'exports/refused-author.scenario.json'));throw Error('Revoked author exported a scenario');}catch(e){if(e.code!=='ENOENT')throw e;}
 await act('Alice',['PRESS Cancel']);results.tests.push({case:'connected-history-pool-constraints-preview-workbench-ack-revoked-permission',status:'passed'});
 await command('deop WA12Alice');await command('gamemode creative @a');await command('dragon_test entry');
 await untilFight(d=>d.entities===1&&!d.dragon_killed);await command('dragon_test kill');await untilFight(d=>d.entities===0&&d.dragon_killed);
 await command('dragon_test travel');await server.wait('DRAGON_GATEWAY_ROUNDTRIP_PASS');await command('dragon_test rewards');await server.wait('DRAGON_REWARDS_PASS');await command('dragon_test ritual');await untilFight(d=>d.respawn_stage&&d.respawn_time>0);
 results.tests.push({case:'four-islands-permissions-placement-sync-undo-first-dragon-gateway-ritual',status:'passed'});await close(clients);
 clients=await open('soak');await untilFight(d=>d.entities===1&&!d.dragon_killed);await command('dragon_test kill');await untilFight(d=>d.entities===0&&d.dragon_killed);await command('dragon_test rewards');await server.wait('DRAGON_REWARDS_PASS');await command('dragon_test exit');
 for(const p of players){await act(p,['COMMAND wa_island home']);const h=homes[p];await at(p,h[0],Number(h[1]),Number(h[2]),Number(h[3]));}
 await command('gamemode spectator @a');const started=Date.now();
 for(let round=0;round<rounds;round++){
  for(const dimension of ['minecraft:overworld','minecraft:the_end'])await command(`execute in ${dimension} run forceload remove all`);
  for(let i=0;i<players.length;i++){
   const dimension=i%2?'minecraft:the_end':'minecraft:overworld',coordinate=32768+round*512+i*96,end=coordinate+48;
   await command(`execute in ${dimension} run forceload add ${coordinate} ${coordinate} ${end} ${end}`);
   await command(`execute in ${dimension} run tp WA12${players[i]} ${coordinate+.5} 140 ${coordinate+.5}`);await at(players[i],dimension,coordinate+.5,140,coordinate+.5);
   await act(players[i],['COMMAND wa_structure list']);
  }
  await command('save-all flush');await pause(seconds*1000);
  const connected='WA_SOAK_FOUR_CONNECTED_'+round;
  server.send('execute '+players.map(p=>'if entity @a[name=WA12'+p+']').join(' ')+' run say '+connected);await server.wait(connected,15);
  for(const p of players)if(Date.now()-(await fs.stat(observed(p))).mtimeMs>10000)throw Error('Client stopped observing the world: '+p);
  results.rounds.push({round,requested_chunks:64,connected_seconds:(Date.now()-started)/1000});await report();console.log('WA_FOUR_PLAYER_SOAK_ROUND '+round);
 }
 results.connected_seconds=(Date.now()-started)/1000;results.requested_chunks=rounds*64;results.homes=homes;results.final_positions=Object.fromEntries(await Promise.all(players.map(async p=>[p,location(await read(observed(p)))])));results.tests.push({case:'native-ritual-restart-second-kill-four-player-soak',status:'passed'});await close(clients);
 clients=await open('restart');for(const p of players){const saved=results.final_positions[p];await at(p,saved[0],Number(saved[1]),Number(saved[2]),Number(saved[3]));await act(p,['COMMAND wa_island home']);const h=homes[p];await at(p,h[0],Number(h[1]),Number(h[2]),Number(h[3]));}
 results.tests.push({case:'after-soak-positions-ledger-safe-home-and-generation-persistence',status:'passed'});await close(clients);results.status='passed';
}catch(error){results.status='failed';results.error=error.stack;process.exitCode=1;}
finally{
 for(const next of ['fresh','soak','restart'])for(const p of players){const dir=join(output,'evidence',next,p);await fs.mkdir(dir,{recursive:true});await fs.writeFile(join(dir,'stop'),'');}
 for(const run of running.toReversed())await run.stop();await report();console.log('Four-player validation '+results.status+': '+relative(root,join(output,'results.json')));
}
