const median=values=>{values=[...values].sort((a,b)=>a-b);return values.length%2?values[(values.length-1)/2]:(values[values.length/2-1]+values[values.length/2])/2;};
const change=(before,after)=>(after/before-1)*100;
const round=value=>Number(value.toFixed(3));

/** Direct generation, sampled heap and actual RSS are distinct metrics. Missing RSS is never zero. */
export function analyzeBenchmarks(input,runs){
    if(!Number.isInteger(input.pairs)||input.pairs<1||input.pairs>5||!Number.isInteger(input.control_pairs)||input.control_pairs<1||input.control_pairs>3||!Number.isInteger(input.samples)||input.samples<2||input.samples>5||!Number.isInteger(input.measured_size)||input.measured_size<1||input.measured_size>32)throw Error('Invalid benchmark sampling plan');
    if(!Array.isArray(input.profiles)||!input.profiles.length||new Set(input.profiles).size!==input.profiles.length||runs.length!==input.profiles.length*(input.pairs+input.control_pairs)*2)throw Error('Incomplete benchmark matrix');
    const identities=new Set();let environment;
    for(const run of runs){
        const identity=[run.kind,run.pair,run.role,run.profile].join('/');if(identities.has(identity))throw Error('Duplicate benchmark run');identities.add(identity);
        const pairs=run.kind==='comparison'?input.pairs:run.kind==='control'?input.control_pairs:0;
        if(!input.profiles.includes(run.profile)||!['baseline','candidate'].includes(run.role)||!Number.isInteger(run.pair)||run.pair<1||run.pair>pairs)throw Error('Unexpected benchmark run');
        const expected=run.kind==='control'||run.role==='baseline'?input.baseline_sha256:input.candidate_sha256;
        if(run.status!=='passed'||run.jar_sha256!==expected||run.header.loaded_jar_sha256!==expected||run.probe_sha256!==input.probe_sha256||run.samples.length!==input.samples)throw Error('Benchmark binary or completion differs');
        const current=JSON.stringify([run.header.java,run.header.os,run.header.processors,run.header.jvm_args]);if(environment&&current!==environment)throw Error('Benchmark runtime settings differ');environment=current;
        const seen=new Set();for(const sample of run.samples){if(!Number.isInteger(sample.id)||sample.id<1||sample.id>input.samples||seen.has(sample.id)||sample.chunks!==input.measured_size**2||sample.width!==input.measured_size||sample.base_chunk!==[1000,-1000,2000,-2000,3000][sample.id-1]||sample.dimension!=='minecraft:overworld'||sample.seed!=='194723'||sample.method!=='direct_generation'||!Number.isSafeInteger(sample.elapsed_nanos)||sample.elapsed_nanos<=0||!Number.isFinite(sample.sampled_heap_peak_bytes)||sample.sampled_heap_peak_bytes<=0||sample.sampled_resident_peak_bytes!==null&&(!Number.isFinite(sample.sampled_resident_peak_bytes)||sample.sampled_resident_peak_bytes<=0))throw Error('Invalid or incomplete direct generation sample');seen.add(sample.id);}
    }
    function summarize(kind,role,profile){
        const records=runs.filter(r=>r.kind===kind&&r.role===role&&r.profile===profile),samples=records.flatMap(r=>r.samples),rss=samples.map(s=>s.sampled_resident_peak_bytes);
        return {pair_seconds:records.sort((a,b)=>a.pair-b.pair).map(r=>r.samples.reduce((total,s)=>total+s.elapsed_nanos/1e9,0)),median_seconds:median(records.map(r=>r.samples.reduce((total,s)=>total+s.elapsed_nanos/1e9,0))),median_sampled_heap_bytes:median(samples.map(s=>s.sampled_heap_peak_bytes)),median_sampled_resident_bytes:rss.every(v=>v!==null)?median(rss):null,requested_chunks:samples.length*input.measured_size**2,gc_count:samples.reduce((total,s)=>total+(s.gc_count||0),0),gc_millis:samples.reduce((total,s)=>total+(s.gc_millis||0),0)};
    }
    const report={format:2,method:'direct_generation',status:'passed',pairs:input.pairs,control_pairs:input.control_pairs,profiles:[]};
    for(const profile of input.profiles){
        const baseline=summarize('comparison','baseline',profile),candidate=summarize('comparison','candidate',profile),left=summarize('control','baseline',profile),right=summarize('control','candidate',profile);
        const generation=change(baseline.median_seconds,candidate.median_seconds),heap=change(baseline.median_sampled_heap_bytes,candidate.median_sampled_heap_bytes),resident=baseline.median_sampled_resident_bytes!==null&&candidate.median_sampled_resident_bytes!==null?change(baseline.median_sampled_resident_bytes,candidate.median_sampled_resident_bytes):null;
        const controlTime=Math.abs(change(left.median_seconds,right.median_seconds)),controlHeap=Math.abs(change(left.median_sampled_heap_bytes,right.median_sampled_heap_bytes)),controlResident=left.median_sampled_resident_bytes!==null&&right.median_sampled_resident_bytes!==null?Math.abs(change(left.median_sampled_resident_bytes,right.median_sampled_resident_bytes)):null;
        const reliable=controlTime<=10&&controlHeap<=15&&controlResident!==null&&controlResident<=15;
        const flagged=generation>10||heap>15||resident!==null&&resident>15;
        const status=!reliable||resident===null?'inconclusive':flagged?'investigate':'passed';
        if(status==='investigate')report.status='investigate';else if(status==='inconclusive'&&report.status==='passed')report.status='inconclusive';
        report.profiles.push({profile,status,baseline,candidate,generation_change_percent:round(generation),heap_change_percent:round(heap),resident_change_percent:resident===null?null:round(resident),identical_jar_control:{generation_difference_percent:round(controlTime),heap_difference_percent:round(controlHeap),resident_difference_percent:controlResident===null?null:round(controlResident),reliable},raw_threshold_flag:flagged});
    }
    return report;
}
