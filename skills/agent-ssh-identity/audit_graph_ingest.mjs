// GPL-3.0-or-later. Operational adapter using the installed OpenPlanner SDK.
// Raw commands/arguments stay in linux_audit; graph entities carry metadata only.
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';

export function compatibleAuditEdgeOps(ops) {
  return ops.map(op=>{
    const write=op.updateOne;
    if(write?.update?.$set?.source!=='linux-audit'||typeof write?.filter?._id!=='string')
      throw Error('audit edge adapter scope');
    return{updateOne:{...write,update:{...write.update,$set:{...write.update.$set,id:write.filter._id}}}};
  });
}

export function graphEvents(records) {
  const events = new Map();
  const harnessNames={'1002':'codex','1003':'grokbot','1004':'opencode','1005':'hermes',
    '1006':'pi','1007':'eta-mu','1008':'knoxx','1009':'claude','1010':'agy'};
  const hash = value => createHash('sha256').update(value).digest('hex');
  for (const record of records) {
    if (record.type !== 'SYSCALL' || !/^100[2-9]$|^1010$/.test(record.auid ?? '')) continue;
    const ts = new Date(record.event_epoch * 1000).toISOString();
    const source = 'linux-audit';
    const project = 'linux-audit';
    const host = `audit:host:${hash(record.source)}`;
    const actor = `audit:principal:${hash(record.source)}:${record.auid}`;
    const effective = `audit:principal:${hash(record.source)}:${record.euid}`;
    const event = `audit:observation:${record._id}`;
    const evidence = {database:'linux_audit',collection:'events',id:record._id,cluster:'Cluster0'};
    const envelope = (id,kind,extra) => ({schema:'openplanner.event.v1',id,ts,source,kind,
      source_ref:{project},text:'',extra:{...extra,lake:project,visibility:'internal',
      provenance:{producer:'audit-graph-projector-v1',evidence},epistemic_tier:extra.epistemic_tier??'observed'}});
    const node = (id,type,label,extra={}) => events.set(`graph.node:${id}`,
      envelope(`graph.node:${id}`,'graph.node',{node_id:id,node_kind:type,label,...extra}));
    const edge = (from,to,relation,tier='observed') => {
      const id=`graph.edge:audit:${hash(`${from}\0${to}\0${relation}`)}`;
      events.set(id,envelope(id,'graph.edge',{source_node_id:from,target_node_id:to,
        edge_kind:relation,layer:tier,epistemic_tier:tier}));
    };
    node(host,'audit.host',record.source,{source_identity:record.source});
    node(actor,'audit.linux_principal',`${harnessNames[record.auid]} (Linux login UID ${record.auid})`,
      {uid:record.auid,host_node_id:host,harness_name:harnessNames[record.auid],
       identity_mapping_tier:'derived',identity_mapping_basis:'agent-ssh-identity/identities.tsv (2026-10-22)'});
    const harness=`audit:harness:${harnessNames[record.auid]}`;
    node(harness,'audit.harness',harnessNames[record.auid],{epistemic_tier:'derived',
      identity_mapping_basis:'agent-ssh-identity/identities.tsv (2026-10-22)'});
    edge(actor,harness,'mapped_to_harness','derived');
    if(effective!==actor)node(effective,'audit.linux_principal',`Linux effective UID ${record.euid}`,{uid:record.euid,host_node_id:host});
    // Do not infer intent, success, file contents or command arguments from an
    // exec record. Numeric login/effective identity and executable path are facts.
    node(event,'audit.syscall',`Audit ${record.serial}: login UID ${record.auid}, effective UID ${record.euid}`,
      {audit_record_id:record._id,audit_serial:record.serial,event_epoch:record.event_epoch,
       login_uid:record.auid,effective_uid:record.euid,audit_session:record.ses,
       executable_path:record.exe,audit_key:record.key});
    edge(event,host,'observed_on');
    edge(event,actor,'login_attributed_to');
    edge(event,effective,'executed_as');
    if (record.exe) {
      const executable=`audit:executable-path:${hash(record.source+'\0'+record.exe)}`;
      node(executable,'audit.executable_path',record.exe,{path:record.exe,host_node_id:host});
      edge(event,executable,'executed_path');
    }
  }
  return [...events.values()];
}

