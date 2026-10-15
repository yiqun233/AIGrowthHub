#!/usr/bin/env python3
from pathlib import Path
import os,subprocess,time,urllib.request,sys,socket,json,uuid,shutil
import ai_stub
urllib.request.install_opener(urllib.request.build_opener(urllib.request.ProxyHandler({})))
ROOT=Path(__file__).resolve().parents[1]
env=os.environ.copy()
from local_env import load_env
env=load_env(ROOT/'.env',env)
env['NO_PROXY']=env['no_proxy']='localhost,127.0.0.1,::1'
env.update(DB_URL='jdbc:postgresql://127.0.0.1:5432/growthhub_test',SERVER_PORT='18080',SERVER_ADDRESS='127.0.0.1',TEST_API_URL='http://127.0.0.1:18080/api/v1')
assert env['DB_URL'].endswith('/growthhub_test')
# Reject concurrent use, then clear only isolated test data, preserving Flyway history and singleton settings.
with socket.socket() as probe:
 probe.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1)
 try:probe.bind(('127.0.0.1',18080))
 except OSError:raise SystemExit('测试端口已被占用，停止清理测试库；请先关闭已有测试进程。')
pg_env=env.copy();pg_env['PGPASSWORD']=env['DB_PASSWORD']
reset="""DO $$ DECLARE names text; BEGIN SELECT string_agg(quote_ident(tablename),',') INTO names FROM pg_tables WHERE schemaname='public' AND tablename NOT IN ('flyway_schema_history','app_setting','feed_source'); IF names IS NOT NULL THEN EXECUTE 'TRUNCATE TABLE ' || names || ' CASCADE'; END IF; IF to_regclass('public.app_setting') IS NOT NULL THEN UPDATE app_setting SET default_provider='OPENAI',default_model='',daily_minutes=60,preferences='',version=version+1; END IF; IF to_regclass('public.feed_source') IS NOT NULL THEN UPDATE feed_source SET last_attempt_at=NULL,last_success_at=NULL,error_code=NULL,item_count=0; END IF; END $$;"""
subprocess.run(['psql','-X','-h','127.0.0.1','-U',env['DB_USER'],'-d','growthhub_test','-v','ON_ERROR_STOP=1','-c',reset],env=pg_env,check=True,stdout=subprocess.DEVNULL)
env.update(OPENAI_API_KEY='test-only-openai',DEEPSEEK_API_KEY='test-only-deepseek',OPENAI_MODEL='test-openai',DEEPSEEK_MODEL='test-deepseek',OPENAI_BASE_URL='http://127.0.0.1:18889/openai/v1',DEEPSEEK_BASE_URL='http://127.0.0.1:18889/deepseek',AI_TIMEOUT_SECONDS='2',AI_DAILY_LIMIT='2000')
extra_providers=['ANTHROPIC','GEMINI','QWEN','ZHIPU','MOONSHOT','DOUBAO','OPENROUTER','CUSTOM']
for provider in extra_providers:
 env.update({provider+'_API_KEY':'test-only-'+provider.lower(),provider+'_BASE_URL':'http://127.0.0.1:18889/'+provider.lower(),provider+'_MODEL':'test-'+provider.lower()})
 # Do not inherit real account wire overrides into the local fixture.
for provider in ['OPENAI','DEEPSEEK']+extra_providers:
 env.pop(provider+'_OUTPUT_MODE',None);env.pop(provider+'_TOKEN_PARAMETER',None)
