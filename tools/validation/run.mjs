// Node 22+, Java 25. All game writes stay in a new disposable output directory.
import { spawn } from 'node:child_process';
import { promises as fs, createWriteStream } from 'node:fs';
import { resolve, relative, join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';
import { baselineBytes } from './baseline.mjs';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const options = Object.fromEntries(Array.from({length: (process.argv.length - 2) / 2}, (_, i) => process.argv.slice(2 + i * 2, 4 + i * 2)));
for (const key of Object.keys(options)) if (!['--jar','--fixture','--output','--world','--profile','--port','--mods','--suite','--bundle','--network','--assembly','--dimension','--preset','--check-end','--pool','--regional','--baseline','--named-regions','--baseline14','--baseline15','--site','--site-structure'].includes(key)) throw new Error(`Unknown option ${key}`);
if (!['online','offline'].includes(options['--network'] || 'offline')) throw new Error('Network must be online or offline');
if (!options['--jar'] || !options['--fixture'] || !options['--output']) throw new Error('Use --jar JAR --fixture VALIDATION_JAR --output NEW_DIRECTORY [--world COPIED_SAVE] [--profile single-block] [--port 25945]');
if(options['--dimension']&&!/^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(options['--dimension']))throw Error('Invalid dimension ID');
if(options['--check-end']&&!['true','false'].includes(options['--check-end']))throw Error('Invalid End check flag');
if(options['--site']&&!['vertical','coastal','polygon','stress','prepared','themed','composite'].includes(options['--site']))throw Error('Invalid site fixture kind');
if(options['--site-structure']&&!/^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(options['--site-structure']))throw Error('Invalid site structure ID');
const output = resolve(root, options['--output']);
if (!relative(root, output).startsWith('build' + (process.platform === 'win32' ? '\\' : '/'))) throw new Error('Output must be under build/');
await fs.mkdir(output, {recursive:false});
const server = join(output, 'server'); await fs.mkdir(join(server,'mods'), {recursive:true});
const profile = options['--profile'] || 'single-block';
if (!['normal','single-block','layered','void','structure-heavy'].includes(profile)) throw new Error('Unknown fixture profile');
const port = Number(options['--port'] || 25945); if (!Number.isInteger(port) || port < 1024 || port > 65535) throw new Error('Invalid port');
const sha = bytes => createHash('sha256').update(bytes).digest('hex');
const results = {format:1, platform:process.platform, profile, jar_sha256:'', tests:[], status:'running'};
const suite = options['--suite'] || 'authoring';
if (!['authoring','compatibility'].includes(suite)) throw new Error('Unknown validation suite');
results.suite = suite;
async function hashes(path) {
    const found = {};
    async function walk(dir) {
        for (const entry of await fs.readdir(dir,{withFileTypes:true})) {
            const file = join(dir,entry.name);
            if (entry.isSymbolicLink()) throw new Error('Symlinks are not allowed in a fixture world');
            if (entry.isDirectory()) await walk(file);
            else if (entry.isFile() && entry.name !== 'session.lock') found[relative(path,file).replaceAll('\\','/')] = sha(await fs.readFile(file));
        }
    }
    await walk(path); return found;
}
let original;
if (options['--world']) {
    const source = resolve(root,options['--world']); original = await hashes(source);
    await fs.cp(source,join(server,'world'),{recursive:true,errorOnExist:true,force:false});
}
for (const [flag,name] of [['--jar','worldarchitect.jar'],['--fixture','validation.jar']]) {
    const bytes = await fs.readFile(resolve(root,options[flag]));
    if (flag === '--jar') results.jar_sha256 = sha(bytes);
    await fs.writeFile(join(server,'mods',name),bytes,{flag:'wx'});
}
if (options['--mods']) {
    results.external_mods = [];
    const directory = resolve(root,options['--mods']);
    for (const name of (await fs.readdir(directory)).filter(name=>name.endsWith('.jar')).sort()) {
        if (!/^[a-zA-Z0-9+_.-]+\.jar$/.test(name)) throw new Error('Invalid mod filename');
        const bytes = await fs.readFile(join(directory,name));
        await fs.writeFile(join(server,'mods',name),bytes,{flag:'wx'}); results.external_mods.push({file:name,sha256:sha(bytes)});
    }
}
let properties = await fs.readFile(join(root,'examples/performance/profiles',profile,'server.properties'),'utf8');
properties = properties.split(/\r?\n/).filter(line => !/^(server-port|management-server|rcon\.|resource-pack|text-filter|log-ips|motd)=/.test(line)).join('\n');
if(options['--preset']){if(options['--bundle']||!/^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(options['--preset']))throw Error('Choose one valid preset ID or a bundle');properties=properties.replace(/^level-type=.*$/m,'level-type='+options['--preset']);}
if (options['--bundle']) {
    if (options['--world']) throw new Error('Use a pack-specific upgrade fixture for an existing bundled world');
    const config=join(server,'config/worldarchitect');await fs.mkdir(join(config,'bundles'),{recursive:true});
    await fs.copyFile(resolve(root,options['--bundle']),join(config,'bundles/preset.zip'));
    await fs.writeFile(join(config,'developer.json'),JSON.stringify({format:1,bundle:'preset.zip'}));
    properties=properties.split(/\r?\n/).filter(line=>!/^(level-type|level-seed|generator-settings|generate-structures|initial-enabled-packs)=/.test(line)).join('\n');
}
if(suite === 'authoring') {
    const config=join(server,'config/worldarchitect');await fs.mkdir(config,{recursive:true});
    const file=join(config,'developer.json');let developer={format:1};try{developer=JSON.parse(await fs.readFile(file,'utf8'));}catch(e){if(e.code!=='ENOENT')throw e;}
    developer.developer_mode=true;await fs.writeFile(file,JSON.stringify(developer));
    await fs.writeFile(join(config,'validation-baseline-1.3.0.jar'),await baselineBytes(root,options['--network'],options['--baseline']?resolve(root,options['--baseline']):undefined),{flag:'wx'});
    const frozen14=resolve(root,options['--baseline14']||'build/validation-inputs/worldarchitect-1.4.0.jar');const bytes14=await fs.readFile(frozen14);
    if(bytes14.length>33554432||sha(bytes14)!=='3684963461df2331c7f0fa4968d12b972fceff6c73c3568c77a19dcc66474fe4')throw Error('Frozen 1.4 baseline fingerprint differs');
    await fs.writeFile(join(config,'validation-baseline-1.4.0.jar'),bytes14,{flag:'wx'});
    const bytes15=await fs.readFile(resolve(root,options['--baseline15']||'build/validation-inputs/worldarchitect-1.5.0.jar'));if(sha(bytes15)!=='27a6a5e55567e04db4c93818daa2a866bd9bc0d0313780b08f17dcba1e20be42')throw Error('Frozen 1.5 baseline fingerprint differs');await fs.writeFile(join(config,'validation-baseline-1.5.0.jar'),bytes15,{flag:'wx'});
}
await fs.writeFile(join(server,'server.properties'),properties + `\nserver-port=${port}\nmanagement-server-enabled=false\nlog-ips=false\nmotd=World Architect validation\n`);
await fs.writeFile(join(server,'eula.txt'),'eula=true\n');
try { await fs.cp(join(root,'examples/performance/profiles',profile,'datapacks'),join(server,'world/datapacks'),{recursive:true}); }
catch (error) { if (error.code !== 'ENOENT') throw error; }

function launch(stage) {
    const args = ['-I','tools/validation/server.init.gradle','runValidationServer','--console=plain',`-PtestWorldDirectory=${server}`];
    if (options['--network'] !== 'online') args.push('--offline');
    if (process.env.JAVA21_HOME) args.push(`-Porg.gradle.java.installations.paths=${process.env.JAVA21_HOME}`);
    let child;
    if (process.platform === 'win32') {
        const quote = s => { if (/["\r\n%&|<>^]/.test(s)) throw new Error('Unsupported shell character in argument'); return `"${s}"`; };
        child = spawn(process.env.ComSpec || 'cmd.exe',['/d','/s','/c',`"${[join(root,'gradlew.bat'),...args].map(quote).join(' ')}"`],{cwd:root,windowsHide:true,windowsVerbatimArguments:true});
    } else child = spawn('sh',[join(root,'gradlew'),...args],{cwd:root});
    const log = createWriteStream(join(output,`${stage}.log`)); let text = '', ended = false;
    for (const stream of [child.stdout,child.stderr]) stream.on('data',chunk => { log.write(chunk); text = (text + chunk).slice(-2_000_000); });
    const exit = new Promise((accept,reject) => { child.on('error',reject); child.on('close',code => { ended=true; log.end(); accept(code); }); });
    const send = command => { if (!ended) child.stdin.write(command+'\n'); };
    async function wait(marker,seconds=150) {
        const until = Date.now()+seconds*1000;
        while (!text.includes(marker)) {
            if (ended || Date.now()>until) throw new Error(`Missing ${marker}; inspect ${stage}.log`);
            if (text.includes('WA_VALIDATION_STRUCTURES_FAILED')) throw new Error('Dedicated structure assertions failed');
            if (text.includes('WA_SITE_GENERATION_FAILED')) throw new Error('Dedicated site generation assertions failed');
            await new Promise(done=>setTimeout(done,200));
        }
    }
    return {child,exit,send,wait,output:()=>text};
}
let running;
let pinned;
async function generationPins() {
    const data=join(server,'world/data'),pins={};
    for(const file of ['worldarchitect-generation-v1.dat','minecraft/world_gen_settings.dat']) {
        try{pins[file]=sha(await fs.readFile(join(data,file)));}catch(error){if(error.code!=='ENOENT')throw error;}
    }
    try {
        for(const file of (await fs.readdir(join(data,'worldarchitect/generation'))).filter(name=>name.endsWith('.zip')).sort())
            pins['worldarchitect/generation/'+file]=sha(await fs.readFile(join(data,'worldarchitect/generation',file)));
    } catch(error){if(error.code!=='ENOENT')throw error;}
    return pins;
}
try {
    for (const stage of ['fresh','restart']) {
        running = launch(stage); await running.wait('Done (');
        let beforeAuthoring;
        if(stage==='fresh'&&suite==='authoring'){running.send('save-all flush');await running.wait('Saved the game');beforeAuthoring=await generationPins();}
        if (suite === 'authoring') {
            running.send(stage === 'fresh' ? 'wa_validate structures' : 'wa_validate restart');
            await running.wait(stage === 'fresh' ? 'WA_VALIDATION_STRUCTURES_PASS' : 'WA_VALIDATION_RESTART_PASS');
            running.send('wa_diagnose report'); await running.wait('Saved local diagnostics');
        } else if(options['--site']) {
            running.send(`wa_validate site_generation ${options['--site']} ${options['--site-structure']} ${options['--dimension']||'minecraft:overworld'} ${stage==='fresh'?1:2}`);
            await running.wait(['prepared','themed','composite'].includes(options['--site'])?'WA16_GENERATION_PASS':'WA15_GENERATION_PASS');
        } else if(options['--named-regions']==='true') {
            running.send('wa_validate named_regions '+(stage==='fresh'?1:2));await running.wait('WA_NAMED_REGIONS_GENERATION_PASS');
        } else if(options['--regional']==='true') {
            running.send('wa_validate regional_generation '+(stage==='fresh'?1:2));await running.wait('WA_REGIONAL_GENERATION_PASS');
        } else if(options['--assembly']||options['--pool']) {
            if(!options['--dimension'])throw Error('Assembly validation requires a dimension');
            if(options['--pool']&&stage==='restart'){running.send(`wa_validate pool_generation ${options['--pool']} ${options['--dimension']} 64`);await running.wait(`WA_POOL_GENERATION_PASS dimension=${options['--dimension']} region=64`);}
            running.send(`wa_validate ${options['--pool']?'pool_generation':'assembly_generation'} ${options['--pool']||options['--assembly']} ${options['--dimension']} ${stage==='fresh'?64:65}`);await running.wait(options['--pool']?`WA_POOL_GENERATION_PASS dimension=${options['--dimension']} region=${stage==='fresh'?64:65}`:'WA_ASSEMBLY_GENERATION_PASS');
        } else {
            const base = stage === 'fresh' ? 16000 : 32000, end = base + 48;
            const dimension=options['--dimension']||'minecraft:overworld';
            running.send(`execute in ${dimension} run forceload add ${base} ${base} ${end} ${end}`);
            const marker = `WA_COMPATIBILITY_${stage.toUpperCase()}_PASS`;
            for (let poll=0; poll<150; poll++) {
                const points=[];if(options['--check-end']==='true'){for(let x=base;x<=end;x+=16)for(let z=base;z<=end;z+=16)points.push(`if loaded ${x} 100 ${z}`);}else points.push(`if loaded ${base} 100 ${base}`,`if loaded ${end} 100 ${end}`);
                running.send(`execute in ${dimension} ${points.join(' ')} run say ${marker}`);
                try { await running.wait(marker,1); break; } catch(error) { if(poll===149)throw error; }
            }
            running.send(`execute in ${dimension} run forceload remove all`);
        }
        if(options['--check-end']==='true'){
            running.send('dragon_test audit');await running.wait('DRAGON_ARENA_AUDIT_PASS');
            running.send('dragon_test state');await running.wait('DRAGON_STATE');
            const state=[...running.output().matchAll(/DRAGON_STATE (\{.*\}) ENTITIES (\d+)/g)].at(-1);if(!state)throw Error('Missing saved dragon state');
            const data=JSON.parse(state[1]),persistent={};for(const key of ['dragon_killed','previously_killed','exit_portal_location','gateways','dragon_uuid','respawn_stage','respawn_time'])if(key in data)persistent[key]=data[key];
            results.end_progression??=[];if(results.end_progression.length&&JSON.stringify(results.end_progression[0].state)!==JSON.stringify(persistent))throw Error('Saved dragon progression changed across the idle restart');results.end_progression.push({stage,state:persistent,arena_audit:'passed'});
        }
        running.send('save-all flush'); await running.wait('Saved the game');
        running.send('stop');
        const code = await Promise.race([running.exit,new Promise((_,fail)=>setTimeout(()=>fail(new Error('Shutdown timed out')),40000).unref())]);
        if (code !== 0) throw new Error('Server exited with an error');
        const current=await generationPins();
        if(beforeAuthoring&&JSON.stringify(current)!==JSON.stringify(beforeAuthoring))throw new Error('Authoring changed pinned generation files');
        if(pinned&&JSON.stringify(current)!==JSON.stringify(pinned))throw new Error('Pinned generation files changed after restart');
        pinned=current;results.generation_sha256=current;
        results.tests.push({case:stage,status:'passed'}); running = undefined;
    }
    results.tests.push({case:'generation-pins-intact',status:'passed',files:Object.keys(pinned).length});
    if (original) {
        const after = await hashes(resolve(root,options['--world']));
        if (JSON.stringify(original) !== JSON.stringify(after)) throw new Error('Original save changed');
        results.tests.push({case:'source-save-intact',status:'passed',files:Object.keys(original).length});
    }
    results.status='passed';
} catch (error) { results.status='failed'; results.error=error.message; process.exitCode=1; }
finally {
    if (running) {
        running.send('stop');
        await Promise.race([running.exit,new Promise(done=>setTimeout(done,20000))]);
        if (running.child.exitCode === null) {
            if (process.platform === 'win32') spawn('taskkill',['/PID',String(running.child.pid),'/T','/F'],{windowsHide:true});
            else running.child.kill('SIGTERM');
        }
    }
    await fs.writeFile(join(output,'results.json'),JSON.stringify(results,null,2)+'\n');
    console.log(`Dedicated validation ${results.status}: ${relative(root,output)}/results.json`);
}
