import { spawn } from 'node:child_process';
import { createHash } from 'node:crypto';
import { createWriteStream, promises as fs } from 'node:fs';
import { basename, isAbsolute, join, relative, resolve } from 'node:path';

const [loader, version, jarFile, runtime, bundleFile, output, fabricApi, fixtureArg, upgradeWorld] = process.argv.slice(2);
const fixture = fixtureArg === '-' ? undefined : fixtureArg;
if (!['neoforge', 'forge', 'fabric'].includes(loader) || !/^\d+(?:\.\d+)+$/.test(version ?? '')
        || !jarFile || !runtime || !bundleFile || !output || loader === 'fabric' && !fabricApi) {
    throw Error('Usage: node loader-server.mjs <loader> <loader-version> <mod.jar> <installed-runtime> <preset.zip> <output> [fabric-api.jar or -] [fixture.jar or -] [world-to-copy]');
}

const build = resolve('build');
const directory = resolve(output);
const destination = relative(build, directory);
if (!destination || destination.startsWith('..') || isAbsolute(destination)) throw Error('Use a fresh directory under build/');
const java = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java';
const config = join(directory, 'config/worldsunwritten');
const mods = join(directory, 'mods');
const bundle = basename(bundleFile);
const hash = async path => createHash('sha256').update(await fs.readFile(path)).digest('hex');
await fs.mkdir(directory);
if (upgradeWorld) await fs.cp(resolve(upgradeWorld), join(directory, 'world'), { recursive: true });
await fs.mkdir(mods);
await fs.copyFile(jarFile, join(mods, 'Worlds-Unwritten.jar'));
if (fixture) await fs.copyFile(fixture, join(mods, 'pack-test.jar'));
await fs.mkdir(join(config, 'bundles'), { recursive: true });
await fs.copyFile(bundleFile, join(config, 'bundles', bundle));
await fs.writeFile(join(config, 'developer.json'), JSON.stringify({ format: 1, bundle, developer_mode: false }));
if (fixture) {
    const required = ['minecraft', 'worldsunwritten', ...(loader === 'fabric' ? ['fabric-api'] : [])];
    await fs.writeFile(join(config, 'test-target.properties'),
        `loader=${loader}\nversion=${version}\nrequired_mods=${required.join(',')}\n`);
}
await fs.writeFile(join(directory, 'eula.txt'), 'eula=true\n');
await fs.writeFile(join(directory, 'server.properties'), [
    'server-ip=127.0.0.1', 'server-port=0', 'online-mode=false', 'pause-when-empty-seconds=0',
    'view-distance=4', 'simulation-distance=4', 'enforce-secure-profile=false', '',
].join('\n'));

let launch;
if (loader === 'fabric') {
    await fs.copyFile(runtime, join(directory, 'fabric-server.jar'));
    await fs.copyFile(fabricApi, join(mods, 'fabric-api.jar'));
    launch = ['-jar', 'fabric-server.jar'];
} else {
    const libraries = resolve(runtime, 'libraries');
    await fs.symlink(libraries, join(directory, 'libraries'), process.platform === 'win32' ? 'junction' : 'dir');
    const args = process.platform === 'win32' ? 'win_args.txt' : 'unix_args.txt';
    if (loader === 'forge') {
        const id = `26.3-${version}`;
        const shim = `forge-${id}-shim.jar`;
        await fs.copyFile(resolve(runtime, shim), join(directory, shim));
        launch = [`@libraries/net/minecraftforge/forge/${id}/${args}`];
    } else {
        launch = [`@libraries/net/neoforged/neoforge/${version}/${args}`];
    }
}

async function boot(index) {
    const file = join(directory, `boot-${index}.log`);
    const log = createWriteStream(file);
    const child = spawn(java, ['-Xmx1500m', '-XX:ActiveProcessorCount=4', ...launch, '--nogui'], { cwd: directory, windowsHide: true });
    let output = '', ended = false;
    for (const stream of [child.stdout, child.stderr]) {
        stream.on('data', bytes => {
            log.write(bytes);
            output = (output + bytes).slice(-1_000_000);
        });
    }
    const exit = new Promise((done, fail) => {
        child.on('error', fail);
        child.on('close', code => { ended = true; log.end(); done(code); });
    });
    child.stdin.on('error', () => {});
    const send = command => { if (!ended) child.stdin.write(`${command}\n`); };
    async function wait(marker, seconds) {
        const deadline = Date.now() + seconds * 1000;
        while (!output.includes(marker)) {
            const failure = output.match(/WA_PACK_TEST_FAILED ([^\r\n]+)/);
            if (failure) throw Error(`${loader} fixture failed: ${failure[1]}. See ${file}`);
            if (ended || Date.now() > deadline) throw Error(`${loader} did not reach ${marker}. See ${file}`);
            await new Promise(done => setTimeout(done, 250));
        }
    }

    try {
        await wait('Done (', 180);
        send('datapack list enabled');
        await wait('data pack(s) enabled', 30);
        const packs = output.split(/\r?\n/).find(line => line.includes('data pack(s) enabled'));
        const modPack = { neoforge: 'mod_data', forge: 'mod:worldsunwritten', fabric: 'worldsunwritten (Fabric mod)' }[loader];
        if (!packs.includes(modPack) || !packs.includes('worldsunwritten_bundle/')) throw Error(`Missing mod or bundle datapack. See ${file}`);
        if (fixture) {
            send(`wa_pack_test ${index}`);
            await wait(`WA_PACK_TEST_PASS stage=${index}`, 180);
        }
        send('save-all flush');
        await wait('Saved the game', 45);
    } finally {
        send('stop');
        const timer = setTimeout(() => { if (!ended) child.kill(); }, 30_000);
        try { if (await exit !== 0) throw Error(`${loader} did not stop cleanly. See ${file}`); }
        finally { clearTimeout(timer); }
    }

    return snapshotOf(join(directory, 'world'));
}

async function snapshotOf(world) {
    const data = join(world, 'data');
    const snapshot = join(data, 'worldsunwritten-generation-v1.dat');
    const archives = join(data, 'worldsunwritten/generation');
    const saved = (await fs.readdir(archives)).filter(name => name.endsWith('.zip')).sort();
    if (saved.length !== 1) throw Error(`Expected one archived preset at ${archives}`);
    return { snapshot: await hash(snapshot), bundle: await hash(join(archives, saved[0])),
        settings: await hash(join(data, 'minecraft/world_gen_settings.dat')) };
}

const previous = upgradeWorld ? await snapshotOf(resolve(upgradeWorld)) : undefined;
const fresh = await boot(1);
const restart = await boot(2);
if (JSON.stringify(fresh) !== JSON.stringify(restart) || fresh.bundle !== await hash(bundleFile)) {
    throw Error('The pinned generation snapshot or original preset changed');
}
if (previous && JSON.stringify(previous) !== JSON.stringify(fresh))
    throw Error('Upgrading the copied world changed its saved generation or archived preset');
const result = { loader, version, preset: bundle, jar: await hash(jarFile), previous, fresh, restart };
await fs.writeFile(join(directory, 'results.json'), JSON.stringify(result, null, 2) + '\n');
console.log(`Passed fresh installation and restart on ${loader} ${version}`);
