import {promises as fs} from 'node:fs';
import {join} from 'node:path';
import {createHash} from 'node:crypto';
export const baselineVersion='1.3.0';
export const baselineSha256='c4d5cd556958fade5f995a80d7c1140a80ff18f60992fea7718634f17bacd7a9';
export async function baselineBytes(root,network,supplied){
    const path=supplied||join(root,'build/validation-inputs/worldarchitect-1.3.0.jar');let bytes;
    try{const stat=await fs.lstat(path);if(!stat.isFile()||stat.isSymbolicLink()||stat.size>33554432)throw Error('Invalid baseline JAR');bytes=await fs.readFile(path);}
    catch(error){if(error.code!=='ENOENT')throw error;if(supplied||network!=='online')throw Error('Cache the published 1.3 baseline with --network online or provide --baseline JAR');
        const response=await fetch('https://github.com/GWSheridan/Worlds-Unwritten/releases/download/v1.3.0/worldarchitect-1.3.0.jar');if(!response.ok)throw Error(`Baseline download returned ${response.status}`);bytes=Buffer.from(await response.arrayBuffer());
        if(bytes.length>33554432)throw Error('Baseline download exceeds its bound');
    }
    if(createHash('sha256').update(bytes).digest('hex')!==baselineSha256)throw Error('Published 1.3 baseline fingerprint differs');
    if(!supplied){await fs.mkdir(join(root,'build/validation-inputs'),{recursive:true});try{await fs.writeFile(path,bytes,{flag:'wx'});}catch(e){if(e.code!=='EEXIST')throw e;}}
    return bytes;
}
