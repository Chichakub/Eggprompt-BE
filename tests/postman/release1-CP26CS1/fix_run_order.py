"""Create a rerunnable, ordered collection; never sends API requests."""
import json
from pathlib import Path
from copy import deepcopy
root=Path(__file__).resolve().parent
c=json.loads((root/'EggPrompt-Release1-Aligned.postman_collection.json').read_text())
def event(kind,code):return {'listen':kind,'script':{'type':'text/javascript','exec':code.splitlines()}}
def request(id,title,body,test,pre='',role=None):
 r={'name':id+' | '+title,'request':{'method':'POST','header':[{'key':'Content-Type','value':'application/json'}],'auth':{'type':'noauth'},'url':'{{baseUrl}}/v1/auth/login','body':{'mode':'raw','raw':json.dumps(body,indent=2),'options':{'raw':{'language':'json'}}}},'event':[],'response':[]}
 if role:
  r['request']['url']='{{baseUrl}}/v1/auth/change-password';r['request']['auth']={'type':'bearer','bearer':[{'key':'token','value':'{{fixtureRecoveryToken}}','type':'string'}]}
 if pre:r['event'].append(event('prerequest',pre))
 r['event'].append(event('test',test));return r
prep=[]
prep.append(request('R1-PREP-001','Check dedicated password account initial state',{'username':'pm.r1.password','password':'{{testPassword}}'},'''const code = pm.response.code;
pm.test('Preparation login is 200 or 401', () => { if (![200,401].includes(code)) throw Error('Unexpected fixture login status '+code); });
pm.collectionVariables.set('fixtureNeedsRecovery',code===401?'true':'false');
if(code===200) pm.test('Dedicated fixture returns token',()=>{if(!pm.response.json().accessToken) throw Error('Missing token');});''', '''pm.collectionVariables.set('fixtureNeedsRecovery','false');
pm.collectionVariables.unset('fixtureRecoveryToken');
// Clear old tokens, so failed logins cannot silently reuse previous-run sessions.
['passwordToken','superToken','adminToken','staffToken','otherToken','qaToken','logoutToken'].forEach(k=>pm.collectionVariables.unset(k));'''))
prep.append(request('R1-PREP-002','Recover only if previous run left Changed password',{'username':'pm.r1.password','password':'{{changedPassword}}'},'''pm.test('Recovery login succeeds',()=>{if(pm.response.code!==200) throw Error('Dedicated fixture matches neither known test password. Reset only pm.r1.password; do not change real accounts.');});
if(pm.response.code===200){
 const token=pm.response.json().accessToken;
 pm.test('Recovery token exists',()=>{if(!token)throw Error('Missing recovery token');});
 if(token)pm.collectionVariables.set('fixtureRecoveryToken',token);
} else { pm.execution.setNextRequest(null); }''',"if(pm.collectionVariables.get('fixtureNeedsRecovery')!=='true') pm.execution.skipRequest();"))
prep.append(request('R1-PREP-003','Return dedicated fixture to initial password before tests',{'currentPassword':'{{changedPassword}}','password':'{{testPassword}}','confirmPassword':'{{testPassword}}'},'''pm.test('Fixture password restored before business tests',()=>{if(pm.response.code!==200)throw Error('Could not restore fixture password');});
if(pm.response.code===200){pm.collectionVariables.set('fixtureNeedsRecovery','false');pm.collectionVariables.unset('fixtureRecoveryToken');}else{pm.execution.setNextRequest(null);}''',"if(pm.collectionVariables.get('fixtureNeedsRecovery')!=='true') pm.execution.skipRequest();",role='recovery'))
folders=[]
setup=next(f for f in c['item'] if f['name'].startswith('00 '));setup['name']='01 Setup accounts and data';folders.append(setup)
for i in range(1,13):
 f=next(f for f in c['item'] if f['name'].startswith(f'PBI-{i:02} '));f['name']=f'{i+1:02} '+f['name'];folders.append(f)
last=next(f for f in c['item'] if f['name'].startswith('99 '));last['name']='99 Restore test password (always last)';folders.append(last)
c['item']=[{'name':'00 Prepare test password automatically','item':prep}]+folders
c['info']['name']='EggPrompt Release 1 v2 - RUN ALL'
c['info']['description']='Excel-aligned API tests. Select EggPrompt SIT and run all folders in the supplied order. Automatically recovers only the dedicated pm.r1.password account if a previous run left Changed@123. Does not change expected farm isolation assertions. UI checks still required. Not live-tested by generator.'
(root/'EggPrompt-Release1-Ready-To-Run.postman_collection.json').write_text(json.dumps(c,ensure_ascii=False,indent=2)+'\n')
assert c['item'][0]['name'].startswith('00 ') and c['item'][-1]['name'].startswith('99 ')
print('Saved v2:',sum(len(f['item']) for f in c['item']),'requests, explicit numeric order, automatic test-password preparation.')
