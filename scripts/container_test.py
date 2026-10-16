"""Smoke-test only the explicitly isolated growthhub-release-check Compose stack."""
from pathlib import Path
import os,subprocess,shutil,json,ssl,http.cookiejar,urllib.request,urllib.error,urllib.parse,time,hashlib,datetime
ROOT=Path(__file__).resolve().parents[1];project='growthhub-release-check';docker=os.environ.get('DOCKER_BIN',shutil.which('docker') or 'docker')
def dockerpath(path):return subprocess.check_output(['wslpath','-w',str(path)],text=True).strip() if docker.endswith('.exe') else str(path)
envfile=ROOT/'.runtime/container-test.env';settings={}
for line in envfile.read_text().splitlines():
 if line and not line.startswith('#'):
  k,v=line.split('=',1);settings[k]=v
compose=[docker,'compose','--project-name',project,'--env-file',dockerpath(envfile),'-f',dockerpath(ROOT/'infra/compose.prod.yml'),'-f',dockerpath(ROOT/'infra/compose.local.yml')]
def cmd(*args):return subprocess.check_output(compose+list(args),cwd=ROOT)
rows=[json.loads(line) for line in cmd('ps','--format','json').decode().splitlines() if line.strip()]
assert {row['Service'] for row in rows}=={'db','backend','web','backup'}
assert all(row['Project']==project and row['State']=='running' for row in rows)
for row in rows:
 for p in row.get('Publishers') or []:
  if p['PublishedPort']:assert row['Service']=='web' and p['URL']=='127.0.0.1'
backend=next(row['ID'] for row in rows if row['Service']=='backend')
identity=subprocess.check_output([docker,'inspect','--format','{{.Config.User}} {{.HostConfig.ReadonlyRootfs}}',backend],text=True).strip();assert identity=='10001:10001 true',identity
cmd('cp','web:/data/caddy/pki/authorities/local/root.crt',dockerpath(ROOT/'.runtime/container-ca.crt'))
context=ssl.create_default_context(cafile=str(ROOT/'.runtime/container-ca.crt'));jar=http.cookiejar.CookieJar();client=urllib.request.build_opener(urllib.request.ProxyHandler({}),urllib.request.HTTPSHandler(context=context),urllib.request.HTTPCookieProcessor(jar))
base='https://localhost:18443'
def request(path,method='GET',body=None,token=None):
 headers={}
 if body is not None:headers['Content-Type']='application/x-www-form-urlencoded'
 if token:headers['X-CSRF-TOKEN']=token
 req=urllib.request.Request(base+path,method=method,data=None if body is None else urllib.parse.urlencode(body).encode(),headers=headers)
 try:
  with client.open(req,timeout=15) as res:return res.status,res.read(),dict(res.headers)
 except urllib.error.HTTPError as e:
  try:return e.code,e.read(),dict(e.headers)
  finally:e.close()
assert request('/api/v1/exports')[0]==401
_,raw,headers=request('/api/v1/auth/session');state=json.loads(raw);assert state['enabled'];assert 'Secure' in headers.get('Set-Cookie','')
assert request('/api/v1/auth/login','POST',{'username':settings['AUTH_USERNAME'],'password':settings['AUTH_PASSWORD']},state['csrfToken'])[0]==200
for path in ['/api/v1/goals','/api/v1/weeks','/api/v1/feed/items','/api/v1/achievements']:
 assert request(path)[0]==200,path
code,raw,_=request('/api/v1/exports');assert code==200 and json.loads(raw)['schemaVersion']=='growthhub-export-v4'
assert request('/api/v1/goals','POST',{'title':'must fail without csrf'})[0]==403
browser_env=os.environ.copy();browser_env.update(settings);browser_env.update(HTTPS_BASE_URL=base,NO_PROXY='localhost,127.0.0.1,::1',no_proxy='localhost,127.0.0.1,::1')
subprocess.run([shutil.which('npm'),'run','test:e2e','--','--config','playwright.auth.config.ts'],cwd=ROOT/'frontend',env=browser_env,check=True)
def sql(db,query):return cmd('exec','-T','db','psql','-X','-At','-U','postgres','-d',db,'-v','ON_ERROR_STOP=1','-c',query)
assert sql('growthhub',"select rolsuper::text||','||rolcreatedb::text||','||rolcreaterole::text from pg_roles where rolname='growthhub'").decode().strip()=='false,false,false'
# Wait for the first validated backup; the service waits for migrations to complete.
daily='/backups/daily/growthhub-'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y-%m-%d')+'.dump'
for _ in range(50):
 checked=subprocess.run(compose+['exec','-T','backup','pg_restore','--list',daily],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
 if checked.returncode==0:break
 time.sleep(.2)
else:raise AssertionError('First automatic backup was not verified')
assert cmd('exec','-T','backup','find','/backups/weekly','-type','f','-name','*.dump').strip()
target='growthhub_restore_check_'+str(int(time.time()));assert target.startswith('growthhub_restore_check_')
cmd('exec','-T','db','pg_dump','-U','growthhub','-d','growthhub','--format=custom','--no-owner','-f','/tmp/growthhub-release-check.dump')
sql('postgres','CREATE DATABASE '+target+' OWNER growthhub')
cmd('exec','-T','db','pg_restore','-U','growthhub','-d',target,'--exit-on-error','--no-owner','--no-privileges','/tmp/growthhub-release-check.dump')
tables=sql('growthhub',"select tablename from pg_tables where schemaname='public' order by tablename").decode().splitlines();report={}
for table in tables:
 assert table.replace('_','').isalnum();query='select to_jsonb(t)::text from '+table+' t order by to_jsonb(t)::text'
 before=sql('growthhub',query);after=sql(target,query);assert before==after,table;report[table]={'count':len(before.splitlines()),'sha256':hashlib.sha256(before).hexdigest()}
images={service:subprocess.check_output([docker,'image','inspect','--format','{{.Id}}',project+'-'+service],text=True).strip() for service in ['backend','web','db','backup']}
(ROOT/'.runtime/container-verification.json').write_text(json.dumps({'project':project,'restoreDatabase':target,'images':images,'tables':report,'backendIdentity':identity},indent=2))
print('Container TLS, browser login/write/logout, CSRF, restricted DB role, nonroot/read-only backend, automatic backup and full restore passed.',flush=True)
