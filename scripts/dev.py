#!/usr/bin/env python3
"""Manage only GrowthHub-owned local processes; no system-wide process killing."""
from pathlib import Path
import json,os,signal,subprocess,sys,time,urllib.request
urllib.request.install_opener(urllib.request.build_opener(urllib.request.ProxyHandler({})))
ROOT=Path(__file__).resolve().parents[1]
RUNTIME=ROOT/'.runtime'; RUNTIME.mkdir(exist_ok=True)
def config():
 env=os.environ.copy()
 for line in (ROOT/'.env').read_text().splitlines():
  if line and not line.startswith('#'):
   key,value=line.split('=',1);env[key]=value
 env['JAVA_HOME']='/usr/lib/jvm/java-21-openjdk-amd64'
 env['PATH']=env['JAVA_HOME']+'/bin:'+str(Path.home()/'.volta/bin')+':/usr/bin:'+env['PATH']
 return env
def owned(pid):
 try:return Path(f'/proc/{pid}/cwd').resolve()==ROOT or ROOT in Path(f'/proc/{pid}/cwd').resolve().parents
 except (FileNotFoundError,PermissionError):return False
def active(name):
 file=RUNTIME/(name+'.pid')
 if not file.exists():return None
 pid=int(file.read_text());return pid if owned(pid) else None
def wait(url):
 for _ in range(90):
  try:
   with urllib.request.urlopen(url,timeout=2) as res:
    if res.status==200:return
  except Exception:pass
  time.sleep(1)
 raise RuntimeError('服务未就绪，请查看 .runtime 日志：'+url)
action=sys.argv[1] if len(sys.argv)>1 else 'status'
if action=='start':
 env=config()
 jar=ROOT/'backend/target/growthhub-0.1.0-SNAPSHOT.jar'
 if not jar.exists():raise SystemExit('请先执行 cd backend && ./mvnw package')
 specs={'backend':(['/usr/bin/java','-jar',str(jar)],ROOT), 'frontend':([str(Path.home()/'.volta/bin/npm'),'run','dev'],ROOT/'frontend')}
 for name,(command,cwd) in specs.items():
  if active(name):print(name+' 已运行');continue
  with (RUNTIME/(name+'.log')).open('ab') as log:
   proc=subprocess.Popen(command,cwd=cwd,env=env,stdout=log,stderr=log,start_new_session=True)
  (RUNTIME/(name+'.pid')).write_text(str(proc.pid))
 wait('http://127.0.0.1:8080/actuator/health');wait('http://127.0.0.1:5173')
 print('GrowthHub 已启动：http://127.0.0.1:5173')
elif action=='stop':
 for name in ['frontend','backend']:
  pid=active(name)
  if pid:os.killpg(pid,signal.SIGTERM);print(name+' 已停止')
  (RUNTIME/(name+'.pid')).unlink(missing_ok=True)
elif action=='status':
 for name in ['backend','frontend']:print(name+': '+('运行中' if active(name) else '未运行'))
else:raise SystemExit('用法：python3 scripts/dev.py start|stop|status')
