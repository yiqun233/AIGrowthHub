"""Real servlet/Spring Security tests in a dedicated backend process and test DB."""
from pathlib import Path
import os,subprocess,time,urllib.request,urllib.error,urllib.parse,http.cookiejar,json,socket,shutil
ROOT=Path(__file__).resolve().parents[1];env=os.environ.copy()
from local_env import load_env
env=load_env(ROOT/'.env',env)
env['NO_PROXY']=env['no_proxy']='localhost,127.0.0.1,::1'
env.update(DB_URL='jdbc:postgresql://127.0.0.1:5432/growthhub_test',SERVER_PORT='18081',SERVER_ADDRESS='127.0.0.1',AUTH_ENABLED='true',AUTH_USERNAME='security-test',AUTH_PASSWORD='integration-only-long-password!',AUTH_PASSWORD_HASH='',SESSION_COOKIE_SECURE='false',SESSION_TIMEOUT='1m',FEED_AUTO_REFRESH='false')
for p in ['OPENAI','DEEPSEEK','ANTHROPIC','GEMINI','QWEN','ZHIPU','MOONSHOT','DOUBAO','OPENROUTER','CUSTOM']:env[p+'_API_KEY']=''
with socket.socket() as probe:probe.bind(('127.0.0.1',18081))
cookie=http.cookiejar.CookieJar();client=urllib.request.build_opener(urllib.request.ProxyHandler({}),urllib.request.HTTPCookieProcessor(cookie))
base='http://127.0.0.1:18081'
def request(path,method='GET',body=None,token=None,form=False,opener=client):
 headers={}
 if token:headers['X-CSRF-TOKEN']=token
 if body is not None:headers['Content-Type']='application/x-www-form-urlencoded' if form else 'application/json'
 data=None if body is None else urllib.parse.urlencode(body).encode() if form else json.dumps(body).encode()
 try:
  with opener.open(urllib.request.Request(base+path,method=method,headers=headers,data=data),timeout=10) as res:return res.status,json.load(res),dict(res.headers)
 except urllib.error.HTTPError as e:
  try:return e.code,json.load(e),dict(e.headers)
  finally:e.close()
def ready(proc):
 for _ in range(100):
  if proc.poll() is not None:raise AssertionError('Security backend stopped; inspect .runtime/security-test.log')
  try:
   if request('/actuator/health')[0]==200:return
  except Exception:pass
  time.sleep(.2)
 raise AssertionError('Security backend did not become ready')
def stop(proc):
 proc.terminate()
 try:proc.wait(timeout=15)
 except subprocess.TimeoutExpired:proc.kill();proc.wait()
with (ROOT/'.runtime/security-test.log').open('w') as output:
 proc=subprocess.Popen([shutil.which('java'),'-jar',str(ROOT/'backend/target/growthhub-0.1.0-SNAPSHOT.jar')],env=env,cwd=ROOT,stdout=output,stderr=output)
 try:
  ready(proc)
  for path in ['/api/v1/goals','/api/v1/exports','/api/v1/weekly-plans','/api/v1/feed/items','/api/v1/achievements']:assert request(path)[0]==401,path
  assert request('/api/v1/goals','POST',{'title':'blocked'})[0]==403
  cookie.clear();_,session,headers=request('/api/v1/auth/session');assert session['enabled'] and not session['authenticated'];assert 'HttpOnly' in headers.get('Set-Cookie','') and 'SameSite=Strict' in headers.get('Set-Cookie','')
  token=session['csrfToken'];before={c.name:c.value for c in cookie}
  assert request('/api/v1/auth/login','POST',{'username':'security-test','password':'wrong'},token,True)[0]==401
  assert request('/api/v1/auth/login','POST',{'username':'security-test','password':env['AUTH_PASSWORD']},token,True)[0]==200
  after={c.name:c.value for c in cookie};assert before!=after,'Session fixation protection missing'
  assert request('/api/v1/goals')[0]==200
  assert request('/api/v1/goals','POST',{'title':'blocked'})[0]==403
  _,state,_=request('/api/v1/auth/session');assert state['authenticated'];token=state['csrfToken']
  assert request('/api/v1/tasks','POST',{'title':'authenticated mutation fixture','scheduledDate':'2090-01-01','estimateMinutes':5},token)[0]==201
  assert request('/api/v1/auth/logout','POST',{},token)[0]==200;assert request('/api/v1/exports')[0]==401
  _,state,_=request('/api/v1/auth/session');assert request('/api/v1/auth/login','POST',{'username':'security-test','password':env['AUTH_PASSWORD']},state['csrfToken'],True)[0]==200
  subprocess.run([shutil.which('npm'),'run','test:e2e','--','--config','playwright.auth.config.ts'],cwd=ROOT/'frontend',env=env,check=True)
  print('Login, CSRF, session rotation and logout passed; checking the real one-minute servlet expiry.',flush=True)
  for _ in range(31):time.sleep(2)
  assert request('/api/v1/goals')[0]==401,'Session should expire'
  print('Authentication, unauthorized reads/writes, CSRF, session rotation, logout and expiry passed.',flush=True)
 finally:stop(proc)
 env['SESSION_COOKIE_SECURE']='true';cookie.clear();proc=subprocess.Popen([shutil.which('java'),'-jar',str(ROOT/'backend/target/growthhub-0.1.0-SNAPSHOT.jar')],env=env,cwd=ROOT,stdout=output,stderr=output)
 try:
  ready(proc);_,_,headers=request('/api/v1/auth/session');assert 'Secure' in headers.get('Set-Cookie','');print('Secure production cookie flag passed.',flush=True)
  if os.environ.get('RUN_HTTPS_TEST')=='true':subprocess.run(['python3','scripts/https_test.py'],env=env,cwd=ROOT,check=True)
 finally:stop(proc)
