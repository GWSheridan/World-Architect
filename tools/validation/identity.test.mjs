import test from 'node:test';import assert from 'node:assert/strict';import {identityFromMetadata} from './identity.mjs';
import {profileProperties} from './profiles.mjs';
test('historical profile identities include escaped Java property separators',()=>{
    const text='level-type=worldsunwritten\\:void_demo\nplain=worldsunwritten:single_block';
    assert.equal(profileProperties(text,{id:'worldarchitect'}),'level-type=worldarchitect\\:void_demo\nplain=worldarchitect:single_block');
    assert.equal(profileProperties(text,{id:'worldsunwritten'}),text);
});
test('current primary metadata wins over the frozen compatibility module',()=>{
    const identity=identityFromMetadata('[[mods]]\nmodId="worldsunwritten"\nversion="2.0.0"\n[[mods]]\nmodId="worldarchitect"\nversion="1.9.0"\n[[mixins]]\nconfig="current.json"');
    assert.equal(identity.id,'worldsunwritten');assert.equal(identity.version,'2.0.0');assert.equal(identity.runtimeFile,'Worlds-Unwritten.jar');assert.equal(identity.command('island'),'worldsunwritten island');
});
test('historical artifacts keep their version and command contract regardless of filename',()=>{
    const identity=identityFromMetadata('[[mods]]\nmodId="worldarchitect"\nversion="1.3.0"');assert.equal(identity.id,'worldarchitect');assert.equal(identity.command('island'),'wa_island');assert.equal(identity.version,'1.3.0');
});
test('ambiguous or incomplete metadata is rejected instead of selecting a guessed namespace',()=>{
    for(const metadata of ['[[mods]]\nmodId="unrelated"\nversion="1.0.0"','[[mods]]\nmodId="worldsunwritten"','[[mods]]\nmodId="worldsunwritten"\nversion="2.0.0"\n[[mods]]\nmodId="worldsunwritten"\nversion="2.0.1"'])assert.throws(()=>identityFromMetadata(metadata));
});
