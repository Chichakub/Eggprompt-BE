"""Check HTTP method/path coverage against Controller mappings, ignoring aliases."""
import json,re
from pathlib import Path
root=Path(__file__).resolve().parent
requests=[]
def walk(items):
 for item in items:
  if 'item' in item: walk(item['item'])
  else:
   r=item['request'];requests.append((item['name'].split(' | ')[0],r['method'],r['url'].removeprefix('{{baseUrl}}').split('?')[0]))
for p in root.glob('*.postman_collection.json'): walk(json.loads(p.read_text())['item'])
def match(controller,request):
 # Named Java path parameters and Postman placeholders both represent one segment.
 c=re.sub(r'\{[^{}]+\}', '__PARAM__',controller)
 r=re.sub(r'\{\{[^{}]+\}\}', '__PARAM__',request)
 pattern='^'+re.escape(c).replace('__PARAM__','[^/]+')+'$'
 return re.fullmatch(pattern,r) is not None
rows=[];missing=[]
for file in sorted((root.parents[1]/'src/main/java/com/eggprompt/backend').glob('*/*Controller.java')):
 text=file.read_text();before,after=text.split('public class',1)
 base=re.search(r'@RequestMapping\((.*?)\)',before,re.S)
 bases=re.findall(r'"([^"]*)"',base.group(1)) if base else ['']
 for m in re.finditer(r'@(Get|Post|Put|Delete)Mapping(?:\((.*?)\))?',after,re.S):
  paths=re.findall(r'"([^"]*)"',m.group(2) or '') or ['']
  routes=[b+p for b in bases for p in paths];method=m.group(1).upper()
  ids=[id for id,met,path in requests if met==method and any(match(route,path) for route in routes)]
  dev='@Profile("dev")' in before
  status='DEV ONLY — excluded' if dev else ('Covered' if ids else 'MISSING')
  if not ids and not dev:missing.append((file.name,method,routes))
  rows.append(f'| {file.name} | {method} | '+ '<br>'.join('`'+r+'`' for r in routes)+' | '+status+' | '+', '.join(ids[:8])+(' …' if len(ids)>8 else '')+' |')
(root/'endpoint-coverage.md').write_text('# Controller endpoint coverage\n\nStatic method/path coverage only; this does not prove live execution or exhaustive branch coverage. Aliases are grouped.\n\n| Controller | Method | Routes / aliases | Coverage | Example Test IDs |\n|---|---|---|---|---|\n'+'\n'.join(rows)+'\n')
print(f'{len(rows)} handler mappings; {len(missing)} production handlers without a request; dev-only endpoints excluded.')
if missing:raise SystemExit(str(missing))
