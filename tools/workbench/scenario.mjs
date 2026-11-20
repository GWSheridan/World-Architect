import {promises as fs} from 'node:fs';
const id=s=>typeof s==='string'&&/^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(s)&&s.length<=128;
const object=(v,keys)=>{if(!v||typeof v!=='object'||Array.isArray(v)||Object.keys(v).some(k=>!keys.includes(k)))throw Error('Unknown or malformed scenario field');};
const integer=(v,min,max)=>{if(!Number.isInteger(v)||v<min||v>max)throw Error(`Expected integer ${min}..${max}`);};
const list=(v,min,max)=>{if(!Array.isArray(v)||v.length<min||v.length>max)throw Error(`Expected list with ${min}..${max} entries`);};
export function validateScenario(s) {
    object(s,['format','bundle','budgets','runs']);if(s.format!==1)throw Error('Unsupported scenario format');
    object(s.bundle,['file','sha256']);if(typeof s.bundle.file!=='string'||! /^[a-zA-Z0-9_.-]{1,140}\.zip$/.test(s.bundle.file)||! /^[a-f0-9]{64}$/.test(s.bundle.sha256))throw Error('Use a local bundle ZIP filename and SHA-256');
    object(s.budgets,['timeout_seconds','heap_mib']);integer(s.budgets.timeout_seconds,30,900);integer(s.budgets.heap_mib,768,4096);
    list(s.runs,1,16);const names=new Set();let total=0;
    for(const r of s.runs){
        object(r,['name','seed','expect_startup','expected_error','safe_spawn','restart','dimensions','regions','probes']);
        if(typeof r.name!=='string'||! /^[a-z0-9_-]{1,40}$/.test(r.name)||names.has(r.name))throw Error('Use unique scenario run names');names.add(r.name);
        if(typeof r.seed!=='string'||! /^-?\d{1,20}$/.test(r.seed)||BigInt(r.seed)<-9223372036854775808n||BigInt(r.seed)>9223372036854775807n)throw Error('Seeds must be signed 64-bit strings');
        if(!['accept','reject'].includes(r.expect_startup)||typeof r.safe_spawn!=='boolean'||typeof r.restart!=='boolean')throw Error('Invalid startup, spawn or restart policy');
        if(r.expect_startup==='reject'&&(typeof r.expected_error!=='string'||r.expected_error.length<8||r.expected_error.length>200||/[\r\n]/.test(r.expected_error)))throw Error('Rejection jobs require a specific expected_error substring');
        list(r.dimensions,1,16);if(r.dimensions.some(d=>!id(d))||new Set(r.dimensions).size!==r.dimensions.length)throw Error('Invalid or repeated dimension');
        list(r.regions,0,32);let chunks=0;for(const region of r.regions){object(region,['dimension','chunk_x','chunk_z','width','depth']);if(!r.dimensions.includes(region.dimension))throw Error('Region dimension is not selected');integer(region.chunk_x,-1874980,1874980);integer(region.chunk_z,-1874980,1874980);integer(region.width,1,16);integer(region.depth,1,16);chunks+=region.width*region.depth;}
        if(chunks>256)throw Error('A run may generate at most 256 requested chunks');total+=chunks;
        list(r.probes,0,128);for(const p of r.probes){object(p,['dimension','x','y','z','block','biome','structure']);if(!r.dimensions.includes(p.dimension))throw Error('Probe dimension is not selected');integer(p.x,-29999680,29999680);integer(p.z,-29999680,29999680);integer(p.y,-2032,2031);if(!['block','biome','structure'].some(k=>k in p))throw Error('Empty probe');if(p.block!==undefined&&(typeof p.block!=='string'||p.block.length>500||! /^[a-z0-9_.-]+:[a-z0-9_./-]+(?:\[[a-z0-9_=,.-]+\])?$/.test(p.block)))throw Error('Invalid block state probe');for(const k of ['biome','structure'])if(p[k]!==undefined&&!id(p[k]))throw Error('Invalid resource probe');}
    }
    if(total>2048)throw Error('A scenario may request at most 2048 chunks');return s;
}
export async function readScenario(path){const stat=await fs.lstat(path);if(!stat.isFile()||stat.isSymbolicLink()||stat.size>262144)throw Error('Scenario must be a regular file under 256 KiB');return validateScenario(JSON.parse(await fs.readFile(path,'utf8')));}
