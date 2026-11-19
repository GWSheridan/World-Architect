import {promises as fs} from 'node:fs';
import {join,resolve} from 'node:path';
const root=resolve(process.argv[2]||'build/performance-comparison');
function csv(text){const rows=text.trim().split(/\r?\n/).map(row=>row.split(',').map(value=>value.replace(/^"|"$/g,'')));const header=rows.shift();return rows.map(row=>Object.fromEntries(header.map((key,i)=>[key,row[i]])));}
const median=values=>{values.sort((a,b)=>a-b);return values.length%2?values[(values.length-1)/2]:(values[values.length/2-1]+values[values.length/2])/2;};
const samples={baseline:[],candidate:[]};
for(let pair=1;pair<=3;pair++)for(const version of ['baseline','candidate'])samples[version].push(...csv(await fs.readFile(join(root,`pair-${pair}`,`bench-${version}`,'performance.csv'),'utf8')).map(row=>({...row,pair})));
const result={format:1,pairs:3,profiles:[],status:'passed'};
for(const profile of ['normal','void','single-block','layered','structure-heavy']) {
    const values={};
    for(const version of ['baseline','candidate']) {
        const rows=samples[version].filter(row=>row.Profile===profile);
        if(rows.length!==6||rows.some(row=>Number(row.Chunks)!==36||!Number.isFinite(Number(row.Seconds))||Number(row.Seconds)<=0||!Number.isFinite(Number(row.PeakSampledWorkingSetMiB))))throw new Error(`Incomplete benchmark data: ${profile}/${version}`);
        const perPair=[];for(let pair=1;pair<=3;pair++){const pairRows=rows.filter(row=>row.pair===pair);if(pairRows.length!==2)throw new Error('Incomplete pair');perPair.push(pairRows.reduce((sum,row)=>sum+Number(row.Seconds),0));}
        values[version]={median_pair_seconds:median(perPair),median_peak_resident_mib:median(rows.map(row=>Number(row.PeakSampledWorkingSetMiB))),measured_chunks:216};
    }
    const time=(values.candidate.median_pair_seconds/values.baseline.median_pair_seconds-1)*100,memory=(values.candidate.median_peak_resident_mib/values.baseline.median_peak_resident_mib-1)*100;
    const status=time>10||memory>15?'investigate':'passed';if(status!=='passed')result.status='investigate';
    result.profiles.push({profile,...values,generation_change_percent:Number(time.toFixed(2)),memory_change_percent:Number(memory.toFixed(2)),status});
}
await fs.writeFile(join(root,'comparison.json'),JSON.stringify(result,null,2)+'\n');console.log(JSON.stringify(result,null,2));if(result.status!=='passed')process.exitCode=1;
