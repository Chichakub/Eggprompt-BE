"""Adapt Postman requests to the 41 supplied manual Release 1 cases.
Does not alter the input workbook, deploy software, or execute APIs.
"""
import ast,types,json,re,html,sys,zipfile,xml.etree.ElementTree as ET
from pathlib import Path
from copy import deepcopy
ROOT=Path(__file__).resolve().parent
basepath=ROOT.parent/'EggPrompt-Postman/generate.py'
if not basepath.exists(): basepath=ROOT.parent/'generate.py'
b=types.ModuleType('base')
parsed=ast.parse(basepath.read_text())
selected=[n for n in parsed.body if isinstance(n,(ast.Import,ast.ImportFrom,ast.FunctionDef)) or (isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id in {'farm','values','pre'} for t in n.targets))]
exec(compile(ast.Module(body=selected,type_ignores=[]),str(basepath),'exec'),b.__dict__)
b.folders={};b.rows=[]
source_path=Path(sys.argv[1]) if len(sys.argv)>1 else Path('/Users/txrn/Downloads/Eggprompt_Release1_Test_Scripts.xlsx')
ns={'m':'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
source=[]
with zipfile.ZipFile(source_path) as z:
 strings=[]
 if 'xl/sharedStrings.xml' in z.namelist():
  strings=[''.join(si.itertext()) for si in ET.fromstring(z.read('xl/sharedStrings.xml')).findall('m:si',ns)]
 rels={r.attrib['Id']:r.attrib['Target'] for r in ET.fromstring(z.read('xl/_rels/workbook.xml.rels'))}
 for sheet in ET.fromstring(z.read('xl/workbook.xml')).findall('m:sheets/m:sheet',ns):
  target=rels[sheet.attrib['{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id']]
  target=target.lstrip('/') if target.startswith('/') else 'xl/'+target
  rows=[]
  for row in ET.fromstring(z.read(target)).findall('m:sheetData/m:row',ns):
   vals={}
   for cell in row.findall('m:c',ns):
    value=cell.find('m:v',ns);value=value.text if value is not None else ''
    if cell.attrib.get('t')=='s':value=strings[int(value)] if value else ''
    if cell.attrib.get('t')=='inlineStr':value=''.join(cell.find('m:is',ns).itertext())
    if value:vals[cell.attrib['r']]=value
   if vals:rows.append(vals)
  source.append({'sheet':sheet.attrib['name'],'rows':rows})
cases=[]
for sheet in source:
 if sheet['sheet']=='Test Scripts':
  for row in sheet['rows']:
   r={re.sub(r'\d','',k):v for k,v in row.items()}
   if r.get('A','').startswith('R1-'):cases.append(r)
byid={r['A']:r for r in cases}
requestmap={};notes={};mode={}
def add(case,step,title,method,path,status,role='admin',body=None,check='',save=None,headers=None,pre='',manual=''):
 folder=byid[case]['B']+' '+next((r.get('C','') for sh in source if sh['sheet']=='Release Summary' for row in sh['rows'] for r in [{re.sub(r'\d','',k):v for k,v in row.items()}] if r.get('A')==byid[case]['B']), '')
 id=case+f'.{step:02}'
 b.add(folder,id,title,method,path,status,role,body,check,save,headers,pre,note='Excel '+case+': '+byid[case]['C']+'\nOriginal expected: '+byid[case]['J']+'\n'+manual)
 requestmap.setdefault(case,[]).append(id)
 if manual:notes[case]=manual
 mode.setdefault(case,'API + manual UI checks')
 return b.folders[folder][-1]
def shape(s):return b.shape(s)
def h(name,cap=100,**kw):return dict(b.house(name),capacity=cap,**kw)
def f(code,house='{{houseId}}',count=100,**kw):return b.flock(code,houseId=house,initialCount=count,**kw)
def e(**kw):return b.egg(gradeJumboQty=10,grade0Qty=20,grade1Qty=30,grade2Qty=0,grade3Qty=0,grade4Qty=0,grade5Qty=0,defectiveQty=3,**kw)
# Setup is intentionally before all PBI cases; deterministic isolated fixtures.
S='00 Setup (run first)'
for n,role,user,pwd in [(1,'super','{{superUsername}}','{{superPassword}}'),(2,'admin','{{adminUsername}}','{{testPassword}}'),(3,'staff','{{staffUsername}}','{{testPassword}}'),(4,'other','{{otherAdminUsername}}','{{testPassword}}'),(5,'password','pm.r1.password','{{testPassword}}')]:
 b.login(S,f'R1-SETUP-{n:03}',role,user,pwd)
b.add(S,'R1-SETUP-006','Read Hy-Line Brown breed','GET','/v1/flocks/breeds',200,'admin',check=shape('pm.expect(data.some(b=>b.breedName==="Hy-Line Brown")).to.eql(true);'),save={'breedId':'data.find(b=>b.breedName==="Hy-Line Brown").breedId'})
for n,name,role,var,extra in [(7,'A-H01-{{runId}}','admin','houseId',{}),(8,'B-H01-{{runId}}','other','houseBId',{}),(9,'A-H02-{{runId}}','admin','emptyHouseId',{}),(10,'A-INTAKE-{{runId}}','admin','intakeHouseId',{})]:
 b.add(S,f'R1-SETUP-{n:03}',name,'POST','/v1/houses',201,role,h(name),save={var:'data.houseId'})
b.add(S,'R1-SETUP-011','Create A-F01 (100 birds, D-7)','POST','/v1/flocks',201,'admin',f('A-F01-{{runId}}'),save={'flockId':'data.flockId'})
b.add(S,'R1-SETUP-012','Create B-F01 for farm isolation','POST','/v1/flocks',201,'other',f('B-F01-{{runId}}','{{houseBId}}'),save={'flockBId':'data.flockId'})
# PBI 01
for i,(role,user) in enumerate([('super','{{superUsername}}'),('admin','{{adminUsername}}'),('staff','{{staffUsername}}')],1):
 add('R1-01-01',i,'Login '+role,'POST','/v1/auth/login',200,None,dict(username=user,password='{{superPassword}}' if role=='super' else '{{testPassword}}'),shape(f'pm.expect(data.user.role).to.eql({json.dumps({"super":"Super Admin","admin":"Farm Admin","staff":"Farm Staff"}[role])}); pm.expect(data.accessToken).to.be.a("string").and.not.empty;'),{role+'Token':'data.accessToken'},manual='ตรวจ UI เพิ่ม: ฟอร์ม รหัสผ่านถูกปิดบัง และหน้า landing ตาม Role')
add('R1-01-02',1,'Wrong password rejected without issuing token','POST','/v1/auth/login',401,None,dict(username='{{adminUsername}}',password='Wrong@123'),shape('pm.expect(data||{}).not.to.have.property("accessToken");'),manual='ตรวจข้อความและการคงอยู่หน้า Login ด้วย Browser')
add('R1-01-03',1,'Create independent session for logout','POST','/v1/auth/login',200,None,dict(username='{{adminUsername}}',password='{{testPassword}}'),save={'logoutToken':'data.accessToken'})
add('R1-01-03',2,'Logout','POST','/v1/auth/logout',204,'logout')
add('R1-01-03',3,'Old token cannot reopen protected houses','GET','/v1/houses',401,'logout',manual='ตรวจ Browser Back + refresh ว่ากลับ Login ตาม Excel')
# PBI 02 use dedicated resettable password fixture
change=dict(currentPassword='{{testPassword}}',password='{{changedPassword}}',confirmPassword='{{changedPassword}}')
add('R1-02-01',1,'Change dedicated Farm Admin password','POST','/v1/auth/change-password',200,'password',change)
add('R1-02-01',2,'New password logs in','POST','/v1/auth/login',200,None,dict(username='pm.r1.password',password='{{changedPassword}}'),save={'passwordToken':'data.accessToken'})
add('R1-02-02',1,'Mismatched confirmation rejected','POST','/v1/auth/change-password',400,'password',dict(change,currentPassword='{{changedPassword}}',password='Another@123',confirmPassword='Different@123'))
add('R1-02-02',2,'Password unchanged after validation failure','POST','/v1/auth/login',200,None,dict(username='pm.r1.password',password='{{changedPassword}}'))
# Mail cases generated into their own collection below.
# PBI03 create two independent staff users, one for first login, one for suspension.
u=b.user(username='qa_staff_{{runId}}')
add('R1-03-01',1,'Create QA staff in farm A','POST','/v1/admin/users',201,'admin',u,shape('pm.expect(data.role).to.eql("Farm Staff"); pm.expect(data.farmId).to.eql(pm.variables.get("farmA")); pm.expect(data.mustChangePassword).to.eql(true);'),{'qaUserId':'data.userId'})
add('R1-03-01',2,'New user appears in own-farm list','GET','/v1/admin/users?search=qa_staff_{{runId}}',200,'admin',check=shape('pm.expect(data.content.length).to.eql(1); pm.expect(data.content[0].userId).to.eql(Number(pm.variables.get("qaUserId")));'))
add('R1-03-02',1,'Duplicate username rejected','POST','/v1/admin/users',409,'admin',u)
add('R1-03-02',2,'Exactly one original user remains','GET','/v1/admin/users?search=qa_staff_{{runId}}',200,'admin',check=shape('pm.expect(data.content.length).to.eql(1);'))
# first-login case depends on user creation, execute here while keeping Excel ID.
add('R1-02-05',1,'Login using temporary password','POST','/v1/auth/login',200,None,dict(username=u['username'],password='{{testPassword}}'),shape('pm.expect(data.user.mustChangePassword).to.eql(true);'),{'qaToken':'data.accessToken'})
add('R1-02-05',2,'Business API blocked before first password change','GET','/v1/flocks',403,'qa')
add('R1-02-05',3,'Set personal password','POST','/v1/auth/change-password',200,'qa',change)
add('R1-02-05',4,'Login with personal password','POST','/v1/auth/login',200,None,dict(username=u['username'],password='{{changedPassword}}'),shape('pm.expect(data.user.mustChangePassword).to.eql(false);'),{'qaToken':'data.accessToken'})
add('R1-02-05',5,'Access allowed after password change','GET','/v1/flocks',200,'qa',manual='ตรวจการพาไปหน้า first-login และหน้าตาม Role ใน Browser')
# folder ordering: move first-login steps into the user folder AFTER creation.
firstlogin=b.folders.pop('PBI-02 Change / Reset Password',[]) # holds password-change steps too
# Save and later sequence explicitly by dependency rather than sorting test IDs.
password_prefix=firstlogin[:4];firstlogin_steps=firstlogin[4:]
b.folders['PBI-02 Change / Reset Password']=password_prefix
b.folders['PBI-03 User Management'].extend(firstlogin_steps)
sv=b.user(username='qa_hold_{{runId}}')
add('R1-03-03',1,'Create separate suspension account','POST','/v1/admin/users',201,'admin',sv,save={'holdUserId':'data.userId'})
add('R1-03-03',2,'Suspend account','PUT','/v1/admin/users/{{holdUserId}}',200,'admin',dict(username=sv['username'],role='Farm Staff',active=False),shape('pm.expect(data.active).to.eql(false);'))
add('R1-03-03',3,'Suspended account cannot login','POST','/v1/auth/login',403,None,dict(username=sv['username'],password='{{testPassword}}'))
add('R1-03-03',4,'Reactivate account','PUT','/v1/admin/users/{{holdUserId}}',200,'admin',dict(username=sv['username'],role='Farm Staff',active=True))
add('R1-03-03',5,'Reactivated account can login','POST','/v1/auth/login',200,None,dict(username=sv['username'],password='{{testPassword}}'))
add('R1-03-04',1,'Staff cannot access user-management API','GET','/v1/admin/users',403,'staff',manual='API ตรวจสิทธิ์ได้ แต่ต้องตรวจด้วย Browser ว่าเมนูไม่แสดง')
# PBI04 uses 11 specially labelled farms from additional SQL, never production counts.
add('R1-04-01',1,'Read farm list','GET','/v1/admin/farms?size=100',200,'super',check=shape('pm.expect(data.content.some(f=>f.farmId===pm.variables.get("farmA"))).to.eql(true); pm.expect(data.content.some(f=>f.farmId===pm.variables.get("farmB"))).to.eql(true);'))
add('R1-04-01',2,'Search farm by ID','GET','/v1/admin/farms?search={{farmA}}',200,'super',check=shape('pm.expect(data.content.length).to.eql(1); pm.expect(data.content[0].farmId).to.eql(pm.variables.get("farmA"));'))
add('R1-04-01',3,'Read overview cards data','GET','/v1/admin/farms/summary',200,'super',check=shape('pm.expect(data.farms).to.be.at.least(2);'),manual='ตรวจการ์ดสรุป การค้นด้วยชื่อ และการล้างคำค้นใน Browser เพิ่ม')
for step,status in enumerate(['active','suspended'],1):add('R1-04-02',step,'Filter '+status,'GET','/v1/admin/farms?search=R1%20Postman&status='+status+'&size=100',200,'super',check=shape(f'pm.expect(data.content.length).to.be.above(0); data.content.forEach(f=>pm.expect(f.active).to.eql({str(status=="active").lower()}));'))
add('R1-04-02',3,'Unmatched search returns empty','GET','/v1/admin/farms?search=NO_MATCH_{{runId}}',200,'super',check=shape('pm.expect(data.content).to.eql([]); pm.expect(data.totalElements).to.eql(0);'))
add('R1-04-03',1,'First page contains 10 of 11 fixture farms','GET','/v1/admin/farms?search=R1%20Postman&page=0&size=10',200,'super',check=shape('pm.expect(data.content.length).to.eql(10); pm.expect(data.totalElements).to.eql(11);'),save={'firstFarmPage':'JSON.stringify(data.content.map(f=>f.farmId))'})
add('R1-04-03',2,'Second page has one distinct farm','GET','/v1/admin/farms?search=R1%20Postman&page=1&size=10',200,'super',check=shape('pm.expect(data.content.length).to.eql(1); const first=JSON.parse(pm.collectionVariables.get("firstFarmPage")); data.content.forEach(f=>pm.expect(first).not.to.include(f.farmId));'),manual='ตรวจปุ่มหน้าถัดไป/ย้อนกลับด้วย Browser')
# PBI05 success requires real inbox but duplicate/missing owner can run automatically.
farm=dict(b.farm,farmName='QA Farm {{runId}}')
add('R1-05-02',1,'Create without Farm Admin rejected','POST','/v1/farms',400,'super',farm)
add('R1-05-02',2,'Failed farm creation leaves no farm','GET','/v1/admin/farms?search=QA%20Farm%20{{runId}}',200,'super',check=shape('pm.expect(data.content.length).to.eql(0);'))
dup=deepcopy(farm);dup['accounts']=[dict(role='Farm Admin',username='{{adminUsername}}',email='duplicate.{{runId}}@example.invalid',temporaryPassword='{{testPassword}}')]
add('R1-05-03',1,'Duplicate owner username rolls transaction back','POST','/v1/farms',409,'super',dup)
add('R1-05-03',2,'No orphan farm after duplicate username','GET','/v1/admin/farms?search=QA%20Farm%20{{runId}}',200,'super',check=shape('pm.expect(data.content.length).to.eql(0);'))
# PBI06 must remain a failing signal if proxy strips underscore header.
for step,farmkey,housevar in [(1,'farmA','houseId'),(2,'farmB','houseBId'),(3,'farmA','houseId')]:
 add('R1-06-01',step,'Inspect '+farmkey+' houses','GET','/v1/houses',200,'super',headers={'X-Farm-Id':'{{'+farmkey+'}}'},check=shape(f'pm.expect(data).to.be.an("array").and.not.empty; data.forEach(h=>pm.expect(h.farmId).to.eql(pm.variables.get("{farmkey}"))); pm.expect(data.some(h=>h.houseId===Number(pm.variables.get("{housevar}")))).to.eql(true);'))
add('R1-06-02',1,'Repeated request with farm scope remains isolated','GET','/v1/houses',200,'super',headers={'X-Farm-Id':'{{farmA}}'},check=shape('data.forEach(h=>pm.expect(h.farmId).to.eql(pm.variables.get("farmA")));'),manual='Postman ส่ง Header ซ้ำได้ แต่ตรวจการจำบริบทหลัง refresh/เปลี่ยนเมนู/กลับหน้ารวมไม่ได้ ต้องทดสอบ Browser')
# PBI07
editfarm=dict(farm,farmName='Postman Farm A',address='QA updated address {{runId}}',ownerName='QA Owner {{runId}}')
add('R1-07-01',1,'Update own farm profile','PUT','/v1/farms/me',200,'admin',editfarm)
add('R1-07-01',2,'Read saved values and same farm ID','GET','/v1/farms/me',200,'admin',check=shape('pm.expect(data.farmId).to.eql(pm.variables.get("farmA")); pm.expect(data.address).to.eql("QA updated address "+pm.variables.get("runId")); pm.expect(data.ownerName).to.eql("QA Owner "+pm.variables.get("runId"));'))
add('R1-07-02',1,'Blank farm name rejected','PUT','/v1/farms/me',400,'admin',dict(editfarm,farmName=''))
add('R1-07-02',2,'Original farm name unchanged','GET','/v1/farms/me',200,'admin',check=shape('pm.expect(data.farmName).to.eql("Postman Farm A");'))
# PBI08
add('R1-08-01',1,'Create house with capacity 100','POST','/v1/houses',201,'admin',h('A-HNEW-{{runId}}'),save={'newHouseId':'data.houseId'})
add('R1-08-01',2,'Update capacity to 120','PUT','/v1/houses/{{newHouseId}}',200,'admin',h('A-HNEW-{{runId}}',120),shape('pm.expect(data.capacity).to.eql(120);'))
add('R1-08-01',3,'Saved capacity survives reread','GET','/v1/houses',200,'admin',check=shape('pm.expect(data.find(h=>h.houseId===Number(pm.variables.get("newHouseId"))).capacity).to.eql(120);'))
add('R1-08-02',1,'Capacity zero rejected','POST','/v1/houses',400,'admin',h('A-HZERO-{{runId}}',0))
add('R1-08-02',2,'Minimum capacity one accepted','POST','/v1/houses',201,'admin',h('A-HONE-{{runId}}',1),save={'oneHouseId':'data.houseId'})
add('R1-08-03',1,'Duplicate house name rejected','POST','/v1/houses',409,'admin',h('A-H01-{{runId}}'))
add('R1-08-03',2,'Original house remains single row','GET','/v1/houses',200,'admin',check=shape('pm.expect(data.filter(h=>h.houseName==="A-H01-"+pm.variables.get("runId")).length).to.eql(1);'))
add('R1-08-04',1,'House containing active flock cannot be deleted','DELETE','/v1/houses/{{houseId}}',409)
add('R1-08-04',2,'House and flock still visible','GET','/v1/flocks',200,'admin',check=shape('pm.expect(data.some(f=>f.flockId===Number(pm.variables.get("flockId")) && f.houseId===Number(pm.variables.get("houseId")))).to.eql(true);'))
# PBI09 validate over-capacity BEFORE success to keep the same empty-house prerequisite.
add('R1-09-02',1,'101 birds exceed capacity 100','POST','/v1/flocks',400,'admin',f('QA-FL-OVER-{{runId}}','{{intakeHouseId}}',101))
add('R1-09-01',1,'100 birds equal capacity 100','POST','/v1/flocks',201,'admin',f('QA-FL-{{runId}}','{{intakeHouseId}}'),shape('pm.expect(data.initialCount).to.eql(100); pm.expect(data.houseId).to.eql(Number(pm.variables.get("intakeHouseId")));'),{'intakeFlockId':'data.flockId'})
add('R1-09-03',1,'Second active flock in same house rejected','POST','/v1/flocks',409,'admin',f('QA-FL-DUP-{{runId}}'))
# PBI10
feed=dict(houseId='{{houseId}}',weightKg=12.5,lotNumber='FEED-{{runId}}')
add('R1-10-01',1,'Record 12.50 kg feed','POST','/feed-logs',201,'staff',feed,shape('pm.expect(Number(data.weightKg)).to.eql(12.5); pm.expect(data.recordDate).to.eql(pm.variables.get("today"));'))
add('R1-10-01',2,'Verify feed history values','GET','/houses/{{houseId}}/feed-logs',200,'staff',check=shape('const row=data.find(l=>l.lotNumber==="FEED-"+pm.variables.get("runId")); pm.expect(row).to.exist; pm.expect(Number(row.weightKg)).to.eql(12.5); pm.expect(row.recordDate).to.eql(pm.variables.get("today"));'))
add('R1-10-02',1,'Zero feed weight rejected','POST','/feed-logs',400,'staff',dict(feed,weightKg=0))
add('R1-10-02',2,'Blank feed lot rejected','POST','/feed-logs',400,'staff',dict(feed,lotNumber=''))
add('R1-10-02',3,'No new feed entries after rejected requests','GET','/houses/{{houseId}}/feed-logs',200,'staff',check=shape('pm.expect(data.filter(l=>l.lotNumber==="FEED-"+pm.variables.get("runId")).length).to.eql(1);'))
add('R1-10-03',1,'Empty house cannot receive feed','POST','/feed-logs',409,'staff',dict(feed,houseId='{{emptyHouseId}}',weightKg=10,lotNumber='EMPTY-{{runId}}'))
# PBI09 immutability now daily feed exists.
add('R1-09-04',1,'Received date cannot change after daily record','PUT','/v1/flocks/{{flockId}}',409,'admin',f('A-F01-{{runId}}',receivedDate='{{today}}'))
add('R1-09-04',2,'Original received date remains','GET','/v1/flocks',200,'admin',check=shape('pm.expect(data.find(f=>f.flockId===Number(pm.variables.get("flockId"))).receivedDate).to.eql(pm.variables.get("receivedDate"));'))
# move immutable flock requests to end of feed folder, preserving prerequisite order
imm=b.folders['PBI-09 Flock Data Management'][-2:];del b.folders['PBI-09 Flock Data Management'][-2:];b.folders['PBI-10 Daily Feed Recording'].extend(imm)
# PBI11 exact spreadsheet arithmetic.
add('R1-11-01',1,'Record 60 good eggs and 3 defective','POST','/v1/egg-production',201,'staff',e(),shape('pm.expect(data.summary.saleableEggCount).to.eql(60); pm.expect(data.summary.defectiveQty).to.eql(3); pm.expect(data.summary.totalEggCount).to.eql(63); pm.expect(Number(data.summary.totalTrayCount)).to.eql(2); pm.expect(Number(data.summary.defectivePercent)).to.eql(4.76);'),{'eggId':'data.eggLogId'})
add('R1-11-02',1,'Duplicate morning round rejected','POST','/v1/egg-production',409,'staff',e())
add('R1-11-02',2,'Afternoon round accepted','POST','/v1/egg-production',201,'staff',e(collectionRound='AFTERNOON'),save={'afternoonEggId':'data.eggLogId'})
add('R1-11-02',3,'Exactly one log per accepted round','GET','/v1/egg-production',200,'staff',check=shape('const logs=data.filter(l=>l.values.flockId===Number(pm.variables.get("flockId")) && l.values.recordDate===pm.variables.get("today")); pm.expect(logs.filter(l=>l.values.collectionRound==="MORNING").length).to.eql(1); pm.expect(logs.filter(l=>l.values.collectionRound==="AFTERNOON").length).to.eql(1);'))
for step,date,status in [(1,'{{beforeReceivedDate}}',400),(2,'{{tomorrow}}',400),(3,'{{receivedDate}}',201)]:
 add('R1-11-03',step,'Record date boundary '+date,'POST','/v1/egg-production',status,'staff',e(recordDate=date),save={'boundaryEggId':'data.eggLogId'} if status==201 else None)
add('R1-11-04',1,'Admin edits grade 1 from 30 to 60','PUT','/v1/egg-production/{{eggId}}',200,'admin',dict(e(),grade1Qty=60),shape('pm.expect(data.summary.saleableEggCount).to.eql(90); pm.expect(data.summary.totalEggCount).to.eql(93); pm.expect(Number(data.summary.totalTrayCount)).to.eql(3); pm.expect(Number(data.summary.defectivePercent)).to.eql(3.23);'))
add('R1-11-04',2,'Admin deletes selected log','DELETE','/v1/egg-production/{{eggId}}',204)
add('R1-11-04',3,'Deleted log excluded after reread','GET','/v1/egg-production',200,'admin',check=shape('pm.expect(data.some(l=>String(l.eggLogId)===String(pm.variables.get("eggId")))).to.eql(false);'))
# PBI12 exact 100 -> 97 -> 96.
health=dict(flockId='{{flockId}}',recordDate='{{today}}',liveHenCount=97,sickCount=1,deadCount=2,cullCount=1,deathCause='QA illness',cullCause='QA injury',symptomNote='QA symptoms',remark='QA-{{runId}}')
add('R1-12-01',1,'Record 2 deaths and 1 cull','POST','/health-logs',201,'staff',health,shape('pm.expect(data.deadCount).to.eql(2); pm.expect(data.cullCount).to.eql(1); pm.expect(data.currentCount).to.eql(97);'))
add('R1-12-01',2,'Flock current count is 97','GET','/v1/flocks',200,'staff',check=shape('pm.expect(data.find(f=>f.flockId===Number(pm.variables.get("flockId"))).currentCount).to.eql(97);'))
add('R1-12-02',1,'Add one death on same date','POST','/health-logs',201,'staff',dict(health,liveHenCount=96,sickCount=0,deadCount=1,cullCount=0),shape('pm.expect(data.deadCount).to.eql(3); pm.expect(data.cullCount).to.eql(1); pm.expect(data.currentCount).to.eql(96);'))
add('R1-12-02',2,'Loss history reflects accumulated events','GET','/health-logs/loss-history',200,'staff',check=shape('pm.expect(data).to.be.an("array").and.not.empty;'))
for step,values in [(1,dict(deadCount=1,cullCount=0,deathCause='')),(2,dict(deadCount=0,cullCount=1,cullCause=''))]:add('R1-12-03',step,'Loss cause required','POST','/health-logs',400,'staff',dict(health,liveHenCount=95,**values))
add('R1-12-03',3,'Rejected losses do not reduce flock','GET','/v1/flocks',200,'staff',check=shape('pm.expect(data.find(f=>f.flockId===Number(pm.variables.get("flockId"))).currentCount).to.eql(96);'))
add('R1-12-04',1,'Sick 101 exceeds live 100 on fresh intake flock','POST','/health-logs',400,'staff',dict(health,flockId='{{intakeFlockId}}',liveHenCount=100,sickCount=101,deadCount=0,cullCount=0))
add('R1-12-04',2,'Fresh flock remains at 100','GET','/v1/flocks',200,'staff',check=shape('pm.expect(data.find(f=>f.flockId===Number(pm.variables.get("intakeFlockId"))).currentCount).to.eql(100);'))
# Return password fixture to starting value for reruns, not other data.
b.add('99 Restore password fixture','R1-RESTORE-001','Restore dedicated password fixture','POST','/v1/auth/change-password',200,'password',dict(currentPassword='{{changedPassword}}',password='{{testPassword}}',confirmPassword='{{testPassword}}'))
main=b.folders; b.folders={}
mailpre='if (pm.variables.get("runEmailTests") !== "true") { console.warn("SKIPPED: opt in and follow inbox checkpoints"); pm.execution.skipRequest(); }'
for case,step,title,method,path,status,body,role,save in [
 ('R1-02-03',1,'Login isolated email QA account','POST','/v1/auth/login',200,dict(username='{{resetUsername}}',password='{{resetCurrentPassword}}'),None,{'resetOldToken':'data.accessToken'}),
 ('R1-02-03',2,'Request reset email by username','POST','/v1/auth/forgot-password',200,dict(username='{{resetUsername}}'),None,None),
 ('R1-02-03',3,'Reset via actual inbox token','POST','/v1/auth/reset-password',200,dict(token='{{resetToken}}',password='{{changedPassword}}',confirmPassword='{{changedPassword}}'),None,None),
 ('R1-02-03',4,'Login with reset password','POST','/v1/auth/login',200,dict(username='{{resetUsername}}',password='{{changedPassword}}'),None,None),
 ('R1-02-04',1,'Reject used reset link','POST','/v1/auth/reset-password',400,dict(token='{{resetToken}}',password='Another@123',confirmPassword='Another@123'),None,None),
 ('R1-02-04',2,'Latest password remains usable','POST','/v1/auth/login',200,dict(username='{{resetUsername}}',password='{{changedPassword}}'),None,None)]:
 add(case,step,title,method,path,status,role,body,save=save,pre=mailpre,manual='หยุดหลังขอ reset เพื่อเปิด inbox QA และกรอก resetToken ก่อน step 3; ตรวจลิงก์และข้อความใน Browser เพิ่ม')
mailfarm=deepcopy(farm);mailfarm['farmName']='QA Mail Farm {{runId}}';mailfarm['accounts']=[dict(role='Farm Admin',username='qa_owner_{{runId}}',email='{{addFarmEmail}}',temporaryPassword='{{testPassword}}')]
add('R1-05-01',1,'Create farm with new Farm Admin','POST','/v1/farms',201,'super',mailfarm,save={'createdMailFarmId':'data.farmId'},pre=mailpre)
add('R1-05-01',2,'Created farm searchable by generated ID','GET','/v1/admin/farms?search={{createdMailFarmId}}',200,'super',check=shape('pm.expect(data.content.length).to.eql(1); pm.expect(data.content[0].farmId).to.eql(pm.variables.get("createdMailFarmId"));'),pre=mailpre)
add('R1-05-01',3,'Owner belongs to new farm','GET','/v1/admin/users?search=qa_owner_{{runId}}',200,'super',check=shape('pm.expect(data.content.length).to.eql(1); pm.expect(data.content[0].farmId).to.eql(pm.variables.get("createdMailFarmId")); pm.expect(data.content[0].emailVerificationPending).to.eql(true);'),pre=mailpre,manual='ใช้ addFarmEmail QA จริง บัญชีใหม่ยังต้องยืนยันอีเมลและเปลี่ยนรหัสครั้งแรก')
mail=b.folders
# Email collection can bootstrap independently.
b.folders={}
b.login('00 Mail prerequisites','R1-MAIL-SETUP-001','super','{{superUsername}}','{{superPassword}}')
mail={**b.folders,**mail}
# Carry original workbook semantics into a separate traceability document.
b.values.update(resetUsername='',resetCurrentPassword='',resetToken='',addFarmEmail='',runEmailTests='false')
basepre=b.pre.replace('AUTH-001','R1-SETUP-001')+'''\nconst received = c.get("receivedDate");
c.set("beforeReceivedDate",new Date(Date.parse(received+"T00:00:00Z")-86400000).toISOString().slice(0,10));
c.set("tomorrow",new Date(Date.parse(c.get("today")+"T00:00:00Z")+86400000).toISOString().slice(0,10));
'''
for filename,name,fs in [('EggPrompt-Release1-Aligned.postman_collection.json','EggPrompt Release 1 - Excel Aligned (API portion)',main),('EggPrompt-Release1-Email-Manual.postman_collection.json','EggPrompt Release 1 - Excel Email Checkpoints',mail)]:
 col=b.collection(name,fs);col['event'][0]['script']['exec']=basepre.splitlines()
 col['info']['description']='Aligned to the supplied 41 Release 1 manual cases. UI criteria require browser testing. Source workbook unchanged. API results do not certify full manual cases. See alignment.html and README. Keep X-Farm-Id checks; do not change expected statuses to mask proxy issues.'
 # collection() sorts folders; dependency steps were explicitly positioned above.
 col['item']=[{'name':key,'item':val} for key,val in sorted(fs.items(), key=lambda pair: (pair[0].startswith('99 '), pair[0]))]
 (ROOT/filename).write_text(json.dumps(col,ensure_ascii=False,indent=2)+'\n')
maprows=[]
for r in cases:
 id=r['A']; ids=requestmap.get(id,[])
 if not ids:raise RuntimeError('Unmapped '+id)
 mailonly=id in ['R1-02-03','R1-02-04','R1-05-01']
 maprows.append({'Test ID':id,'PBI':r['B'],'Scenario':r['C'],'Original Expected Results':r['J'],'Postman requests':ids,'Execution':'Manual email checkpoints' if mailonly else 'API portion + Browser checks','Manual checks':notes.get(id,'ตรวจ UI/ข้อความ/การรีเฟรชตาม Expected Results ใน Excel เพิ่ม'),'Result':'NOT RUN'})
(ROOT/'alignment.json').write_text(json.dumps(maprows,ensure_ascii=False,indent=2)+'\n')
# Render the English request/response evidence layout after rebuilding collections.
import runpy
runpy.run_path(str(ROOT / 'render_evidence.py'), run_name='__main__')
print('Aligned workbook cases:',len(maprows),'Main requests:',sum(map(len,main.values())),'Manual-email requests:',sum(map(len,mail.values())))
