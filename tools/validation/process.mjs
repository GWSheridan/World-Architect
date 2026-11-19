import {spawn} from 'node:child_process';
import {createWriteStream} from 'node:fs';
import {join} from 'node:path';

export function gradle(root,args,logFile) {
    const quote = s => {if(/["\r\n%&|<>^]/.test(s))throw new Error('Unsupported command argument');return `"${s}"`;};
    const child=process.platform==='win32'
        ?spawn(process.env.ComSpec||'cmd.exe',['/d','/s','/c',`"${[join(root,'gradlew.bat'),...args].map(quote).join(' ')}"`],{cwd:root,windowsHide:true,windowsVerbatimArguments:true})
        :spawn('sh',[join(root,'gradlew'),...args],{cwd:root});
    const log=createWriteStream(logFile);let text='',ended=false;
    for(const stream of [child.stdout,child.stderr])stream.on('data',bytes=>{log.write(bytes);text=(text+bytes).slice(-4_000_000);});
    const exit=new Promise((accept,reject)=>{child.on('error',reject);child.on('close',code=>{ended=true;log.end();accept(code);});});
    const send=command=>{if(!ended)child.stdin.write(command+'\n');};
    async function wait(marker,seconds=180) {
        const deadline=Date.now()+seconds*1000;
        while(!text.includes(marker)){if(ended||Date.now()>deadline)throw new Error(`Missing ${marker}; inspect ${logFile}`);await new Promise(done=>setTimeout(done,200));}
    }
    async function stop() {
        if(ended)return;send('stop');await Promise.race([exit,new Promise(done=>setTimeout(done,20000).unref())]);
        if(!ended) {
            if(process.platform==='win32')await new Promise(done=>spawn('taskkill',['/PID',String(child.pid),'/T','/F'],{windowsHide:true}).on('close',done));
            else child.kill('SIGTERM');
        }
    }
    return {child,send,wait,exit,stop};
}
