#!/usr/bin/env python3
from pathlib import Path
import os,subprocess,time,urllib.request,sys
urllib.request.install_opener(urllib.request.build_opener(urllib.request.ProxyHandler({})))
ROOT=Path(__file__).resolve().parents[1]
env=os.environ.copy()
for line in (ROOT/'.env').read_text().splitlines():
 if line and not line.startswith('#'):
  key,value=line.split('=',1);env[key]=value
env['NO_PROXY']=env['no_proxy']='localhost,127.0.0.1,::1'
env.update(DB_URL='jdbc:postgresql://127.0.0.1:5432/growthhub_test',SERVER_PORT='18080',SERVER_ADDRESS='127.0.0.1',TEST_API_URL='http://127.0.0.1:18080/api/v1')
assert env['DB_URL'].endswith('/growthhub_test')
(ROOT/'.runtime').mkdir(exist_ok=True)
with (ROOT/'.runtime/test-backend.log').open('w') as log:
 proc=subprocess.Popen(['/usr/bin/java','-jar',str(ROOT/'backend/target/growthhub-0.1.0-SNAPSHOT.jar')],env=env,cwd=ROOT,stdout=log,stderr=log)
 try:
  for _ in range(90):
   if proc.poll() is not None:raise RuntimeError('测试后端启动失败，请查看 .runtime/test-backend.log')
   try:
    with urllib.request.urlopen('http://127.0.0.1:18080/actuator/health',timeout=2) as res:
     if res.status==200:break
   except Exception:time.sleep(1)
  else:raise RuntimeError('测试后端启动超时')
  subprocess.run(['python3','scripts/api_test.py'],cwd=ROOT,env=env,check=True)
  if '--api-only' not in sys.argv:
   subprocess.run([str(Path.home()/'.volta/bin/npm'),'run','test:e2e'],cwd=ROOT/'frontend',env=env,check=True)
 finally:
  proc.terminate()
  try:proc.wait(timeout=15)
  except subprocess.TimeoutExpired:proc.kill();proc.wait()
