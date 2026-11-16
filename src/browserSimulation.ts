/** Contact simulator for static hosting. Global telemetry never forwards a packet. */
export type Packet = {id:string;source:string;destination:string;text:string;priority:'sos'|'routine';created:number;expires:number;kind:'message'|'ack';path:string[];incident:string;version:number;message_id?:string;location?:{lat:number;lon:number}};
export type SimMessage = Packet & {status:string;delivered:number|null;confirmed:number|null;acknowledged:number|null;ack_path?:string[]};
export type SimNode = {id:string;name:string;role:string;x:number;y:number;online:boolean;battery:number;direction?:number};
type SimEvent = {id:string;time:number;kind:string;text:string;message:string|null};
type World = {schema:1;time:number;running:boolean;config:{loss:number;speed:number;storm:boolean;routing:'direct'|'dtn'};nodes:SimNode[];messages:SimMessage[];events:SimEvent[];attempts:number;transfers:number;failed:number;random:number;queues:Record<string,Record<string,Packet>>;heads:Record<string,Record<string,number>>};
export type StorageLike = Pick<Storage,'getItem'|'setItem'>;
const KEY = 'desertlink.simulator.v1';
const clone = <T,>(value:T):T => structuredClone(value);
export class BrowserSimulation {
  private world!:World;
  persistent = true;
  constructor(private readonly storage?:StorageLike) {
    try {
      const raw = storage?.getItem(KEY);
      if (raw) {
        const saved = JSON.parse(raw) as World;
        if (saved.schema!==1 || !Array.isArray(saved.nodes) || saved.nodes.length!==5 || !saved.queues || !saved.heads || !Array.isArray(saved.messages) || !Array.isArray(saved.events)) throw new Error('Invalid stored scenario');
        this.world = saved; this.world.running = false;
      } else this.reset();
    } catch { this.reset(); }
    this.persistent = !!storage;
    this.save();
  }
  private random() { let x=this.world.random|0; x^=x<<13; x^=x>>>17; x^=x<<5; this.world.random=x>>>0; return this.world.random/4294967296; }
  private event(kind:string,text:string,message:string|null=null) { this.world.events.unshift({id:crypto.randomUUID(),time:this.world.time,kind,text,message});this.world.events.length=Math.min(200,this.world.events.length); }
  private save() { if(this.storage)try{this.storage.setItem(KEY,JSON.stringify(this.world));this.persistent=true;}catch{this.persistent=false;} }
  reset() {
    const nodes:SimNode[]=[
      {id:'safari',name:'Safari 01',role:'stranded',x:1.8,y:6.4,online:true,battery:86},
      {id:'relay1',name:'Relay 01',role:'mobile',x:2.8,y:6,online:true,battery:94,direction:1},
      {id:'relay2',name:'Relay 02',role:'mobile',x:10.2,y:3.6,online:true,battery:78,direction:-1},
      {id:'ridge',name:'Ridge repeater',role:'repeater',x:8,y:7.5,online:true,battery:98},
      {id:'base',name:'Rescue base',role:'base',x:14,y:2.6,online:true,battery:100}
    ];
    this.world={schema:1,time:0,running:false,config:{loss:15,speed:40,storm:false,routing:'dtn'},nodes,messages:[],events:[],attempts:0,transfers:0,failed:0,random:7,queues:{},heads:{}};
    for(const n of nodes){this.world.queues[n.id]={};this.world.heads[n.id]={};}
    this.event('system','Scenario ready. Safari 01 has no direct radio contact with rescue base.');this.save();
  }
  private inventory(id:string) { return Object.values(this.world.queues[id]); }
  private put(node:string,packet:Packet) {
    const queue=this.world.queues[node];
    if(queue[packet.id])return false;
    if(packet.kind==='message'){
      const head=this.world.heads[node][packet.incident]||0;
      if(head>packet.version)return false;
      this.world.heads[node][packet.incident]=packet.version;
      for(const old of Object.values(queue))if(old.kind==='message'&&old.incident===packet.incident&&old.version<packet.version)delete queue[old.id];
    }
    queue[packet.id]=clone(packet);return true;
  }
  links():[SimNode,SimNode][] {
    const active=this.world.nodes.filter(n=>n.online&&n.battery>0),range=this.world.config.storm?1.45:2.6;
    const result:[SimNode,SimNode][]=[];
    active.forEach((a,i)=>active.slice(i+1).forEach(b=>{if(Math.hypot(a.x-b.x,a.y-b.y)<=range)result.push([a,b]);}));return result;
  }
  command(action:string,args:Record<string,unknown>={}) {
    const w=this.world;
    if(action==='reset')this.reset();
    else if(action==='play'||action==='pause')w.running=action==='play';
    else if(action==='step')this.step(60);
    else if(action==='toggle_node'){
      const node=w.nodes.find(n=>n.id===args.node_id);if(!node)throw Error('Unknown node');node.online=!node.online;
      this.event('system',`${node.name} ${node.online?'reconnected':'disconnected'}. Stored packets retained.`);
    }else if(action==='configure'){
      if(args.loss!==undefined){if(typeof args.loss!=='number'||args.loss<0||args.loss>100)throw Error('Packet loss must be between 0 and 100');w.config.loss=args.loss;}
      if(args.speed!==undefined){if(typeof args.speed!=='number'||args.speed<0||args.speed>80)throw Error('Speed must be between 0 and 80');w.config.speed=args.speed;}
      if(typeof args.storm==='boolean')w.config.storm=args.storm;
      if(args.routing==='direct'||args.routing==='dtn')w.config.routing=args.routing;
    }else if(action==='send'){
      if(w.messages.length>=200)throw Error('Scenario limit reached. Reset before sending more messages.');
      const text=typeof args.text==='string'?args.text.trim():'Vehicle immobilized. Two passengers need assistance.';
      const ttl=typeof args.ttl==='number'?args.ttl:3600;
      if(!text||text.length>160||ttl<60||ttl>86400)throw Error('Use 1–160 characters and a valid lifetime.');
      const incident=typeof args.incident==='string'?args.incident:null;
      const previous=incident?w.messages.filter(m=>m.incident===incident):[];
      if(incident&&!previous.length)throw Error('Unknown incident');
      const version=Math.max(0,...previous.map(m=>m.version))+1,id=crypto.randomUUID().slice(0,8);
      const packet:Packet={id,incident:incident||id,version,source:'safari',destination:'base',text,priority:args.priority==='routine'?'routine':'sos',created:w.time,expires:w.time+ttl,kind:'message',path:['safari'],location:{lat:24.8412,lon:55.7318}};
      this.put('safari',packet);w.messages.unshift({...clone(packet),status:'queued',delivered:null,confirmed:null,acknowledged:null});
      this.event('queued',`${packet.priority.toUpperCase()} v${version} stored at Safari 01. Waiting for radio contact.`,id);
    }else if(action==='ack'){
      const m=w.messages.find(m=>m.id===args.message_id);if(!m||m.delivered===null)throw Error('Message has not reached rescue base');
      if(!w.nodes.find(n=>n.id==='base')?.online)throw Error('Rescue base is offline');
      if(m.confirmed===null){
        const ack:Packet={id:'ack-'+m.id,incident:m.incident,version:m.version,source:'base',destination:'safari',text:'Rescue operator confirmed this revision.',priority:'sos',kind:'ack',message_id:m.id,created:w.time,expires:w.time+3600,path:['base']};
        this.put('base',ack);m.confirmed=w.time;m.status='ack_returning';this.event('ack',`Operator confirmed v${m.version}. ACK queued for return journey.`,m.id);
      }
    }else throw Error('Unknown action');
    this.save();return this.snapshot();
  }
  tick() { if(this.world.running)this.step(60);return this.snapshot(); }
  step(seconds=60) {
    const w=this.world;w.time+=seconds;
    const start=[2.2,6.2],end=[13.5,2.8],dx=end[0]-start[0],dy=end[1]-start[1],length=Math.hypot(dx,dy);
    for(const node of w.nodes){
      if(!node.online)continue;
      if(node.role==='mobile'&&node.battery>0){
        let fraction=((node.x-start[0])*dx+(node.y-start[1])*dy)/length**2;
        fraction+=(node.direction||1)*w.config.speed*seconds/3600/length;
        while(fraction>1||fraction<0){if(fraction>1){fraction=2-fraction;node.direction=-1;}else{fraction=-fraction;node.direction=1;}}
        node.x=start[0]+fraction*dx;node.y=start[1]+fraction*dy;
      }
      if(node.role!=='base')node.battery=Math.max(0,node.battery-seconds/18000);
    }
    for(const node of w.nodes)for(const p of this.inventory(node.id))if(p.expires<=w.time)delete w.queues[node.id][p.id];
    for(const m of w.messages)if(m.expires<=w.time&&m.delivered===null&&m.status!=='expired'){m.status='expired';this.event('expired','Message lifetime elapsed before destination delivery.',m.id);}
    // Freeze each independent queue at the start of the contact round.
    const inventories=Object.fromEntries(w.nodes.map(n=>[n.id,clone(this.inventory(n.id))]));
    for(const [a,b] of this.links())for(const [sender,receiver] of [[a,b],[b,a]]){
      let candidates=inventories[sender.id].filter(p=>!w.queues[receiver.id][p.id]&&p.expires>w.time&&!(p.kind==='message'&&(w.heads[receiver.id][p.incident]||0)>p.version));
      if(w.config.routing==='direct')candidates=candidates.filter(p=>p.source===sender.id&&p.destination===receiver.id);
      candidates.sort((a,b)=>Number(a.priority!=='sos')-Number(b.priority!=='sos')||a.expires-b.expires||a.created-b.created);
      for(const packet of candidates.slice(0,1)){
        w.attempts++;
        const loss=Math.min(100,w.config.loss+(w.config.storm?25:0));
        if(this.random()*100<loss){w.failed++;this.event('loss',`Packet lost: ${sender.name} → ${receiver.name}. Sender retains its copy.`,packet.id);continue;}
        const received={...clone(packet),path:[...packet.path,receiver.id]};
        if(!this.put(receiver.id,received))continue;
        w.transfers++;this.event('transfer',`${sender.name} → ${receiver.name} · ${packet.kind==='ack'?'ACK':packet.priority.toUpperCase()} v${packet.version}`,packet.id);
        const m=w.messages.find(m=>m.id===(packet.message_id||packet.id));if(!m)continue;
        if(receiver.id===packet.destination){
          if(packet.kind==='ack'&&m.acknowledged===null){m.acknowledged=w.time;m.status='acknowledged';m.ack_path=received.path;this.event('success',`ACK for v${m.version} reached Safari 01.`,m.id);}
          else if(packet.kind==='message'&&m.delivered===null){m.delivered=w.time;m.status='delivered';m.path=received.path;this.event('delivered',`v${m.version} reached rescue base. Awaiting human acknowledgment.`,m.id);}
        }else if(packet.kind==='message'&&m.status==='queued')m.status='relayed';
      }
    }
    this.save();
  }
  snapshot(){
    const w=this.world,delivered=w.messages.filter(m=>m.delivered!==null);
    return clone({time:w.time,running:w.running,config:w.config,nodes:w.nodes.map(n=>({...n,queue:this.inventory(n.id).length,packets:this.inventory(n.id).map(p=>p.id)})),links:this.links().map(([a,b])=>[a.id,b.id]),messages:w.messages,events:w.events,attempts:w.attempts,transfers:w.transfers,failed:w.failed,metrics:{sent:w.messages.length,delivered:delivered.length,delivery_rate:w.messages.length?Math.round(100*delivered.length/w.messages.length):0}});
  }
}
