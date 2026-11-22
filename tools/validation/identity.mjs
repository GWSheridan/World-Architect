import {inflateRawSync} from 'node:zlib';

// Inspect the actual artifact rather than guessing its contract from a renamed filename.
export function identityFromMetadata(text){
    const mods=[];let current;
    for(const line of text.split(/\r?\n/)){
        if(/^\s*\[\[mods\]\]/.test(line)){current={};mods.push(current);continue;}
        if(/^\s*\[/.test(line)){current=undefined;continue;}
        const field=line.match(/^\s*(modId|version)\s*=\s*"([^"]+)"/);if(current&&field)current[field[1]]=field[2];
    }
    for(const id of ['worldsunwritten','worldarchitect']){
        const matches=mods.filter(mod=>mod.modId===id);if(matches.length>1)throw Error('Duplicate core mod metadata');
        if(matches.length){const version=matches[0].version;if(!/^\d+\.\d+\.\d+(?:[-+][\w.-]+)?$/.test(version||''))throw Error('Missing concrete core version');return{id,version,artifactBase:id==='worldsunwritten'?'Worlds-Unwritten':'worldarchitect',runtimeFile:id==='worldsunwritten'?'Worlds-Unwritten.jar':'worldarchitect.jar',command:group=>id==='worldsunwritten'?'worldsunwritten '+group:'wa_'+group};}
    }
    throw Error('The JAR does not contain a supported core mod');
}

export function zipText(bytes,wanted){
    let end=bytes.length-22;const lower=Math.max(0,bytes.length-65557);while(end>=lower&&bytes.readUInt32LE(end)!==0x06054b50)end--;
    if(end<lower)throw Error('Invalid JAR directory');let at=bytes.readUInt32LE(end+16);
    for(let count=bytes.readUInt16LE(end+10);count--;){
        if(at+46>bytes.length||bytes.readUInt32LE(at)!==0x02014b50)throw Error('Invalid JAR entry');
        const length=bytes.readUInt16LE(at+28),extra=bytes.readUInt16LE(at+30),comment=bytes.readUInt16LE(at+32),name=bytes.subarray(at+46,at+46+length).toString();
        if(name===wanted){
            const size=bytes.readUInt32LE(at+20),expanded=bytes.readUInt32LE(at+24),local=bytes.readUInt32LE(at+42),method=bytes.readUInt16LE(at+10);
            if(size>1048576||expanded>1048576||local+30>bytes.length)throw Error('Oversized mod metadata');
            const start=local+30+bytes.readUInt16LE(local+26)+bytes.readUInt16LE(local+28);if(start+size>bytes.length)throw Error('Truncated mod metadata');
            const compressed=bytes.subarray(start,start+size),text=method===0?compressed:method===8?inflateRawSync(compressed,{maxOutputLength:1048576}):undefined;
            if(!text)throw Error('Unsupported metadata compression');return text.toString('utf8');
        }
        at+=46+length+extra+comment;
    }
    return undefined;
}

export function identityFromJar(bytes){const text=zipText(bytes,'META-INF/neoforge.mods.toml');if(text===undefined)throw Error('Missing NeoForge mod metadata');return identityFromMetadata(text);}
export function bundleManifest(bytes){const current=zipText(bytes,'worldsunwritten.bundle.json'),legacy=zipText(bytes,'worldarchitect.bundle.json');if((current===undefined)===(legacy===undefined))throw Error('Missing or conflicting bundle manifests');return JSON.parse(current??legacy);}
