import test from 'node:test';
import assert from 'node:assert/strict';
import { BrowserSimulation } from '../.tools/browser-tests/browserSimulation.js';

class MemoryStorage { values=new Map();getItem(k){return this.values.get(k)??null;}setItem(k,v){this.values.set(k,v);} }
function engine(){const sim=new BrowserSimulation(new MemoryStorage());sim.command('configure',{loss:0});return sim;}
function until(sim,condition,max=80){for(let i=0;i<max;i++){if(condition(sim.snapshot()))return;sim.step(60);}assert.ok(condition(sim.snapshot()),'Expected contact outcome did not occur');}

test('partition requires a relay and ACK has a real return journey',()=>{
 const sim=engine();const first=sim.command('send').messages[0];
 assert.equal(sim.snapshot().nodes.find(n=>n.id==='base').queue,0);
 sim.step();assert.equal(sim.snapshot().messages[0].status,'relayed');assert.equal(sim.snapshot().messages[0].delivered,null);
 until(sim,s=>s.messages[0].delivered!==null);
 assert.ok(sim.snapshot().messages[0].path.length>2);
 sim.command('ack',{message_id:first.id});assert.equal(sim.snapshot().messages[0].acknowledged,null);
 until(sim,s=>s.messages[0].acknowledged!==null);
 assert.equal(sim.snapshot().messages[0].status,'acknowledged');
});

test('complete packet loss retains the source copy and recovers later',()=>{
 const sim=engine();sim.command('configure',{loss:100,speed:0});const m=sim.command('send').messages[0];
 for(let i=0;i<4;i++)sim.step();
 assert.equal(sim.snapshot().transfers,0);assert.ok(sim.snapshot().nodes.find(n=>n.id==='safari').packets.includes(m.id));
 sim.command('configure',{loss:0,speed:40});until(sim,s=>s.messages[0].delivered!==null);
});

test('reload preserves independent node queues and pauses playback',()=>{
 const storage=new MemoryStorage(),sim=new BrowserSimulation(storage);sim.command('configure',{loss:0});const m=sim.command('send').messages[0];sim.step();sim.command('play');
 const restored=new BrowserSimulation(storage),state=restored.snapshot();assert.equal(state.running,false);assert.equal(state.time,60);
 assert.ok(state.nodes.find(n=>n.id==='relay1').packets.includes(m.id));assert.equal(state.nodes.find(n=>n.id==='base').queue,0);
});

test('direct-only baseline cannot cross the partition',()=>{
 const sim=engine();sim.command('configure',{routing:'direct'});sim.command('send',{ttl:600});for(let i=0;i<11;i++)sim.step();
 assert.equal(sim.snapshot().metrics.delivered,0);assert.equal(sim.snapshot().messages[0].status,'expired');assert.equal(sim.snapshot().transfers,0);
});

test('SOS is prioritized under a one-packet contact budget and duplicates stop',()=>{
 const sim=engine();sim.command('configure',{speed:0});sim.command('send',{priority:'routine',text:'Routine'});const sos=sim.command('send',{text:'SOS'}).messages[0];sim.step();
 assert.deepEqual(sim.snapshot().nodes.find(n=>n.id==='relay1').packets,[sos.id]);sim.step();sim.step();assert.equal(sim.snapshot().transfers,2);
});

test('isolated update does not reach a disconnected relay until contact resumes',()=>{
 const sim=engine();sim.command('configure',{speed:0});const old=sim.command('send',{text:'Initial'}).messages[0];sim.step();sim.command('toggle_node',{node_id:'relay1'});
 const fresh=sim.command('send',{text:'Medical emergency',incident:old.incident}).messages[0];sim.step();
 assert.deepEqual(sim.snapshot().nodes.find(n=>n.id==='relay1').packets,[old.id]);
 sim.command('toggle_node',{node_id:'relay1'});sim.step();assert.deepEqual(sim.snapshot().nodes.find(n=>n.id==='relay1').packets,[fresh.id]);assert.equal(fresh.version,2);
});

test('ACK for v1 cannot confirm the source’s isolated v2',()=>{
 const sim=engine();const old=sim.command('send',{text:'Initial'}).messages[0];sim.step();sim.command('toggle_node',{node_id:'safari'});
 const next=sim.command('send',{text:'Updated urgency',incident:old.incident}).messages[0];
 until(sim,s=>s.messages.find(m=>m.id===old.id).delivered!==null);
 sim.command('ack',{message_id:old.id});sim.command('toggle_node',{node_id:'safari'});
 until(sim,s=>s.messages.find(m=>m.id===old.id).acknowledged!==null);
 const second=sim.snapshot().messages.find(m=>m.id===next.id);assert.equal(second.confirmed,null);assert.equal(second.acknowledged,null);
});

test('invalid saved data resets safely and a base cannot ACK an unseen message',()=>{
 const storage=new MemoryStorage();storage.setItem('desertlink.simulator.v1','not-json');const sim=new BrowserSimulation(storage);
 assert.equal(sim.snapshot().nodes.length,5);assert.throws(()=>sim.command('ack',{message_id:'unknown'}));
});
