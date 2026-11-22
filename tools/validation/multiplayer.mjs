import {promises as fs} from 'node:fs';
import {resolve,join,dirname,relative} from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import {gradle} from './process.mjs';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'../..');
const args=Object.fromEntries(Array.from({length:(process.argv.length-2)/2},(_,i)=>process.argv.slice(2+i*2,4+i*2)));
for(const key of Object.keys(args))if(!['--jar','--fixture','--output','--options'].includes(key))throw new Error('Unknown option '+key);
if(!args['--jar']||!args['--fixture']||!args['--output'])throw new Error('Use --jar JAR --fixture VALIDATION_JAR --output build/NEW_DIRECTORY [--options EXISTING_TEST_OPTIONS]');
const output=resolve(root,args['--output']);if(!relative(root,output).startsWith('build'+(process.platform==='win32'?'\\':'/')))throw new Error('Output must be under build/');
await fs.mkdir(output,{recursive:false});
const jar=await fs.readFile(resolve(root,args['--jar'])),fixture=await fs.readFile(resolve(root,args['--fixture']));
for(const name of ['server','client-alice','client-bob']) {
    const dir=join(output,name);await fs.mkdir(join(dir,'mods'),{recursive:true});
    await fs.writeFile(join(dir,'mods/worldsunwritten.jar'),jar);await fs.writeFile(join(dir,'mods/validation.jar'),fixture);
    if(name!=='server') {
        if(args['--options'])await fs.copyFile(resolve(root,args['--options']),join(dir,'options.txt'));
        else await fs.writeFile(join(dir,'options.txt'),'onboardAccessibility:false\nskipMultiplayerWarning:true\nguiScale:2\n');
    }
}
const properties=await fs.readFile(join(root,'examples/performance/profiles/single-block/server.properties'),'utf8');
await fs.writeFile(join(output,'server/server.properties'),properties.replace(/server-port=\d+/,'server-port=25946')+'\nlog-ips=false\nmanagement-server-enabled=false\n');
await fs.writeFile(join(output,'server/eula.txt'),'eula=true\n');
const whitelist=['WA11Alice','WA11Bob'].map(name=>{
    const bytes=createHash('md5').update('OfflinePlayer:'+name).digest();bytes[6]=(bytes[6]&15)|48;bytes[8]=(bytes[8]&63)|128;
    const text=bytes.toString('hex');return {name,uuid:[text.slice(0,8),text.slice(8,12),text.slice(12,16),text.slice(16,20),text.slice(20)].join('-')};
});
await fs.writeFile(join(output,'server/whitelist.json'),JSON.stringify(whitelist,null,2));
const results={format:1,jar_sha256:createHash('sha256').update(jar).digest('hex'),tests:[],status:'running'};
const running=[];let server;
async function read(file){try{return await fs.readFile(file,'utf8');}catch(error){if(error.code==='ENOENT')return '';throw error;}}
async function wait(file,marker,seconds=180){const end=Date.now()+seconds*1000;while(!(await read(file)).includes(marker)){if(Date.now()>end)throw new Error('Missing '+marker);await new Promise(done=>setTimeout(done,200));}}
function start(task,key,stage){const params=['-I','tools/validation/multiplayer.init.gradle',task,'--offline','--console=plain',`-PvalidationRoot=${output}`,`-PvalidationStage=${stage}`,`-PtestWorldDirectory=${join(output,'server')}`];if(process.env.JAVA21_HOME)params.push(`-Porg.gradle.java.installations.paths=${process.env.JAVA21_HOME}`);const run=gradle(root,params,join(output,`${stage}-${key}.log`));running.push(run);return run;}
let counters={};
async function act(player,commands,stage){const index=counters[player]++,dir=join(output,'evidence',stage,player);const pending=join(dir,`pending-${index}.tmp`);await fs.writeFile(pending,commands.join('\n')+'\n');await fs.rename(pending,join(dir,`action-${index}.txt`));await wait(join(dir,'observed.log'),`DONE ${index} SCREEN`);}
const observed=(player,stage)=>join(output,'evidence',stage,player,'observed.log');
try {
    let pins;
    for(const stage of ['fresh','restart']) {
        counters={Alice:0,Bob:0};
        for(const name of ['Alice','Bob'])await fs.mkdir(join(output,'evidence',stage,name),{recursive:true});
        server=start('runServer','server',stage);await server.wait('Done (');
        const alice=start('runValidationAlice','Alice',stage),bob=start('runValidationBob','Bob',stage);
        await server.wait('WA11Alice joined the game');await server.wait('WA11Bob joined the game');
        if(stage==='fresh') {
            server.send('op WA11Alice');server.send('gamemode spectator @a');
            server.send('forceload add 4096 4096 4112 4112');server.send('forceload add 5120 5120 5136 5136');
            server.send('setblock 4096 200 4096 stone');server.send('setblock 4097 200 4096 oak_stairs[facing=east]');server.send('setblock 4098 200 4096 chest');
            server.send('worldsunwritten structure capture validation:fixture 4096 200 4096 4098 203 4098');
            await server.wait('Captured');
            server.send('worldsunwritten structure marker set validation:fixture spawn 0 1 0 north');
            server.send('tp @a 5126 204 5126');
            await act('Bob',['COMMAND worldsunwritten structure list'],stage);await wait(observed('Bob',stage),'Unknown or incomplete command');
            await act('Alice',['COMMAND worldsunwritten structure stage validation:fixture 5120 200 5120'],stage);await wait(observed('Alice',stage),'SCREEN Structure placement');
            await act('Alice',['PRESS Rotate'],stage);await act('Alice',['PRESS Mirror','SHOT'],stage);
            server.send('worldsunwritten structure marker set validation:fixture spawn 0 1 0 east');
            await server.wait('Saved validation:fixture revision 3.');
            await act('Alice',['PRESS Confirm'],stage);await wait(observed('Alice',stage),'template changed after preview');
            await act('Alice',['COMMAND worldsunwritten structure stage validation:fixture 5120 200 5120'],stage);
            server.send('setblock 5120 200 5120 gold_block');
            await server.wait('Changed the block at 5120, 200, 5120');
            await act('Alice',['PRESS Confirm'],stage);await wait(observed('Alice',stage),'Placement collides');
            server.send('setblock 5120 200 5120 air');
            server.send('execute if block 5120 200 5120 air run say WA_DESTINATION_CLEAR');await server.wait('WA_DESTINATION_CLEAR');
            await act('Alice',['COMMAND worldsunwritten structure stage validation:fixture 5120 200 5120'],stage);
            await act('Alice',['PRESS World ghost','SHOT'],stage);
            await act('Alice',['COMMAND worldsunwritten structure stage validation:fixture 5120 200 5120'],stage);
            await act('Alice',['PRESS Confirm'],stage);await wait(observed('Alice',stage),'Placed 3 blocks');
            await act('Bob',['BLOCK 5120 200 5120'],stage);await wait(observed('Bob',stage),'BLOCK 5120, 200, 5120 minecraft:stone');
            await act('Alice',['COMMAND worldsunwritten structure undo'],stage);await wait(observed('Alice',stage),'Undid 3 blocks');
            await act('Bob',['BLOCK 5120 200 5120'],stage);await wait(observed('Bob',stage),'BLOCK 5120, 200, 5120 minecraft:air');
            await act('Bob',['COMMAND worldsunwritten diagnose report'],stage);
            results.tests.push({case:'staging-rotation-mirror-markers-stale-revision-collision-ghost-confirm-undo-client-sync',status:'passed'});
        } else {
            await act('Alice',['COMMAND worldsunwritten structure undo'],stage);await wait(observed('Alice',stage),'No manual placement remains');
            await act('Bob',['BLOCK 5120 200 5120'],stage);await wait(observed('Bob',stage),'BLOCK 5120, 200, 5120 minecraft:air');
            await wait(observed('Alice',stage),'AT minecraft:overworld 5126.50 204.00 5126.50');
            results.tests.push({case:'restart-session-history-cleared-and-player-position-retained',status:'passed'});
        }
        for(const name of ['Alice','Bob'])await fs.writeFile(join(output,'evidence',stage,name,'stop'),'');
        await alice.exit;await bob.exit;server.send('save-all flush');await server.wait('Saved the game');server.send('stop');if(await server.exit!==0)throw new Error('Server failed to stop');
        const data=join(output,'server/world/data'),current={};
        for(const file of ['worldsunwritten-generation-v1.dat','minecraft/world_gen_settings.dat'])current[file]=createHash('sha256').update(await fs.readFile(join(data,file))).digest('hex');
        if(pins&&JSON.stringify(pins)!==JSON.stringify(current))throw new Error('Generation pins changed after restart');pins=current;
    }
    results.status='passed';
} catch(error){results.status='failed';results.error=error.message;process.exitCode=1;}
finally {
    for(const stage of ['fresh','restart'])for(const name of ['Alice','Bob']){const dir=join(output,'evidence',stage,name);await fs.mkdir(dir,{recursive:true});await fs.writeFile(join(dir,'stop'),'');}
    for(const run of running.toReversed())await run.stop();
    await fs.writeFile(join(output,'results.json'),JSON.stringify(results,null,2)+'\n');console.log(`Multiplayer validation ${results.status}: ${relative(root,output)}/results.json`);
}
