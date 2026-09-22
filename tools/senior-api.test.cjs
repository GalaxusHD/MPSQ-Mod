// Runs the actual Edge Function with a private, in-memory REST database.
// No network connection or production token is used.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const { stripTypeScriptTypes } = require('node:module');
const { webcrypto, createHash } = require('node:crypto');
const ids = { root: 'root-id', other: 'other-id', duplicate: 'duplicate-id' };
let handler;
let count = 0;
let tables;
function reset(active = null) {
  tables = {
    mpsq_clients: Object.entries(ids).map(([who,id]) => ({id, display_name:who === 'other' ? 'Other' : 'MP_SquidGame', token_hash:createHash('sha256').update(who).digest('hex')})),
    mpsq_team_profiles: Object.entries(ids).map(([who,id]) => ({client_id:id, base_rank:who === 'root' ? 'sr_offizier' : 'spieler', active_rank:who === 'root' ? active : null, name_visible:true})),
    mpsq_team_root:[{id:1,root_client_id:ids.root,root_display_name:'MP_SquidGame'}],
    mpsq_team_rank_log:[],mpsq_team_rank_requests:[],mpsq_team_templates:[],mpsq_team_todos:[],mpsq_team_timer:[]
  };
}
function matches(row, query) {
  for (const [key, filter] of query) {
    if (['select','order','limit','offset','on_conflict'].includes(key)) continue;
    if (filter.startsWith('eq.') && String(row[key]) !== filter.slice(3)) return false;
    if (filter.startsWith('in.(') && !filter.slice(4,-1).split(',').includes(String(row[key]))) return false;
    if (filter.startsWith('lt.') && !(String(row[key]) < filter.slice(3))) return false;
  }
  return true;
}
async function fakeFetch(url, init = {}) {
  const parsed = new URL(url);
  assert.equal(parsed.hostname, 'fake.invalid', 'unexpected network request');
  const table = parsed.pathname.split('/').pop();
  assert.ok(table in tables, 'unmocked table '+table);
  const rows = tables[table];
  let selected = rows.filter(row => matches(row, parsed.searchParams));
  if (parsed.searchParams.has('offset')) selected = selected.slice(Number(parsed.searchParams.get('offset')));
  const method = init.method || 'GET';
  const data = init.body ? JSON.parse(init.body) : null;
  if (method === 'PATCH') selected.forEach(row => Object.assign(row,data));
  if (method === 'DELETE') tables[table] = rows.filter(row => !selected.includes(row));
  if (method === 'POST') {
    const conflict = parsed.searchParams.get('on_conflict');
    const existing = conflict && rows.find(row => row[conflict] === data[conflict]);
    if (existing) { Object.assign(existing,data); selected=[existing]; }
    else { const row={id:'row-'+rows.length,...data}; rows.push(row); selected=[row]; }
  }
  return new Response(JSON.stringify(selected), {status:200, headers:{'Content-Type':'application/json'}});
}
let source = fs.readFileSync(path.join(__dirname,'../supabase/functions/mpsq-api/index.ts'),'utf8');
source = source.replace(/^import .*;\r?\n/gm,'');
const code = stripTypeScriptTypes(source, {mode:'transform'});
vm.runInNewContext(code, {
  serve: fn => handler=fn, AwsClient:class {},
  Deno:{env:{get:key=>({SUPABASE_URL:'https://fake.invalid',SUPABASE_SERVICE_ROLE_KEY:'test-only',ADMIN_PASSWORD:'test-admin'})[key]}},
  fetch:fakeFetch, URL, Response, Request, Headers, TextEncoder, crypto:webcrypto, console
});
async function request(who, route, method='GET', body, expected=200) {
  const headers={'x-mpsq-token':who,'Content-Type':'application/json'};
  if(who === 'admin') headers['x-admin-password']='test-admin';
  const result=await handler(new Request('https://fake.invalid/functions/v1/mpsq-api'+route,{method,headers,...(body === undefined ? {} : {body:JSON.stringify(body)})}));
  const json=await result.json();
  assert.equal(result.status,expected,`${who} ${method} ${route}: ${JSON.stringify(json)}`);
  count++;
  return json;
}
(async()=>{
  for(const active of [null,'vip','spieler','streamer','001','soldat','arbeiter','offizier','frontman']) {
    reset(active);
    await request('root','/team/templates');
    await request('root','/team/templates','POST',{text:'Test',speaker:'offizier'},201);
    await request('root','/team/templates/row-0','PATCH',{text:'Edited',speaker:'frontman'});
    await request('root','/team/templates/row-0','DELETE');
    await request('root','/team/timer','POST',{running:true,durationSeconds:60});
    await request('root','/team/timer');
    await request('root','/team/todos');
    await request('root','/team/todos','POST',{text:'Test',listKey:'offizier'},201);
    for(const rank of ['offizier','frontman','spieler']) {
      await request('root',`/team/members/${ids.other}/rank`,'POST',{rank});
      assert.equal(tables.mpsq_team_profiles.find(x=>x.client_id===ids.other).base_rank,rank);
    }
    await request('root',`/team/members/${ids.other}/event-rank`,'POST',{rank:'001'});
    await request('root',`/team/members/${ids.other}/event-rank`,'DELETE');
    await request('root',`/team/members/${ids.root}/event-rank`,'POST',{rank:'vip'});
    await request('root',`/team/members/${ids.root}/event-rank`,'DELETE');
    await request('root',`/team/members/${ids.root}/rank`,'POST',{rank:'spieler'},403);
    await request('root','/team/rank-requests','POST',{targetId:ids.root,rank:'spieler'},403);
    assert.equal(tables.mpsq_team_profiles.find(x=>x.client_id===ids.root).base_rank,'sr_offizier');
    await request('duplicate','/team/templates','GET',undefined,403);
    await request('duplicate',`/team/members/${ids.other}/rank`,'POST',{rank:'offizier'},403);
    await request('duplicate',`/team/members/${ids.root}/event-rank`,'POST',{rank:'vip'},403);
    await request('duplicate',`/team/members/${ids.other}/event-rank`,'POST',{rank:'001'},403);
  }
  reset('001');
  tables.mpsq_team_rank_requests.push({id:'old-request',target_id:ids.root,requested_rank:'spieler',status:'PENDING'});
  await request('admin','/admin/rank-requests/old-request/decision','POST',{approved:true},403);
  assert.equal(tables.mpsq_team_profiles.find(x=>x.client_id===ids.root).base_rank,'sr_offizier');
  console.log(`Sr-Offizier API: ${count} route cases passed (real handler, mocked database).`);
})().catch(error=>{console.error(error);process.exitCode=1});
