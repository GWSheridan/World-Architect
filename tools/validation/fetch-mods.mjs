// Downloads fixed public test inputs only. Third-party JARs are not redistributed.
import {promises as fs} from 'node:fs';
import {createHash} from 'node:crypto';
import {resolve,join,dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'../..');
const lock=JSON.parse(await fs.readFile(join(root,'tools/validation/mods.lock.json'),'utf8'));
const directory=join(root,'build/compatibility-inputs');await fs.mkdir(directory,{recursive:true});
for(const entry of lock) {
    const url=new URL(entry.url);
    if(url.protocol!=='https:'||url.hostname!=='cdn.modrinth.com'||!/^[\w.+-]+\.jar$/.test(entry.filename))throw new Error('Invalid locked download');
    let bytes;try{bytes=await fs.readFile(join(directory,entry.filename));}catch(error){if(error.code!=='ENOENT')throw error;}
    if(!bytes){const response=await fetch(url,{headers:{'User-Agent':'WorldArchitect validation'}});if(!response.ok)throw new Error(`Download failed: ${entry.project} ${response.status}`);bytes=Buffer.from(await response.arrayBuffer());}
    for(const algorithm of ['sha256','sha512'])if(createHash(algorithm).update(bytes).digest('hex')!==entry[algorithm])throw new Error(`Checksum mismatch: ${entry.project}`);
    await fs.writeFile(join(directory,entry.filename),bytes);console.log(`Verified ${entry.filename}`);
}
