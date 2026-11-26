"""Render a plain evidence table. Does not execute requests or infer results."""
import html
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent
sources = [
    ('Main collection', 'EggPrompt-Release1-Recovered-Run-All.postman_collection.json'),
    ('Email collection', 'EggPrompt-Release1-Email-Manual.postman_collection.json'),
]

def walk(items):
    for item in items:
        if 'item' in item:
            yield from walk(item['item'])
        else:
            yield item

sections = []
counts = []
for title, filename in sources:
    collection = json.loads((ROOT / filename).read_text())
    rows = []
    for item in walk(collection['item']):
        request_id, _, name = item['name'].partition(' | ')
        match = re.match(r'R1-(\d{2})-', request_id)
        label = f'PBI-{match.group(1)} : ' if match else ''
        request = item['request']
        url = request['url']
        endpoint = (url if isinstance(url, str) else url['raw']).removeprefix('{{baseUrl}}')
        method = request['method']
        rows.append(f'''<tr data-id="{html.escape(request_id)}">
<td>{html.escape(label + request_id)}<small>{html.escape(name)}</small></td>
<td class="method {method.lower()}">{method}</td>
<td class="endpoint">{html.escape(endpoint)}</td>
<td class="evidence" contenteditable="true" aria-label="Request evidence for {request_id}"></td>
<td class="evidence" contenteditable="true" aria-label="Response evidence for {request_id}"></td>
<td class="status" contenteditable="true" aria-label="HTTP status for {request_id}"></td>
<td class="result" contenteditable="true" aria-label="Result for {request_id}"></td>
</tr>''')
    sections.append(f'''<section><h2>{html.escape(title)}</h2><p class="source">{html.escape(filename)}</p>
<div class="table-wrap"><table>
<colgroup><col style="width:12%"><col style="width:5%"><col style="width:17%"><col style="width:25%"><col style="width:26%"><col style="width:8%"><col style="width:7%"></colgroup>
<thead><tr><th>Test ID</th><th>Method</th><th>Endpoint</th><th>Request (JSON)</th><th>Response (JSON)</th><th>HTTP Status</th><th>Result</th></tr></thead>
<tbody>{''.join(rows)}</tbody></table></div></section>''')
    counts.append(len(rows))

page = '''<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>EggPrompt Release 1 — Test Evidence</title>
<style>
*{box-sizing:border-box}
body{margin:0;padding:28px 14px;background:#fff;color:#111;font:15px/1.5 Arial,sans-serif}
h2{font-size:21px;margin:0 0 4px}.source{font-size:12px;color:#666;margin:0 0 18px}section+section{margin-top:40px}td small{display:block;font-size:11px;line-height:1.4;color:#666;font-weight:400;margin-top:9px}
.table-wrap{width:100%;overflow-x:auto}
table{width:100%;min-width:1180px;border-collapse:separate;border-spacing:0;table-layout:fixed}
thead{color:white;background:linear-gradient(100deg,#697bf0,#804b99)}
th{text-align:left;padding:16px 14px;font-size:16px;font-weight:700;border-right:1px solid #ffffff26}
th:first-child{border-radius:30px 0 0 30px}
th:last-child{border-radius:0 30px 30px 0;border-right:0}
td{height:300px;padding:22px 14px;vertical-align:top;border-bottom:1px solid #e5e7ef;overflow-wrap:anywhere;font-weight:600}
.method{color:#009800;font-size:18px}
.method.get{color:#2368ad}.method.put{color:#a37010}.method.delete{color:#ba3441}
.endpoint{font-size:15px}
.status,.result{color:#009800;text-align:center;font-size:17px}
.evidence{font-weight:400;white-space:pre-wrap}
.evidence img{display:block;max-width:100%;height:auto;margin:0 auto 10px}
[contenteditable]:focus{outline:2px solid #aaa0de;outline-offset:-3px}
@media print{@page{size:A3 landscape;margin:10mm}body{padding:0}table{min-width:0}thead{display:table-header-group;print-color-adjust:exact;-webkit-print-color-adjust:exact}tr{break-inside:avoid}td{height:220px;padding:14px 10px}th{font-size:13px}}
</style>
</head>
<body>
__SECTIONS__
<script>
// Allow direct screenshot pasting without adding controls to the table.
for(const cell of document.querySelectorAll('.evidence')){
 cell.addEventListener('paste',event=>{
  const file=[...event.clipboardData.items].find(item=>item.type.startsWith('image/'))?.getAsFile();
  if(!file)return;
  event.preventDefault();
  const reader=new FileReader();
  reader.onload=()=>{const img=document.createElement('img');img.src=reader.result;img.alt='';cell.append(img);};
  reader.readAsDataURL(file);
 });
}
</script>
</body></html>
'''
(ROOT / 'alignment.html').write_text(page.replace('__SECTIONS__', '\n'.join(sections)))
print(f'Evidence tables: main={counts[0]}, email={counts[1]}, total={sum(counts)}')