if (process.argv.includes('--test')) {
  const r={_id:'raw1',source:'host:machine',type:'SYSCALL',auid:'1002',euid:'0',ses:'7',
    serial:42,event_epoch:1792725610,exe:'/usr/bin/id',raw:'SECRET ARGUMENT'};
  const events=graphEvents([r]);
  if (events.length!==11 || JSON.stringify(events).includes('SECRET ARGUMENT')) throw Error('projection contract');
  if (events.filter(e=>e.kind==='graph.edge').length!==5) throw Error('edge contract');
  if (JSON.stringify(events)!==JSON.stringify(graphEvents([r]))) throw Error('determinism');
  if (graphEvents([{...r,auid:'0'}]).length) throw Error('scope');
  const op={updateOne:{filter:{_id:'sdk-edge-id'},update:{$set:{source:'linux-audit'},$setOnInsert:{createdAt:'now'}},upsert:true}};
  const adapted=compatibleAuditEdgeOps([op])[0];
  if(adapted.updateOne.update.$set.id!=='sdk-edge-id'||op.updateOne.update.$set.id)
    throw Error('edge ID adapter identity or mutation');
  let rejected=false;
  try{compatibleAuditEdgeOps([{updateOne:{filter:{_id:'translation'},update:{$set:{source:'translation'}}}}]);}
  catch{rejected=true;}
  if(!rejected)throw Error('edge ID adapter foreign write');
  console.log('PASS deterministic identities, typed nodes/edges, agent scope, argument exclusion');
} else {
  const records=JSON.parse(readFileSync(0,'utf8'));
  const {createOpenPlannerSdk}=await import('/app/node_modules/@open-hax/openplanner-sdk/dist/index.js');
  const {upsertGraphEdges}=await import('/app/node_modules/@open-hax/openplanner-sdk/dist/mongodb.js');
  const sdk=await createOpenPlannerSdk({log:{warn(){},error(){},info(){},debug(){}}});
  try {
    // Knoxx's translation graph shares this collection and requires unique id.
    // Keep the SDK's identity unchanged, merely mirror it into that required
    // field for our own audit-edge operations. Never relax the existing index.
    const graphEdges=sdk.mongo.graphEdges;
    sdk.mongo.graphEdges=new Proxy(graphEdges,{get(target,property){
      if(property==='bulkWrite')return(ops,options)=>target.bulkWrite(compatibleAuditEdgeOps(ops),options);
      const value=Reflect.get(target,property,target);
      return typeof value==='function'?value.bind(target):value;
    }});
    const events=graphEvents(records);
    const ids=events.map(e=>e.id);
    const existing=new Set(await sdk.mongo.events.distinct('id',{id:{$in:ids}}));
    const missing=events.filter(e=>!existing.has(e.id));
    // Use bounded parallel SDK batches. The SDK admits each event serially;
    // bounded batches avoid a network round trip per event becoming a large
    // historical replay delay, without bypassing validation or admission.
    const chunks=[];
    for(let i=0;i<missing.length;i+=32)chunks.push(missing.slice(i,i+32));
    let next=0;
    await Promise.all(Array.from({length:Math.min(8,chunks.length)},async()=>{
      while(next<chunks.length){const chunk=chunks[next++];
        const result=await sdk.ingestEvents(chunk);await result.backgroundIndexing;}
    }));
    // Repair projected edges on retry even if an earlier attempt admitted an
    // edge event and crashed before its projection was written.
    const edges=events.filter(e=>e.kind==='graph.edge').map(e=>({
      source_node_id:e.extra.source_node_id,target_node_id:e.extra.target_node_id,
      edge_kind:e.extra.edge_kind,layer:e.extra.layer,project:'linux-audit',source:'linux-audit',
      data:e.extra,updated_at:new Date(e.ts)}));
    if(edges.length)await upsertGraphEdges(sdk.mongo.graphEdges,edges);
    const count=await sdk.mongo.events.countDocuments({id:{$in:ids}});
    if(count!==ids.length)throw Error('graph event coverage mismatch');
    console.log(JSON.stringify({audit_graph_ack:records.map(r=>r._id)}));
  } finally {await sdk.close();}
}
