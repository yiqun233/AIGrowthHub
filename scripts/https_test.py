"""Validate the shipped Caddy routes over real local TLS. Backend is owned by security_test.py."""
from pathlib import Path
import os,subprocess,urllib.request,urllib.error,urllib.parse,json,time,ssl,http.cookiejar,re,shutil,socket
ROOT=Path(__file__).resolve().parents[1];caddy=Path(os.environ.get('CADDY_BIN',str(Path.home()/'.local/share/growthhub/tools/caddy/2.11.7/caddy')))
if not caddy.is_file():raise SystemExit('CADDY_BIN is required for the native HTTPS smoke check')
with socket.socket() as probe:probe.bind(('127.0.0.1',18443))
runtime=ROOT/'.runtime/caddy-smoke';runtime.mkdir(parents=True,exist_ok=True);env=os.environ.copy();env.update(XDG_DATA_HOME=str(runtime/'data'),XDG_CONFIG_HOME=str(runtime/'config'),APP_DOMAIN='growthhub.example.com',ACME_EMAIL='operator@example.com')
common=(ROOT/'infra/common.caddy').read_text().replace('backend:8080','127.0.0.1:18081').replace('root * /srv','root * '+str(ROOT/'frontend/dist'))
(runtime/'common.caddy').write_text(common)
production=(ROOT/'infra/Caddyfile').read_text().replace('/etc/caddy/common.caddy',str(runtime/'common.caddy'))
(runtime/'production.caddy').write_text(production)
local=(ROOT/'infra/Caddyfile.local').read_text().replace(' admin off',' admin off\n auto_https disable_redirects').replace('/etc/caddy/common.caddy',str(runtime/'common.caddy')).replace('https://localhost {','https://localhost:18443 {\n bind 127.0.0.1')
(runtime/'Caddyfile').write_text(local)
for file in ['production.caddy','Caddyfile']:subprocess.run([str(caddy),'validate','--config',str(runtime/file),'--adapter','caddyfile'],env=env,check=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
with (ROOT/'.runtime/https-test.log').open('w') as log:
 proc=subprocess.Popen([str(caddy),'run','--config',str(runtime/'Caddyfile'),'--adapter','caddyfile'],env=env,cwd=ROOT,stdout=log,stderr=log)
 try:
  cert=runtime/'data/caddy/pki/authorities/local/root.crt'
  for _ in range(100):
   if proc.poll() is not None:raise AssertionError('Caddy stopped; inspect .runtime/https-test.log')
   if cert.is_file():break
   time.sleep(.1)
  else:raise AssertionError('Local CA not generated')
  context=ssl.create_default_context(cafile=str(cert));jar=http.cookiejar.CookieJar();client=urllib.request.build_opener(urllib.request.ProxyHandler({}),urllib.request.HTTPSHandler(context=context),urllib.request.HTTPCookieProcessor(jar))
  base='https://localhost:18443'
  def request(path,method='GET',body=None,token=None):
   headers={}
   if body is not None:headers['Content-Type']='application/x-www-form-urlencoded'
   if token:headers['X-CSRF-TOKEN']=token
   req=urllib.request.Request(base+path,method=method,headers=headers,data=None if body is None else urllib.parse.urlencode(body).encode())
   try:
    with client.open(req,timeout=10) as res:return res.status,res.read().decode(),dict(res.headers)
   except urllib.error.HTTPError as e:
    try:return e.code,e.read().decode(),dict(e.headers)
    finally:e.close()
  for _ in range(50):
   try:
    if request('/actuator/health')[0]==200:break
   except Exception:pass
   time.sleep(.1)
  assert request('/api/v1/exports')[0]==401
  _,raw,headers=request('/api/v1/auth/session');token=json.loads(raw)['csrfToken'];assert 'Secure' in headers.get('Set-Cookie','') and 'HttpOnly' in headers.get('Set-Cookie','')
  code,_,_=request('/api/v1/auth/login','POST',{'username':'security-test','password':env['AUTH_PASSWORD']},token);assert code==200
  assert request('/api/v1/goals')[0]==200
  code,html,headers=request('/weekly');assert code==200 and 'id="root"' in html;assert "script-src 'self'" in headers['Content-Security-Policy']
  asset=re.search(r'src="(/assets/[^\"]+)"',html).group(1);_,_,asset_headers=request(asset);assert 'immutable' in asset_headers.get('Cache-Control','')
  browser_env=env.copy();browser_env['HTTPS_BASE_URL']=base
  subprocess.run([shutil.which('npm'),'run','test:e2e','--','--config','playwright.auth.config.ts'],cwd=ROOT/'frontend',env=browser_env,check=True)
  print('Native HTTPS: certificate verification, secure session, same-origin API, SPA fallback, CSP and asset caching passed.',flush=True)
 finally:
  proc.terminate()
  try:proc.wait(timeout=10)
  except subprocess.TimeoutExpired:proc.kill();proc.wait()