env.update(AUTH_ENABLED='false',FEED_AUTO_REFRESH='false',SESSION_COOKIE_SECURE='false',FEED_SPRING_BOOT_URL='http://127.0.0.1:18889/feeds/spring',FEED_PGVECTOR_URL='http://127.0.0.1:18889/feeds/vector')
stub=ai_stub.start()
(ROOT/'.runtime').mkdir(exist_ok=True)
with (ROOT/'.runtime/test-backend.log').open('w') as log:
 proc=subprocess.Popen([shutil.which('java') or '/usr/bin/java','-jar',str(ROOT/'backend/target/growthhub-0.1.0-SNAPSHOT.jar')],env=env,cwd=ROOT,stdout=log,stderr=log)
 try:
  for _ in range(90):
   if proc.poll() is not None:raise RuntimeError('测试后端启动失败，请查看 .runtime/test-backend.log')
   try:
    with urllib.request.urlopen('http://127.0.0.1:18080/actuator/health',timeout=2) as res:
     if res.status==200:break
   except Exception:time.sleep(1)
  else:raise RuntimeError('测试后端启动超时')
  subprocess.run(['python3','scripts/api_test.py'],cwd=ROOT,env=env,check=True)
  subprocess.run(['python3','scripts/ai_api_test.py'],cwd=ROOT,env=env,check=True)
  subprocess.run(['python3','scripts/provider_export_test.py'],cwd=ROOT,env=env,check=True)
  subprocess.run(['python3','scripts/weekly_api_test.py'],cwd=ROOT,env=env,check=True)
  subprocess.run(['python3','scripts/remaining_api_test.py'],cwd=ROOT,env=env,check=True)
  if '--api-only' not in sys.argv:
   subprocess.run([shutil.which('npm') or 'npm','run','test:e2e'],cwd=ROOT/'frontend',env=env,check=True)
  # Simulate a real crash only for our dedicated test process; the development server is unaffected.
  def api(path,method='GET',body=None,key=None):
   headers={'Content-Type':'application/json'}
   if key:headers['Idempotency-Key']=key
   req=urllib.request.Request('http://127.0.0.1:18080/api/v1'+path,method=method,headers=headers,data=None if body is None else json.dumps(body).encode())
   try:
    with urllib.request.urlopen(req,timeout=10) as res:return res.status,json.load(res)
   except urllib.error.HTTPError as e:
    try:return e.code,json.load(e)
    finally:e.close()
  _,source=api('/study-logs','POST',{'studyDate':'2040-01-01','content':'restart recovery fixture','durationMinutes':0})
  payload={'sourceLogId':source['id'],'targetDate':'2040-01-02','budgetMinutes':60,'provider':'OPENAI','model':'test-openai','feedback':'[test:timeout]'}
  code,job=api('/ai/analyses','POST',payload,str(uuid.uuid4()));assert code==202,(code,job)
  for _ in range(40):
   _,current=api('/ai/analyses/'+job['id'])
   if current['status']=='RUNNING':break
   time.sleep(.05)
  else:raise RuntimeError('No running test job available for crash recovery check')
  proc.kill();proc.wait(timeout=10)
  restart_env=env.copy();restart_env.update({p+'_API_KEY':'' for p in ['OPENAI','DEEPSEEK']+extra_providers})
  proc=subprocess.Popen([shutil.which('java') or '/usr/bin/java','-jar',str(ROOT/'backend/target/growthhub-0.1.0-SNAPSHOT.jar')],env=restart_env,cwd=ROOT,stdout=log,stderr=log)
  for _ in range(90):
   if proc.poll() is not None:raise RuntimeError('Recovery test backend failed to start')
   try:
    code,recovered=api('/ai/analyses/'+job['id'])
    if code==200 and recovered['status']=='FAILED':break
   except Exception:pass
   time.sleep(.2)
  else:raise RuntimeError('Running analysis was not recovered after restart')
  assert recovered['errorCode']=='PROCESS_RESTARTED',recovered
  assert api('/study-logs/'+source['id'])[1]['content']==source['content']
  code,error=api('/ai/analyses','POST',payload,str(uuid.uuid4()));assert code==422 and error['code']=='PROVIDER_NOT_CONFIGURED',(code,error)
  print('Crash recovery and missing-credential integration checks passed.',flush=True)
 finally:
  proc.terminate()
  try:proc.wait(timeout=15)
  except subprocess.TimeoutExpired:proc.kill();proc.wait()
  stub.shutdown();stub.server_close()
