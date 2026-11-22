import {promises as fs} from 'node:fs';
import {join} from 'node:path';

export function profileProperties(text,identity){
    return identity.id==='worldarchitect'?text.replaceAll('worldsunwritten\\:','worldarchitect\\:').replaceAll('worldsunwritten:','worldarchitect:'):text;
}

// Give both versions equivalent profile inputs while keeping repository files untouched.
export async function copyProfilePacks(source,destination,identity){
    for(const entry of await fs.readdir(source,{withFileTypes:true})){
        if(entry.isSymbolicLink())throw Error('Profile packs cannot contain symbolic links');
        const name=identity.id==='worldarchitect'&&entry.name==='worldsunwritten'?'worldarchitect':entry.name;
        const from=join(source,entry.name),to=join(destination,name);
        if(entry.isDirectory()){await fs.mkdir(to,{recursive:true});await copyProfilePacks(from,to,identity);}
        else if(entry.isFile()){
            let bytes=await fs.readFile(from);
            if(identity.id==='worldarchitect'&&entry.name.endsWith('.json'))bytes=Buffer.from(bytes.toString('utf8').replaceAll('"worldsunwritten":','"worldarchitect":').replaceAll('worldsunwritten:','worldarchitect:'));
            await fs.writeFile(to,bytes,{flag:'wx'});
        }
    }
}
